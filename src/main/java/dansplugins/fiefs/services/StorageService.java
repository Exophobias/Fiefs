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
    private final Path dataFolderOverride;

    private final static String FIEFS_FILE_NAME = "fiefs.json";
    private final static String CLAIMED_CHUNKS_FILE_NAME = "claimedChunks.json";
    private final static Type LIST_MAP_TYPE = new TypeToken<ArrayList<HashMap<String, String>>>(){}.getType();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Defense in depth for callers other than Fiefs.onEnable(): production loads also throw so the
    // plugin's own loaded flag remains false, but a caller that catches that failure still must not
    // be able to overwrite the unreadable file with stale or empty in-memory state.
    private boolean loadCompletedCleanly = true;

    public StorageService(ConfigService configService, Fiefs fiefs, PersistentData persistentData, Logger logger, MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this(configService, fiefs, persistentData, logger, medievalFactionsIntegrator, null);
    }

    /**
     * Test seam for keeping storage writes inside a caller-owned temporary directory.
     * Production always uses {@link Fiefs#getDataFolder()} through the public constructor.
     */
    StorageService(ConfigService configService, Fiefs fiefs, PersistentData persistentData, Logger logger,
                   MedievalFactionsIntegrator medievalFactionsIntegrator, Path dataFolderOverride) {
        this.configService = configService;
        this.fiefs = fiefs;
        this.persistentData = persistentData;
        this.logger = logger;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.dataFolderOverride = dataFolderOverride;
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
     * lives. Focused tests supply an isolated data-folder override through the package-private
     * constructor instead of writing into a real server-style directory.
     */
    private File dataFile(String name) {
        if (dataFolderOverride != null) {
            return dataFolderOverride.resolve(name).toFile();
        }
        return new File(fiefs.getDataFolder(), name);
    }

    public void save() {
        if (!loadCompletedCleanly) {
            System.out.println("ERROR: skipping save because the last load did not complete cleanly. " +
                    "Fix " + FIEFS_FILE_NAME + "/" + CLAIMED_CHUNKS_FILE_NAME + " and restart to try again.");
            return;
        }
        saveFiefs();
        saveClaimedChunks();
        if (configService.hasBeenAltered()) {
            fiefs.saveConfig();
        }
        persistentData.clearDirty();
    }

    public void load() {
        loadCompletedCleanly = true;
        loadFiefs();
        loadClaimedChunks();
    }

    /**
     * For tests: whether both load() calls this session parsed their files fully,
     * without needing to reach into the private flag directly.
     */
    boolean isLoadCompletedCleanly() {
        return loadCompletedCleanly;
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
            log("Failed to save " + fileName + ": " + e);
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
        applyFiefs(dataFile(FIEFS_FILE_NAME), true);
    }

    private void loadClaimedChunks() {
        applyClaimedChunks(dataFile(CLAIMED_CHUNKS_FILE_NAME), true);
    }

    /** Package-private entry point retained for focused storage tests. */
    void applyFiefs(String filename) {
        applyFiefs(new File(filename), false);
    }

    /**
     * Reads and constructs into temporary collections before replacing live state.
     *
     * <p>A failure to read the file itself leaves both live and quarantined state untouched. Normal
     * startup rethrows that failure so {@code Fiefs.loaded} is never set; the package-private test
     * entry point records the unsafe state and returns, matching the upstream diagnostic API.
     * Individual bad rows are different: they are retained verbatim in quarantine while every good
     * row remains usable.
     */
    private void applyFiefs(File file, boolean failLoudly) {
        final ArrayList<HashMap<String, String>> data;
        try {
            data = loadDataFromFilename(file);
        } catch (RuntimeException e) {
            loadCompletedCleanly = false;
            if (failLoudly) {
                throw e;
            }
            return;
        }

        List<Fief> loaded = new ArrayList<>();
        List<Map<String, String>> quarantined = new ArrayList<>();
        int minted = 0;
        for (Map<String, String> fiefData : data) {
            try {
                Fief fief = new Fief(fiefData, medievalFactionsIntegrator, logger);
                loaded.add(fief);
                if (fief.hasMintedId()) {
                    minted++;
                }
            } catch (RuntimeException e) {
                quarantine(quarantined, fiefData, FIEFS_FILE_NAME, e);
            }
        }

        persistentData.clearFiefs();
        for (Fief fief : loaded) {
            persistentData.addFief(fief);
        }
        quarantinedFiefs.clear();
        quarantinedFiefs.addAll(quarantined);
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
        log(message);
        System.out.println(message);
        saveFiefs();
    }

    void applyClaimedChunks(String filename) {
        applyClaimedChunks(new File(filename), false);
    }

    private void applyClaimedChunks(File file, boolean failLoudly) {
        final ArrayList<HashMap<String, String>> data;
        try {
            data = loadDataFromFilename(file);
        } catch (RuntimeException e) {
            loadCompletedCleanly = false;
            if (failLoudly) {
                throw e;
            }
            return;
        }

        List<ClaimedChunk> loaded = new ArrayList<>();
        List<Map<String, String>> quarantined = new ArrayList<>();
        for (Map<String, String> claimedChunkData : data) {
            try {
                loaded.add(new ClaimedChunk(claimedChunkData));
            } catch (RuntimeException e) {
                quarantine(quarantined, claimedChunkData, CLAIMED_CHUNKS_FILE_NAME, e);
            }
        }

        persistentData.clearClaimedChunks();
        for (ClaimedChunk claimedChunk : loaded) {
            persistentData.addChunk(claimedChunk);
        }
        quarantinedChunks.clear();
        quarantinedChunks.addAll(quarantined);
    }

    /** Keep an unreadable row aside, and say so loudly enough that somebody fixes it. */
    private void quarantine(List<Map<String, String>> held, Map<String, String> row,
                            String fileName, RuntimeException cause) {
        // A literal null is valid JSON and therefore reaches row-level handling rather than the
        // whole-file parser catch. Preserve it as null; trying to copy it would itself throw and
        // turn a safely quarantinable row into a failed startup.
        held.add(row == null ? null : new HashMap<>(row));
        String message = "[Fiefs] WARNING: one entry in " + fileName + " could not be read and has "
                + "been skipped. It is kept and will be written back unchanged, so nothing is lost. "
                + "Fix or remove it: " + row + " (" + cause + ")";
        log(message);
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
        // Keep the stream as its own resource as well as the wrappers around it. If constructing a
        // later wrapper ever fails, Java still closes everything that was opened before it; on
        // Windows that is the difference between a recoverable load failure and a locked save file.
        try (FileInputStream fileInputStream = new FileInputStream(file);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(inputStreamReader)) {
            ArrayList<HashMap<String, String>> data = gson.fromJson(reader, LIST_MAP_TYPE);
            return data != null ? data : new ArrayList<>();
        } catch (Exception e) {
            log("Failed to read " + file + ": " + e);
            throw new IllegalStateException(
                    "Fiefs could not read " + file + ". Refusing to enable so the file is not "
                            + "overwritten with empty data. Fix or remove the file, then restart.", e);
        }
    }

    /** The test-only null-plugin construction has no usable plugin-backed debug logger. */
    private void log(String message) {
        if (fiefs != null && logger != null) {
            logger.log(message);
        }
    }
}
