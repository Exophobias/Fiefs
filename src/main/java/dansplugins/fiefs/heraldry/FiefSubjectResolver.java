package dansplugins.fiefs.heraldry;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import com.github.exophobias.patriamheraldry.api.SubjectKey;
import com.github.exophobias.patriamheraldry.api.SubjectPublicationChangedEvent;
import com.github.exophobias.patriamheraldry.api.SubjectResolver;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers PatriamHeraldry's questions about fiefs, so that a fief can bear a coat of arms.
 *
 * <p>PatriamHeraldry deliberately names no plugin that owns a subject: a faction, a fief and a faith
 * are all an opaque {@code type:id} pair to it, and whoever owns the subject registers the answers.
 * This is Fiefs' side of that inversion, and it is registered on Bukkit's {@code ServicesManager}
 * where PatriamHeraldry reads every resolver at once.
 *
 * <p><b>This class must never be reachable from anything that always loads.</b> It implements a type
 * that only exists when PatriamHeraldry is installed, so loading it on a server without heraldry
 * throws {@link NoClassDefFoundError} while linking, before any code of ours runs. Fiefs is a tier 1
 * plugin and has to keep working there, so {@link HeraldryPresence} is the only thing that names this
 * class, it does so only from inside a method body, and it catches the error. See that class.
 *
 * <p>Everything here keys on {@link Fief#getId()} and never on the fief's name. That is the whole
 * reason the id was added: {@code /fi rename} exists, and a name-keyed armorial would hand a fief's
 * arms to whoever took its old name next.
 */
public final class FiefSubjectResolver implements SubjectResolver {

    private final Plugin plugin;
    private final PersistentData persistentData;
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    /**
     * Subject ids that were not a UUID, so each is complained about once.
     *
     * <p>Only ever touched from the main thread, which the {@code SubjectResolver} contract guarantees,
     * so a plain set is enough. It is bounded by the number of distinct broken keys in PatriamHeraldry's
     * armorial rather than by how often it is asked, which matters because the map sync asks about every
     * subject it draws.
     */
    private final Set<String> unreadableIdsReported = new HashSet<>();

    private FiefSubjectResolver(Plugin plugin, PersistentData persistentData,
                                MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this.plugin = plugin;
        this.persistentData = persistentData;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
    }

    /**
     * Builds and publishes the resolver.
     *
     * <p>Package-private and static so that {@link HeraldryPresence} can reach it with one
     * {@code invokestatic} and nothing else in the plugin can reach it at all. The signature names no
     * heraldry type, which is what lets the caller be a class that always loads.
     */
    static void register(Plugin plugin, PersistentData persistentData,
                         MedievalFactionsIntegrator medievalFactionsIntegrator) {
        plugin.getServer().getServicesManager().register(
                SubjectResolver.class,
                new FiefSubjectResolver(plugin, persistentData, medievalFactionsIntegrator),
                plugin,
                ServicePriority.Normal);
    }

    /** Publish an owner-side lifecycle invalidation after the Fiefs store has changed. */
    static void publicationChanged(UUID fief, HeraldryPresence.PublicationChange change) {
        SubjectPublicationChangedEvent.Reason reason = switch (change) {
            case NAME -> SubjectPublicationChangedEvent.Reason.NAME;
            case EXISTENCE -> SubjectPublicationChangedEvent.Reason.EXISTENCE;
            case OTHER -> SubjectPublicationChangedEvent.Reason.OTHER;
        };
        Bukkit.getPluginManager().callEvent(new SubjectPublicationChangedEvent(
                SubjectKey.fief(fief.toString()), reason));
    }

    @Override
    public SubjectKey.Type type() {
        return SubjectKey.Type.FIEF;
    }

    /**
     * The fief this player is acting for: the one they are IN, holder or not.
     *
     * <p>Membership rather than holding, because the two questions are asked separately. This one is
     * "which fief are we talking about" and the answer is the same for every member of it;
     * {@link #mayAdminister} is "may this player speak for it", and that is where the holder is
     * distinguished from the rest. Answering with the held fief instead would tell a member of a fief
     * they are in no fief at all, which is a worse message than being told the arms are not theirs to
     * change.
     *
     * <p>A player can be in at most one fief, which the join and grant commands enforce, so there is
     * nothing to disambiguate.
     */
    @Override
    public Optional<SubjectKey> subjectOf(Player player) {
        return keyOf(persistentData.getFief(player));
    }

    /**
     * Whether this player may change this fief's arms: its holder, or the head of its faction.
     *
     * <p>Those two and nobody else, which is the authority model {@code /fi grant} and {@code /fi
     * revoke} already use. A fief is held FROM a faction rather than owned outright, so the faction's
     * recorded head can act for any fief of theirs; within a fief, the holder acts and the other members
     * do not. Fiefs has no rank between those two -- there is no officer -- so there is no third case
     * to consider.
     *
     * <p>The faction's head is Medieval Factions' {@code primaryOwnerId}, the identity answer rather
     * than the capability one, for the reason {@code GrantCommand} sets out: granting arms is an act of
     * the person at the top, and there is exactly one of them.
     */
    @Override
    public boolean mayAdminister(Player player, SubjectKey subject) {
        Fief fief = fiefOf(subject);
        if (fief == null) {
            return false;
        }
        if (fief.isOwner(player.getUniqueId())) {
            return true;
        }
        FactionView faction = medievalFactionsIntegrator.getAPI()
                .getFaction(new FactionId(fief.getFactionId()));
        return faction != null && player.getUniqueId().equals(faction.getPrimaryOwnerId());
    }

    /** Holder and faction head authority is natural, so herald bypass tools remain self-dealing. */
    @Override
    public boolean hasNaturalAuthority(Player player, SubjectKey subject) {
        return mayAdminister(player, subject);
    }

    @Override
    public Optional<String> displayName(SubjectKey subject) {
        Fief fief = fiefOf(subject);
        return fief == null ? Optional.empty() : Optional.of(fief.getName());
    }

    /**
     * The fief with this name, case-insensitively.
     *
     * <p>The one place a name becomes a subject, so that a staff member can type a fief's name instead
     * of a uuid. Fief names are unique server-wide -- {@code PersistentData.isNameTaken} refuses a
     * duplicate at creation and at rename -- so there is no ambiguity to resolve.
     */
    @Override
    public Optional<SubjectKey> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return keyOf(persistentData.getFief(name));
    }

    /** Every fief's name, for tab completion and for a staff listing. */
    @Override
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Fief fief : persistentData.getFiefs()) {
            names.add(fief.getName());
        }
        return names;
    }

    /**
     * Whether a fief still answers to this id.
     *
     * <p>Overridden rather than left to the conservative interface default, which deliberately says
     * true because an older resolver cannot prove deletion. Fiefs owns this lifecycle and can answer
     * the question directly, which enables safe automatic retirement.
     */
    @Override
    public boolean exists(SubjectKey subject) {
        return fiefOf(subject) != null;
    }

    /**
     * The fief's holder, or empty when the fief has reverted to its faction and awaits a regrant.
     *
     * <p>Empty is a real answer here rather than a failure. PatriamHeraldry uses this only to tell
     * somebody that their arms are awaiting approval, and a vacant fief has nobody to tell; the
     * faction's head is deliberately not substituted, because they did not submit the design.
     */
    @Override
    public Optional<OfflinePlayer> head(SubjectKey subject) {
        Fief fief = fiefOf(subject);
        if (fief == null || fief.isVacant()) {
            return Optional.empty();
        }
        return Optional.of(plugin.getServer().getOfflinePlayer(fief.getOwnerUUID()));
    }

    /**
     * A fief as a heraldry subject, or empty for no fief.
     *
     * <p>{@link SubjectKey}'s constructor throws {@link IllegalArgumentException} for an id that is
     * blank or contains a colon. A UUID's string form is neither, so the only way to reach that throw
     * is a fief with no id at all, which the load path makes impossible. It is checked anyway because
     * the alternative is an exception surfacing in front of whoever ran {@code /arms set}.
     */
    private Optional<SubjectKey> keyOf(Fief fief) {
        if (fief == null) {
            return Optional.empty();
        }
        UUID id = fief.getId();
        if (id == null) {
            plugin.getLogger().warning("The fief '" + fief.getName() + "' has no stable id, so it "
                    + "cannot bear a coat of arms. This should be impossible; please report it.");
            return Optional.empty();
        }
        return Optional.of(SubjectKey.fief(id.toString()));
    }

    /**
     * The fief a subject refers to, or null.
     *
     * <p>Null for three different things on purpose, because PatriamHeraldry turns all three into the
     * same "no such fief" and none of them is ours to fix: a subject of another type handed to this
     * resolver, an id that is not a UUID, and a UUID for a fief that has been disbanded. A false
     * answer is Fiefs' authoritative lifecycle fact; PatriamHeraldry may use it to move a complete
     * active record into its permanent retirement ledger. This resolver still deletes nothing itself.
     */
    private Fief fiefOf(SubjectKey subject) {
        if (subject == null || subject.type() != SubjectKey.Type.FIEF) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(subject.id());
        } catch (IllegalArgumentException e) {
            // A hand-edited arms.yml, or a record carried over from a server whose fief ids were not
            // uuids. Said once per distinct id: this is reached from the map sync, which asks about
            // every subject it draws, so logging on every call would bury the one line that matters.
            if (unreadableIdsReported.add(subject.id())) {
                plugin.getLogger().warning("A coat of arms is filed under the fief id '" + subject.id()
                        + "', which is not a fief id this server could have issued. It belongs to no "
                        + "fief and will be reported absent for heraldry retirement.");
            }
            return null;
        }
        return persistentData.getFiefById(id);
    }
}
