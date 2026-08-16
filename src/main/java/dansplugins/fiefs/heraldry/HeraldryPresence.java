package dansplugins.fiefs.heraldry;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Decides whether this server has PatriamHeraldry, and it is the only class allowed to ask.
 *
 * <p>Fiefs is a tier 1 plugin: it runs on servers that have no heraldry at all, and it must lose
 * nothing there but the arms. That is harder than it sounds, because {@link FiefSubjectResolver}
 * IMPLEMENTS a PatriamHeraldry type, and a class cannot be LOADED when a type in its
 * {@code implements} clause is missing. The JVM throws {@link NoClassDefFoundError} while linking it,
 * before a line of our code inside it runs, so an {@code if} around the registration CALL does not
 * help if the resolver is reachable from anything that is already loaded by then.
 *
 * <p>So three things are true here and each of them is load-bearing:
 *
 * <ul>
 *   <li>No field, parameter or return type in this class, or anywhere else outside the
 *       {@code heraldry} package, names a PatriamHeraldry type. Verifying a method needs the types in
 *       its descriptor, so one such signature on a class that always loads would fail at link time
 *       where nothing can catch it.
 *   <li>The presence question is asked with {@link Class#forName(String)} on a string constant rather
 *       than with a {@code .class} literal. A literal would put the type in this class's constant pool
 *       and answer the question by crashing.
 *   <li>{@link FiefSubjectResolver} is named exactly once, from inside a method body, inside a
 *       {@code try} that catches {@link LinkageError}. Constant-pool entries resolve lazily, so the
 *       resolver is not loaded until that call executes -- and if PatriamHeraldry is a version behind
 *       and the api it ships no longer has the type we compiled against, the error lands in the catch
 *       and disables the bridge instead of the plugin.
 * </ul>
 *
 * <p>{@code softdepend: [PatriamHeraldry]} in plugin.yml is what makes the successful case work at
 * all. Paper only lets a plugin see another plugin's classes when it has declared a relationship with
 * it, and softdepend also orders PatriamHeraldry's enable before ours, so its api is loadable by the
 * time {@link #register} runs. It is a soft dependency rather than a hard one because a fief with no
 * arms is a fief.
 */
public final class HeraldryPresence {

    /**
     * Named as a string rather than as a {@code .class} literal, which is the whole mechanism. See the
     * class javadoc.
     */
    private static final String SUBJECT_RESOLVER_CLASS =
            "com.github.exophobias.patriamheraldry.api.SubjectResolver";

    /** The plugin name as PatriamHeraldry's own plugin.yml declares it. */
    private static final String HERALDRY_PLUGIN = "PatriamHeraldry";

    /** So the absence notice is printed once per startup rather than once per caller. */
    private static final AtomicBoolean ANNOUNCED_ABSENT = new AtomicBoolean();

    /** Owner-side lifecycle changes, expressed without loading PatriamHeraldry when it is absent. */
    public enum PublicationChange {
        NAME,
        EXISTENCE,
        OTHER
    }

    @FunctionalInterface
    private interface PublicationInvalidator {
        void invalidate(UUID fief, PublicationChange change);
    }

    /** No-op until the optional bridge has linked successfully. */
    private static PublicationInvalidator invalidator = (fief, change) -> { };

    private HeraldryPresence() {
    }

    /**
     * Whether the heraldry bridge can be wired: PatriamHeraldry is on the server AND the api type we
     * compiled against can actually be loaded from here.
     *
     * <p>Both halves are needed and they fail differently. A missing plugin is the ordinary case on a
     * server that does not run heraldry and deserves a plain sentence. A plugin that is present with an
     * api we cannot bind to is a version skew between two jars in the same {@code plugins} folder, and
     * that deserves a warning, because somebody updated one of them and not the other.
     *
     * @param plugin the Fiefs plugin, for its logger and its plugin manager
     * @return whether {@link #register} can proceed
     */
    public static boolean installed(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin(HERALDRY_PLUGIN) == null) {
            if (ANNOUNCED_ABSENT.compareAndSet(false, true)) {
                plugin.getLogger().info("PatriamHeraldry is not installed, so a fief cannot bear a "
                        + "coat of arms. Nothing else about Fiefs is affected.");
            }
            return false;
        }
        try {
            Class.forName(SUBJECT_RESOLVER_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            plugin.getLogger().warning("PatriamHeraldry is installed but " + SUBJECT_RESOLVER_CLASS
                    + " could not be loaded from Fiefs, so a fief cannot bear a coat of arms. The two "
                    + "jars are probably from different builds; update both. Nothing else about Fiefs "
                    + "is affected.");
            return false;
        }
    }

    /**
     * Publishes Fiefs' answers about fiefs to PatriamHeraldry, if it is there to hear them.
     *
     * <p>Call this from {@code onEnable} after the store has loaded. Registering earlier would publish
     * a resolver that answers "no such fief" for every fief on the server, and PatriamHeraldry has no
     * way to know the difference.
     *
     * @return whether the bridge was wired. False is a normal outcome, not an error.
     */
    public static boolean register(Plugin plugin, PersistentData persistentData,
                                   MedievalFactionsIntegrator medievalFactionsIntegrator) {
        if (!installed(plugin)) {
            return false;
        }
        try {
            // The only mention of FiefSubjectResolver in the plugin, and the only place it is loaded.
            FiefSubjectResolver.register(plugin, persistentData, medievalFactionsIntegrator);
            invalidator = FiefSubjectResolver::publicationChanged;
            plugin.getLogger().info("Registered fiefs as a PatriamHeraldry subject, so a fief may bear "
                    + "a coat of arms.");
            return true;
        } catch (LinkageError | RuntimeException e) {
            // LinkageError covers the case installed() cannot: PatriamHeraldry ships the api type we
            // asked for, but a different version of it, so the resolver fails to link against the
            // interface it declares. RuntimeException covers a ServicesManager that refuses.
            plugin.getLogger().log(Level.WARNING, "PatriamHeraldry is present but the fief subject "
                    + "resolver could not be registered, so a fief cannot bear a coat of arms. "
                    + "Nothing else about Fiefs is affected.", e);
            return false;
        }
    }

    /**
     * Tell the optional heraldry bridge that a fief was renamed, removed, or restored.
     *
     * <p>Safe to call unconditionally from ordinary Fiefs code. When PatriamHeraldry is absent the
     * installed invalidator is a no-op and no foreign type is linked.
     */
    public static void publicationChanged(UUID fief, PublicationChange change) {
        if (fief != null && change != null) {
            invalidator.invalidate(fief, change);
        }
    }
}
