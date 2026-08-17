package dansplugins.fiefs.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for safe storage loading: whole-file failures preserve live state and
 * block saves, while malformed individual rows are quarantined without hiding valid rows.
 */
class StorageServiceTest {

    private static final Logger NULL_LOGGER = new Logger(null);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ROW_LIST_TYPE = new TypeToken<ArrayList<HashMap<String, String>>>() { }.getType();
    private static final Path PROC_SELF_FD = Path.of("/proc/self/fd");

    @TempDir
    Path tempDir;

    private Path dataFolder() {
        return tempDir.resolve("plugin-data");
    }

    private StorageService newStorageService(PersistentData persistentData) {
        return new StorageService(
                new ConfigService(null), null, persistentData, NULL_LOGGER, null, dataFolder());
    }

    private Fief newFief(String name) {
        return new Fief(null, name, UUID.randomUUID(), "faction-1", NULL_LOGGER);
    }

    private Map<String, String> claimedChunkData(String world, int x, int z) {
        Map<String, String> data = new HashMap<>();
        data.put("X", GSON.toJson(x));
        data.put("Z", GSON.toJson(z));
        data.put("world", GSON.toJson(world));
        data.put("faction", GSON.toJson("faction-1"));
        data.put("fief", GSON.toJson("Testopia"));
        return data;
    }

    private Path writeJson(Object data) throws IOException {
        Path file = Files.createTempFile(tempDir, "storage-input-", ".json");
        Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        return file;
    }

