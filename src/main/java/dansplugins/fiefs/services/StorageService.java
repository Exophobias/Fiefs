package dansplugins.fiefs.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Daniel McCoy Stephenson
 */
public class StorageService {
    private final ConfigService configService;
    private final Fiefs fiefs;
    private final PersistentData persistentData;
    private final Logger logger;
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    private final static String FIEFS_FILE_NAME = "fiefs.json";
    private final static String CLAIMED_CHUNKS_FILE_NAME = "claimedChunks.json";
    private final static Type LIST_MAP_TYPE = new TypeToken<ArrayList<HashMap<String, String>>>(){}.getType();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();


    public StorageService(ConfigService configService, Fiefs fiefs, PersistentData persistentData, Logger logger, MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this.configService = configService;
        this.fiefs = fiefs;
        this.persistentData = persistentData;
        this.logger = logger;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
    }

    /**
     * Writes only if something actually changed. Used by the autosave.
     *
     * <p>The autosave previously serialised and rewrote both files every hour regardless, on the main
     * thread with pretty-printed Gson. An idle server now does no work at all.
     */
    public void saveIfDirty() {
        if (persistentData.isDirty() || configService.hasBeenAltered()) {
            save();
        }
    }

    /**
     * A file inside the plugin's own data folder.
     *
     * <p>Previously hardcoded to "./plugins/Fiefs/", which assumes the server's plugin directory is
     * named "plugins" and sits in the working directory. Bukkit already tells a plugin where its data
     * lives; using that also means tests get an isolated temp folder rather than writing into the
     * repository.
     */
    private File dataFile(String name) {
        return new File(fiefs.getDataFolder(), name);
    }

    public void save() {
        saveFiefs();
        saveClaimedChunks();
        if (configService.hasBeenAltered()) {
            fiefs.saveConfig();
        }
        persistentData.clearDirty();
    }

    public void load() {
        loadFiefs();
        loadClaimedChunks();
    }

    private void saveFiefs() {
        // save each fief object individually
        List<Map<String, String>> fiefs = new ArrayList<>();
        for (Fief fief : persistentData.getFiefs()){
            fiefs.add(fief.save());
        }

        writeOutFiles(fiefs, FIEFS_FILE_NAME);
    }

    private void saveClaimedChunks() {
        // save each claimed chunk object individually
        List<Map<String, String>> claimedChunks = new ArrayList<>();
        for (ClaimedChunk claimedChunk : persistentData.getClaimedChunks()){
            claimedChunks.add(claimedChunk.save());
        }

        writeOutFiles(claimedChunks, CLAIMED_CHUNKS_FILE_NAME);
    }

    /**
     * Writes to a temporary file and then atomically moves it over the target.
     *
     * <p>The previous implementation opened the real file with {@code new FileOutputStream(file)},
     * which truncates it immediately. A crash, a full disk, or a serialization error between the
     * truncate and the write therefore left a half-written or empty save file — losing every fief.
     * Writing elsewhere and moving means the real file is only ever replaced by a file that is already
     * complete on disk.
     */
    private void writeOutFiles(List<Map<String, String>> saveData, String fileName) {
        Path target = dataFile(fileName).toPath();
        Path temp = dataFile(fileName + ".tmp").toPath();
        try {
            Files.createDirectories(target.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(saveData));
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems (notably across volumes) cannot do this atomically. A plain replace
                // is still strictly better than truncate-then-write.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log("Failed to save " + fileName + ": " + e);
            System.out.println("[Fiefs] ERROR: failed to save " + fileName + ": " + e);
        }
    }

    private void loadFiefs() {
        persistentData.clearFiefs();

        ArrayList<HashMap<String, String>> data = loadDataFromFilename(dataFile(FIEFS_FILE_NAME));

        for (Map<String, String> fiefData : data){
            Fief fief = new Fief(fiefData, medievalFactionsIntegrator, logger);
            persistentData.addFief(fief);
        }
    }

    private void loadClaimedChunks() {
        persistentData.clearClaimedChunks();

        ArrayList<HashMap<String, String>> data = loadDataFromFilename(dataFile(CLAIMED_CHUNKS_FILE_NAME));

        for (Map<String, String> claimedChunkData : data){
            ClaimedChunk claimedChunk = new ClaimedChunk(claimedChunkData);
            persistentData.addChunk(claimedChunk);
        }
    }

    /**
     * Reads one save file. Returns an empty list when the file simply does not exist yet, and
     * <b>fails loudly</b> when it exists but cannot be read.
     *
     * <p>That asymmetry is deliberate and is the safe choice. Recovering from a corrupt save by
     * starting empty would let {@code Fiefs#loaded} be set, and the next shutdown would then write
     * {@code []} over the very file that still held the data — turning a recoverable parse error into
     * permanent loss. Throwing aborts {@code onEnable}, leaves {@code loaded} false, and guarantees
     * the existing file is left untouched for manual recovery.
     *
     * <p>Two further defects fixed here: the reader was never closed (on Windows an open handle blocks
     * the atomic replace in {@link #writeOutFiles}), and Gson returns {@code null} for an empty file,
     * which used to NPE in the caller.
     */
    private ArrayList<HashMap<String, String>> loadDataFromFilename(File file) {
        if (!file.exists()) {
            // Normal on first run.
            return new ArrayList<>();
        }
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            ArrayList<HashMap<String, String>> data = gson.fromJson(reader, LIST_MAP_TYPE);
            return data != null ? data : new ArrayList<>();
        } catch (Exception e) {
            logger.log("Failed to read " + file + ": " + e);
            throw new IllegalStateException(
                    "Fiefs could not read " + file + ". Refusing to enable so the file is not "
                            + "overwritten with empty data. Fix or remove the file, then restart.", e);
        }
    }
}