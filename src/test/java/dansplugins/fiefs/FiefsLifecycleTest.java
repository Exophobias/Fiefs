package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plugin startup and shutdown.
 *
 * <p>This tier exists because it is the only one that can catch "the plugin could not have loaded on
 * a real server" — the exact class of defect that made upstream Fiefs unusable (an NPE in the
 * constructor, an NPE loading any non-empty save file) while still compiling perfectly.
 */
class FiefsLifecycleTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Registers the fake API the way MedievalFactions registers the real one: via ServicesManager. */
    private FakeMedievalFactionsApi withMedievalFactions() {
        server = MockBukkit.mock();
        FakeMedievalFactionsApi api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(
                MedievalFactionsApi.class, api, MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);
        return api;
    }

    @Test
    void enablesWhenMedievalFactionsIsPresent() {
        withMedievalFactions();
        Fiefs fiefs = MockBukkit.load(Fiefs.class);
        assertTrue(fiefs.isEnabled());
    }

    /**
     * Without MF the plugin must decline to enable rather than run half-initialised. On a real server
     * the hard {@code depend:} stops it loading at all; this covers the in-code guard behind that.
     */
    @Test
    void doesNotFunctionWhenMedievalFactionsIsAbsent() {
        server = MockBukkit.mock();
        Fiefs fiefs = MockBukkit.load(Fiefs.class);
        // onEnable returns early; nothing registered, and critically nothing written on disable.
        assertTrue(fiefs.getAPI().getFiefsOfFaction("anything").isEmpty());
    }

    /**
     * The data-loss regression test. A save file that cannot be parsed must survive a full plugin
     * lifecycle untouched. Upstream wrote [] over it on disable, because Bukkit marks a plugin enabled
     * before calling onEnable and does not un-mark it when onEnable throws.
     */
    @Test
    void aCorruptSaveFileIsNeverOverwritten() throws Exception {
        withMedievalFactions();

        File dataFolder = new File(MockBukkit.getMock().getPluginsFolder(), "Fiefs");
        assertTrue(dataFolder.mkdirs() || dataFolder.isDirectory());
        File fiefsJson = new File(dataFolder, "fiefs.json");
        String corrupt = "[{\"name\": \"Ashford Mill\", TRUNCATED-GARBAGE";
        Files.write(fiefsJson.toPath(), corrupt.getBytes(StandardCharsets.UTF_8));

        try {
            MockBukkit.load(Fiefs.class);
        } catch (Exception expected) {
            // onEnable is expected to fail loudly rather than start empty.
        }

        // Disable WITHOUT unmocking: unmock() deletes the temp data folder, so the file has to be
        // read while it still exists. Disabling is the step that used to destroy it.
        server.getPluginManager().disablePlugins();

        assertEquals(corrupt, new String(Files.readAllBytes(fiefsJson.toPath()), StandardCharsets.UTF_8),
                "a corrupt save file must be left exactly as found");
    }

    /** The main class must not be final: MockBukkit subclasses it to load it. */
    @Test
    void mainClassIsSubclassable() {
        assertFalse(java.lang.reflect.Modifier.isFinal(Fiefs.class.getModifiers()),
                "Fiefs must not be final or MockBukkit cannot load it");
    }
}
