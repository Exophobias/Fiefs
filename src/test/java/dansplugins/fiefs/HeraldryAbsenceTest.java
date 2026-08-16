package dansplugins.fiefs;

import com.github.exophobias.patriamheraldry.api.SubjectResolver;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fiefs on a server with no PatriamHeraldry, which is most of them.
 *
 * <p>Fiefs is tier 1. The heraldry bridge implements a type that only exists when PatriamHeraldry is
 * installed, and a class whose {@code implements} clause names a missing type cannot be LOADED at all:
 * the JVM throws {@link NoClassDefFoundError} while linking it, before any code inside it runs, so an
 * {@code if} around the registration call does not save anything that has already been dragged in by a
 * field or a method signature. Getting that wrong takes Fiefs down on every server without heraldry, and
 * it cannot be caught by a suite that always has the api on its classpath -- which, because the
 * dependency is {@code provided}, this one always does.
 *
 * <p>So this tier builds a classloader that hides the api and loads Fiefs' own classes through it. That
 * is the closest a test can get to the real thing: a Paper {@code PluginClassLoader} that has no
 * PatriamHeraldry to delegate to. Concretely it establishes three things.
 *
 * <ol>
 *   <li>Every class in the plugin except the bridge loads AND resolves its own declared fields,
 *       constructors and methods with the api absent. Resolving the declared members is the point: a
 *       class links lazily, so merely loading it would pass even with a heraldry type in a field or a
 *       parameter, and the failure would then surface later, wherever Bukkit or we reflect over it.
 *   <li>The bridge itself genuinely cannot load under that loader. Without this the first assertion
 *       could pass by the api not really being hidden, and the suite would be measuring nothing.
 *   <li>The guard answers, and answers false, with the api absent: it is reached, invoked and returns
 *       rather than throwing. Including the version-skew case, where PatriamHeraldry is present in the
 *       plugins folder but its api cannot be bound to.
 * </ol>
 */
class HeraldryAbsenceTest {

    private static final String HERALDRY_PACKAGE = "com.github.exophobias.patriamheraldry.";
    private static final String PRESENCE = "dansplugins.fiefs.heraldry.HeraldryPresence";
    private static final String RESOLVER = "dansplugins.fiefs.heraldry.FiefSubjectResolver";

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * A classloader that stands in for a server with no PatriamHeraldry.
     *
     * <p>Child-first for {@code dansplugins.fiefs.*} so that Fiefs' classes are defined HERE and their
     * references resolve through this loader rather than through the test classpath, which has the api.
     * Everything else -- Paper, the Medieval Factions api, the JDK -- comes from the parent, because the
     * point is to remove one dependency and not to rebuild the world.
     */
    private static final class HeraldrylessLoader extends URLClassLoader {

        HeraldrylessLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(HERALDRY_PACKAGE)) {
                throw new ClassNotFoundException(name + " is hidden: this loader stands in for a "
                        + "server with no PatriamHeraldry installed");
            }
            if (name.startsWith("dansplugins.fiefs.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> already = findLoadedClass(name);
                    if (already != null) {
                        return already;
                    }
                    try {
                        Class<?> found = findClass(name);
                        if (resolve) {
                            resolveClass(found);
                        }
                        return found;
                    } catch (ClassNotFoundException notOurs) {
                        // The test classes share the package root with the plugin's and live in
                        // target/test-classes, which is not on this loader's path. Nothing here asks for
                        // one, but falling through is better than a confusing failure if something does.
                    }
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static Path compiledClasses() throws Exception {
        return Path.of(Fiefs.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static HeraldrylessLoader heraldryless() throws Exception {
        URL classes = compiledClasses().toUri().toURL();
        return new HeraldrylessLoader(new URL[] {classes},
                HeraldryAbsenceTest.class.getClassLoader());
    }

    /** Every class the build produced, by binary name. */
    private static List<String> everyPluginClass() throws Exception {
        Path root = compiledClasses();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString()
                            .replace(java.io.File.separatorChar, '.')
                            .replace('/', '.')
                            .replaceAll("\\.class$", ""))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("every class but the bridge loads and resolves its own members with the api absent")
    void nothingElseNeedsTheApiToLoad() throws Exception {
        HeraldrylessLoader loader = heraldryless();
        List<String> failed = new ArrayList<>();
        List<String> why = new ArrayList<>();

        for (String name : everyPluginClass()) {
            try {
                Class<?> loaded = Class.forName(name, false, loader);
                // Not initialization: these force the JVM to resolve the types named in every field
                // and method DESCRIPTOR, which is where a leaked heraldry type would hide. Loading the
                // class alone does not, because linking is lazy.
                loaded.getDeclaredFields();
                loaded.getDeclaredConstructors();
                loaded.getDeclaredMethods();
            } catch (ClassNotFoundException | LinkageError e) {
                failed.add(name);
                why.add(name + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            }
        }

        assertEquals(List.of(RESOLVER), failed,
                "exactly one class may need PatriamHeraldry to load, and it is the guarded bridge. "
                        + "Anything else here breaks Fiefs on every server without heraldry: " + why);
    }

    @Test
    @DisplayName("the bridge really cannot load without the api, so the loader is hiding something")
    void theBridgeIsTheThingThatCannotLoad() throws Exception {
        // The control for the test above. If the api were reachable through this loader after all, that
        // one would pass while proving nothing at all.
        HeraldrylessLoader loader = heraldryless();

        assertThrows(LinkageError.class, () -> Class.forName(RESOLVER, false, loader),
                "if this loads, the api is not actually hidden and this tier measures nothing");
        assertNotNull(Class.forName(RESOLVER, false, getClass().getClassLoader()),
                "and it must load perfectly well when the api IS there");
    }

    @Test
    @DisplayName("the guard loads and answers false when PatriamHeraldry is not installed")
    void theGuardAnswersWithoutTheApi() throws Exception {
        HeraldrylessLoader loader = heraldryless();
        Class<?> presence = Class.forName(PRESENCE, true, loader);
        Plugin plugin = MockBukkit.createMockPlugin("Fiefs");

        Method installed = presence.getMethod("installed", Plugin.class);

        assertEquals(false, installed.invoke(null, plugin),
                "the guard must return an answer rather than throw one");

        // Ordinary Fiefs code calls this unconditionally after owner-side mutations. Exercise the
        // method body as well as its api-free signature: the default invalidator must stay a true
        // no-op and must not resolve the guarded bridge merely because a fief was disbanded.
        Class<?> changeType = loader.loadClass(PRESENCE + "$PublicationChange");
        Object existence = java.util.Arrays.stream(changeType.getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().equals("EXISTENCE"))
                .findFirst()
                .orElseThrow();
        Method publicationChanged = presence.getMethod(
                "publicationChanged", java.util.UUID.class, changeType);
        publicationChanged.invoke(null, java.util.UUID.randomUUID(), existence);
    }

    @Test
    @DisplayName("PatriamHeraldry present with an api we cannot bind to disables the bridge, not Fiefs")
    void aVersionSkewDisablesOnlyTheBridge() throws Exception {
        // The failure the plugin-manager check on its own cannot see: both jars are in the plugins
        // folder, but they are from different builds and the api type we compiled against is not in the
        // one that is installed. It has to land in a catch, because there is nothing else to blame it on.
        MockBukkit.createMockPlugin("PatriamHeraldry");
        assertNotNull(server.getPluginManager().getPlugin("PatriamHeraldry"),
                "the skew case needs the plugin to be present, or it degenerates into the absent case");

        HeraldrylessLoader loader = heraldryless();
        Class<?> presence = Class.forName(PRESENCE, true, loader);
        Plugin plugin = MockBukkit.createMockPlugin("Fiefs");

        // Fiefs' own collaborators, built through this loader so nothing crosses back into the parent's
        // view of the world. Each is exactly what Fiefs.onEnable passes.
        Class<?> fiefsClass = loader.loadClass("dansplugins.fiefs.Fiefs");
        Class<?> loggerClass = loader.loadClass("dansplugins.fiefs.utils.Logger");
        Object logger = loggerClass.getConstructor(fiefsClass).newInstance((Object) null);
        Class<?> integratorClass =
                loader.loadClass("dansplugins.fiefs.integrators.MedievalFactionsIntegrator");
        Object integrator = integratorClass.getConstructor(loggerClass).newInstance(logger);
        Class<?> dataClass = loader.loadClass("dansplugins.fiefs.data.PersistentData");
        Object persistentData = dataClass.getConstructor(integratorClass).newInstance(integrator);

        Method register = presence.getMethod("register", Plugin.class, dataClass, integratorClass);

        assertEquals(false, register.invoke(null, plugin, persistentData, integrator),
                "a skew must be reported as a disabled bridge, not thrown out of onEnable");
        assertNull(server.getServicesManager().load(SubjectResolver.class),
                "and nothing may be registered when the bridge could not be built");
    }

    @Test
    @DisplayName("no class outside the heraldry package names a PatriamHeraldry type at all")
    void theBridgeIsTheOnlyPlaceTheApiIsNamed() throws Exception {
        // Belt and braces on the loader tests, and a much clearer failure than a LinkageError when
        // somebody adds an import in the wrong file. Reads the constant pool the crude way: a class file
        // stores every type it references as a UTF-8 string, so the package name is simply in the bytes.
        Path root = compiledClasses();
        String descriptor = HERALDRY_PACKAGE.replace('.', '/');
        List<String> offenders = new ArrayList<>();

        for (String name : everyPluginClass()) {
            if (name.startsWith("dansplugins.fiefs.heraldry.")) {
                continue;
            }
            Path file = root.resolve(name.replace('.', java.io.File.separatorChar) + ".class");
            if (new String(readAll(file), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .contains(descriptor)) {
                offenders.add(name);
            }
        }

        assertTrue(offenders.isEmpty(), "only the guarded heraldry package may name the api: " + offenders);
        assertFalse(offenders.contains(PRESENCE));
    }

    private static byte[] readAll(Path file) throws IOException {
        return Files.readAllBytes(file);
    }
}
