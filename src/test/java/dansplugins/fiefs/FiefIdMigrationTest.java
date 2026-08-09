package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.google.gson.Gson;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half of the stable-id migration that needs a real save file: the boot that mints an id must also
 * write it.
 *
 * <p>{@code FiefIdentityTest} proves the id survives a save and a load. It cannot prove the save
 * happens. Leaving it to {@code onDisable} looks equivalent and is not: a crash, a {@code kill -9} or a
 * host reboot skips {@code onDisable} entirely, and the next boot then mints DIFFERENT ids for the same
 * fiefs. Nothing about that is visible except a coat of arms that has quietly stopped belonging to
 * anybody, which is why the write happens during the load rather than being trusted to a clean
 * shutdown.
 *
 * <p>So the assertion here is about the file, on disk, before anything has been disabled.
 */
class FiefIdMigrationTest {

    private ServerMock server;

    /** Remembered so each test can confirm it wrote where the plugin actually looked. */
    private File dataFolder;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Registers the fake API the way MedievalFactions registers the real one: via ServicesManager. */
    private void withMedievalFactions() {
        server = MockBukkit.mock();
        server.getServicesManager().register(MedievalFactionsApi.class, new FakeMedievalFactionsApi(),
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);
    }

    private File fiefsJson() {
        dataFolder = PluginDataFolder.create();
        return new File(dataFolder, "fiefs.json");
    }

    /** One fief exactly as a server that predates fief ids would have saved it: no id key at all. */
    private String legacySaveFile(String name) {
        Map<String, String> row = new Fief(null, name, UUID.randomUUID(), "faction-1", new Logger(null))
                .save();
        row.remove("id");
        return new Gson().toJson(List.of(row));
    }

    @Test
    @DisplayName("a legacy fief is given an id, and the id is on disk before anything is disabled")
    void theMintedIdIsWrittenDuringTheBootThatMintsIt() throws Exception {
        withMedievalFactions();
        File saveFile = fiefsJson();
        Files.write(saveFile.toPath(), legacySaveFile("Ashford Mill").getBytes(StandardCharsets.UTF_8));

        Fiefs fiefs = MockBukkit.load(Fiefs.class);
        PluginDataFolder.assertIsWhereThePluginLooked(dataFolder, fiefs);

        Fief loaded = fiefs.getPersistentData().getFief("Ashford Mill");
        assertNotNull(loaded, "the fief must still load");
        assertNotNull(loaded.getId());
        // Read WITHOUT disabling the plugin. Disabling would save anyway, which is exactly the thing
        // that must not be what this depends on.
        String onDisk = Files.readString(saveFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains(loaded.getId().toString()),
                "the minted id must reach fiefs.json during the boot that minted it, not at shutdown");
    }

    @Test
    @DisplayName("the id a legacy fief was given is the id it has after a restart")
    void theMintedIdSurvivesARestart() throws Exception {
        withMedievalFactions();
        File firstBootFile = fiefsJson();
        Files.write(firstBootFile.toPath(),
                legacySaveFile("Ashford Mill").getBytes(StandardCharsets.UTF_8));

        Fiefs firstBoot = MockBukkit.load(Fiefs.class);
        PluginDataFolder.assertIsWhereThePluginLooked(dataFolder, firstBoot);
        UUID afterFirstBoot = firstBoot.getPersistentData().getFief("Ashford Mill").getId();
        // What the first boot left behind. It has to be carried across by hand: unmock() deletes the
        // temp plugins folder, so the second server gets a fresh one.
        String rewritten = Files.readString(firstBootFile.toPath(), StandardCharsets.UTF_8);

        MockBukkit.unmock();
        withMedievalFactions();
        Files.write(fiefsJson().toPath(), rewritten.getBytes(StandardCharsets.UTF_8));

        Fief afterRestart = MockBukkit.load(Fiefs.class).getPersistentData().getFief("Ashford Mill");

        assertEquals(afterFirstBoot, afterRestart.getId(),
                "minting a new id every boot detaches a fief's arms with no error anywhere");
    }

    @Test
    @DisplayName("a save file whose rows have no id does not stop the plugin loading")
    void aLegacyFileIsNotQuarantined() throws Exception {
        // The migration must not route a legacy row into the quarantine, which would take every fief on
        // an upgrading server out of play at once.
        withMedievalFactions();
        Files.write(fiefsJson().toPath(),
                legacySaveFile("Ashford Mill").getBytes(StandardCharsets.UTF_8));

        Fiefs fiefs = MockBukkit.load(Fiefs.class);
        PluginDataFolder.assertIsWhereThePluginLooked(dataFolder, fiefs);

        assertTrue(fiefs.isEnabled());
        assertEquals(1, fiefs.getPersistentData().getFiefs().size());
    }
}
