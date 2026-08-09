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

    /**
     * Rows that would not parse, held verbatim so a save does not delete them.
     *
     * <p>Not in {@code PersistentData}, deliberately: nothing else in the plugin should be able to
     * see these or act on them. They are bytes waiting to be written back, not fiefs.
     */
    private final List<Map<String, String>> quarantinedFiefs = new ArrayList<>();
    private final List<Map<String, String>> quarantinedChunks = new ArrayList<>();
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
        // Then whatever would not load, exactly as it was read. See loadFiefs.
        fiefs.addAll(quarantinedFiefs);

        writeOutFiles(fiefs, FIEFS_FILE_NAME);
    }

    private void saveClaimedChunks() {
        // save each claimed chunk object individually
        List<Map<String, String>> claimedChunks = new ArrayList<>();
        for (ClaimedChunk claimedChunk : persistentData.getClaimedChunks()){
            claimedChunks.add(claimedChunk.save());
        }
        claimedChunks.addAll(quarantinedChunks);

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

    /**
     * Loads every fief, and quarantines the ones that will not parse rather than dying on them.
     *
     * <p>The file-level failure in {@link #loadDataFromFilename} refuses to enable, and that is right:
     * an unreadable FILE means everything is unknown. A single unreadable ROW is a different thing --
     * the other forty are sitting there perfectly readable, and taking the whole plugin down over one
     * of them means one hand-edited entry costs the server every fief it has.
     *
     * <p>So a bad row is skipped, logged with its contents, and <b>kept</b>: it is written back out
     * verbatim on the next save, so the plugin never silently deletes data it could not understand.
     * It stays quarantined until somebody fixes or removes it, and it is logged every startup so that
     * somebody knows to.
     *
     * <p>This is also where the stable-id migration is completed. A fief loaded from a record written
     * before {@code Fief} had an id mints one, and the file is rewritten immediately rather than at the
     * next save, for the reason set out in {@link #persistMintedIds}.
     */
    private void loadFiefs() {
        persistentData.clearFiefs();
        quarantinedFiefs.clear();

        ArrayList<HashMap<String, String>> data = loadDataFromFilename(dataFile(FIEFS_FILE_NAME));

        int minted = 0;
        for (Map<String, String> fiefData : data){
            try {
                Fief fief = new Fief(fiefData, medievalFactionsIntegrator, logger);
                persistentData.addFief(fief);
                if (fief.hasMintedId()) {
                    minted++;
                }
            } catch (RuntimeException e) {
                quarantine(quarantinedFiefs, fiefData, FIEFS_FILE_NAME, e);
            }
        }
        persistMintedIds(minted);
    }

    /**
     * Writes the save file back when the load minted any stable ids, and says so.
     *
     * <p>The write cannot wait for the autosave or for shutdown. {@code onDisable} does save, so a
     * clean restart would persist the ids anyway, but a crash or a {@code kill -9} would not -- and the
     * next boot would then mint different ids for the same fiefs. Nothing about that failure is
     * visible: the fiefs load, the commands work, and the only casualty is whatever was keyed on the
     * ids, which is a coat of arms silently belonging to nobody after a restart nobody connects it to.
     *
     * <p>One rewrite, on one boot per fief, in exchange for that. It is announced rather than logged
     * through {@link Logger} because that is conditional on debugMode, and an operator who later finds
     * a fief's arms detached needs to be able to find the boot this happened on.
     */
    private void persistMintedIds(int minted) {
        if (minted == 0) {
            return;
        }
        String message = "[Fiefs] " + minted + " fief(s) predated stable fief ids and have each been "
                + "given one. Rewriting " + FIEFS_FILE_NAME + " now so the ids survive an unclean "
                + "shutdown. This happens once.";
        logger.log(message);
        System.out.println(message);
        saveFiefs();
    }

    private void loadClaimedChunks() {
        persistentData.clearClaimedChunks();
        quarantinedChunks.clear();

        ArrayList<HashMap<String, String>> data = loadDataFromFilename(dataFile(CLAIMED_CHUNKS_FILE_NAME));

        for (Map<String, String> claimedChunkData : data){
            try {
                ClaimedChunk claimedChunk = new ClaimedChunk(claimedChunkData);
                persistentData.addChunk(claimedChunk);
            } catch (RuntimeException e) {
                quarantine(quarantinedChunks, claimedChunkData, CLAIMED_CHUNKS_FILE_NAME, e);
            }
        }
    }

    /** Keep an unreadable row aside, and say so loudly enough that somebody fixes it. */
    private void quarantine(List<Map<String, String>> held, Map<String, String> row,
                            String fileName, RuntimeException cause) {
        held.add(new HashMap<>(row));
        String message = "[Fiefs] WARNING: one entry in " + fileName + " could not be read and has "
                + "been skipped. It is kept and will be written back unchanged, so nothing is lost. "
                + "Fix or remove it: " + row + " (" + cause + ")";
        logger.log(message);
        System.out.println(message);
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