    private List<Map<String, String>> readRows(Path file) throws IOException {
        return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ROW_LIST_TYPE);
    }

    @Test
    void applyFiefs_populatesPersistentDataFromValidFile() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        List<Map<String, String>> data = new ArrayList<>();
        data.add(newFief("Testopia").save());
        Path file = writeJson(data);

        storageService.applyFiefs(file.toString());

        assertEquals(1, persistentData.getFiefs().size());
        assertEquals("Testopia", persistentData.getFiefs().get(0).getName());
        assertTrue(storageService.isLoadCompletedCleanly());
    }

    @Test
    void applyFiefs_missingFileLeavesPersistentDataEmptyButCountsAsClean() {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);

        storageService.applyFiefs(tempDir.resolve("does-not-exist.json").toString());

        assertEquals(0, persistentData.getFiefs().size());
        assertTrue(storageService.isLoadCompletedCleanly());
    }

    /** A directory cannot be opened as a byte stream, even by a privileged test process. */
    @Test
    void applyFiefs_pathThatCannotBeOpenedForReadingIsTreatedAsUncleanRatherThanEmpty() throws IOException {
        Path unreadableAsFile = Files.createDirectory(tempDir.resolve("not-a-file"));
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);

        storageService.applyFiefs(unreadableAsFile.toString());

        assertEquals(0, persistentData.getFiefs().size());
        assertFalse(storageService.isLoadCompletedCleanly());
    }

    @Test
    void applyFiefs_quarantinesMalformedRowLoadsGoodRowsAndPreservesBadRowOnSave() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        persistentData.addFief(newFief("Preexisting"));
        StorageService storageService = newStorageService(persistentData);

        Map<String, String> good = newFief("Good").save();
        Map<String, String> corrupted = new HashMap<>(newFief("Corrupted").save());
        corrupted.put("ownerUUID", GSON.toJson("not-a-uuid"));
        Path file = writeJson(List.of(good, corrupted));

        storageService.applyFiefs(file.toString());

        assertEquals(1, persistentData.getFiefs().size());
        assertEquals("Good", persistentData.getFiefs().get(0).getName());
        assertTrue(storageService.isLoadCompletedCleanly(), "a bad row is quarantinable, not a file failure");

        storageService.save();

        List<Map<String, String>> saved = readRows(dataFolder().resolve("fiefs.json"));
        assertEquals(2, saved.size());
        assertTrue(saved.contains(corrupted), "the unreadable row must survive a later save unchanged");
    }

    @Test
    void applyFiefs_emptyFileLeavesPersistentDataEmptyButCountsAsClean() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Path file = Files.createTempFile(tempDir, "empty-fiefs-", ".json");
        assertEquals(0, Files.size(file), "test expects a zero-byte file");

        storageService.applyFiefs(file.toString());

        assertEquals(0, persistentData.getFiefs().size());
        assertTrue(storageService.isLoadCompletedCleanly());
    }

    @Test
    void applyClaimedChunks_emptyFileLeavesPersistentDataEmptyButCountsAsClean() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Path file = Files.createTempFile(tempDir, "empty-chunks-", ".json");
        assertEquals(0, Files.size(file), "test expects a zero-byte file");

        storageService.applyClaimedChunks(file.toString());

        assertEquals(0, persistentData.getNumChunks());
        assertTrue(storageService.isLoadCompletedCleanly());
    }

    @Test
    void save_stillWritesAfterLoadingAnEmptyFile() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Path file = Files.createTempFile(tempDir, "empty-before-save-", ".json");
        storageService.applyFiefs(file.toString());
        persistentData.addFief(newFief("CreatedAfterEmptyLoad"));

        storageService.save();

        assertTrue(Files.exists(dataFolder().resolve("fiefs.json")),
                "an empty save file must not disable saving for the session");
    }

    @Test
    void applyFiefs_malformedJsonDoesNotClearPersistentData() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        Fief existing = newFief("Preexisting");
        persistentData.addFief(existing);
        StorageService storageService = newStorageService(persistentData);
        Path file = tempDir.resolve("malformed-input.json");
        Files.writeString(file, "{ not valid json [", StandardCharsets.UTF_8);

        storageService.applyFiefs(file.toString());

        assertEquals(1, persistentData.getFiefs().size());
        assertEquals(existing, persistentData.getFiefs().get(0));
        assertFalse(storageService.isLoadCompletedCleanly());
    }

    @Test
    void load_malformedWholeFileFailsLoudlyInProductionPath() throws IOException {
        Files.createDirectories(dataFolder());
        Path file = dataFolder().resolve("fiefs.json");
        String malformed = "{ not valid json [";
        Files.writeString(file, malformed, StandardCharsets.UTF_8);
        StorageService storageService = newStorageService(new PersistentData(null));

        assertThrows(IllegalStateException.class, storageService::load);

        assertFalse(storageService.isLoadCompletedCleanly());
        assertEquals(malformed, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void applyClaimedChunks_loadsCoordinatesAndQuarantinesMalformedRow() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Map<String, String> good = claimedChunkData("world", 12, -7);
        Map<String, String> corrupted = new HashMap<>(claimedChunkData("world_nether", 3, 4));
        corrupted.put("X", GSON.toJson("not-an-integer"));
        Path file = writeJson(List.of(good, corrupted));

        storageService.applyClaimedChunks(file.toString());

        assertEquals(1, persistentData.getNumChunks());
        ClaimedChunk loaded = persistentData.getClaimedChunks().get(0);
        assertEquals(12, loaded.getX());
        assertEquals(-7, loaded.getZ());
        assertEquals("world", loaded.getWorld());
        assertEquals("faction-1", loaded.getFaction());
        assertEquals("Testopia", loaded.getFief());
        assertTrue(storageService.isLoadCompletedCleanly(), "a bad row is quarantinable, not a file failure");

        storageService.save();

        List<Map<String, String>> saved = readRows(dataFolder().resolve("claimedChunks.json"));
        assertEquals(2, saved.size());
        assertTrue(saved.contains(corrupted), "the unreadable row must survive a later save unchanged");
    }

    @Test
    void applyFiefs_closesTheFileAfterLoadingIt() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Path file = writeJson(List.of(newFief("Testopia").save()));

        storageService.applyFiefs(file.toString());

        assertTrue(storageService.isLoadCompletedCleanly(), "test expects a clean load");
        assertFileReleased(file);
    }

    @Test
    void applyClaimedChunks_closesTheFileAfterLoadingIt() throws IOException {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);
        Path file = writeJson(List.of(claimedChunkData("world", 1, 2)));

        storageService.applyClaimedChunks(file.toString());

        assertEquals(1, persistentData.getNumChunks());
        assertTrue(storageService.isLoadCompletedCleanly(), "test expects a clean load");
        assertFileReleased(file);
    }

    /**
     * Procfs can prove that no descriptor remains open. On platforms without it (notably Windows),
     * an immediate rename exercises the practical guarantee storage needs for atomic replacement.
     */
    private static void assertFileReleased(Path file) throws IOException {
        if (Files.isDirectory(PROC_SELF_FD)) {
            assertFalse(openDescriptorsFor(file), "the save file must not be left open after the load");
            return;
        }

        Path renamed = file.resolveSibling(file.getFileName() + ".released");
        Files.move(file, renamed);
        Files.move(renamed, file);
    }

    private static boolean openDescriptorsFor(Path file) throws IOException {
        Path target = file.toRealPath();
        try (Stream<Path> descriptors = Files.list(PROC_SELF_FD)) {
            return descriptors.anyMatch(descriptor -> {
                try {
                    return Files.readSymbolicLink(descriptor).equals(target);
                } catch (IOException ignored) {
                    return false;
                }
            });
        }
    }

    @Test
    void applyClaimedChunks_missingFileLeavesPersistentDataEmptyAndClean() {
        PersistentData persistentData = new PersistentData(null);
        StorageService storageService = newStorageService(persistentData);

        storageService.applyClaimedChunks(tempDir.resolve("missing-chunks.json").toString());

        assertEquals(0, persistentData.getNumChunks());
        assertTrue(storageService.isLoadCompletedCleanly());
    }

    @Test
    void save_skipsWritingWhenWholeFileLoadDidNotCompleteCleanly() throws IOException {
        Files.createDirectories(dataFolder());
        Path fiefsFile = dataFolder().resolve("fiefs.json");
        String malformed = "{ not valid json [";
        Files.writeString(fiefsFile, malformed, StandardCharsets.UTF_8);
        StorageService storageService = newStorageService(new PersistentData(null));
        storageService.applyFiefs(fiefsFile.toString());
        assertFalse(storageService.isLoadCompletedCleanly());

        assertDoesNotThrow(storageService::save);

        assertEquals(malformed, Files.readString(fiefsFile, StandardCharsets.UTF_8),
                "save() must leave the unreadable source file untouched");
        assertFalse(Files.exists(dataFolder().resolve("claimedChunks.json")),
                "save() must not write either data file after a whole-file load failure");
    }

    @Test
    void save_writesNormallyWhenLoadCompletedCleanly() {
        PersistentData persistentData = new PersistentData(null);
        persistentData.addFief(newFief("Testopia"));
        StorageService storageService = newStorageService(persistentData);
        assertTrue(storageService.isLoadCompletedCleanly());

        storageService.save();

        assertTrue(Files.exists(dataFolder().resolve("fiefs.json")));
        assertTrue(Files.exists(dataFolder().resolve("claimedChunks.json")));
    }
}
