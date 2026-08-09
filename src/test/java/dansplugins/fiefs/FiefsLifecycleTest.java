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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     *
     * <p>This wrote to {@code plugins/Fiefs} until the fief-id work needed the same path and found that
     * MockBukkit's data folder is {@code plugins/Fiefs-<version>}. The plugin therefore never read the
     * file, started empty, and left it alone for no reason at all -- so the test passed without once
     * exercising what it is named after. See {@link PluginDataFolder}.
     */
    @Test
    void aCorruptSaveFileIsNeverOverwritten() throws Exception {
        withMedievalFactions();

        File dataFolder = PluginDataFolder.create();
        File fiefsJson = new File(dataFolder, "fiefs.json");
        String corrupt = "[{\"name\": \"Ashford Mill\", TRUNCATED-GARBAGE";
        Files.write(fiefsJson.toPath(), corrupt.getBytes(StandardCharsets.UTF_8));

        Exception refusal = null;
        try {
            Fiefs fiefs = MockBukkit.load(Fiefs.class);
            PluginDataFolder.assertIsWhereThePluginLooked(dataFolder, fiefs);
        } catch (Exception expected) {
            // onEnable is expected to fail loudly rather than start empty.
            refusal = expected;
        }
        // Asserted rather than merely swallowed, because a swallowed exception is indistinguishable
        // from no exception -- which is how this test spent its life passing against a file the plugin
        // never opened. The refusal is only reachable if the file was actually read.
        assertNotNull(refusal, "the plugin must refuse to enable rather than start empty");
        assertTrue(refusal.getMessage().contains(fiefsJson.getPath()),
                "the refusal must name the file this test wrote: " + refusal.getMessage());

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
