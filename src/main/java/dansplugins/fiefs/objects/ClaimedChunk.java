package dansplugins.fiefs.objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;

/**
 * A chunk claimed by a fief, stored as plain coordinates.
 *
 * <p>This class used to hold a live {@link Chunk}. That was expensive and destructive: rebuilding it
 * on load called {@code Bukkit.createWorld(new WorldCreator(name))} followed by
 * {@code World.getChunkAt(x, z)}, which is a synchronous main-thread chunk load <em>per persisted
 * claim</em> inside {@code onEnable}. Worse, {@code createWorld} does not return null for a world that
 * no longer exists — it <em>generates a new one</em> from whatever name was in the JSON, and it
 * force-loads worlds an admin deliberately left unloaded.
 *
 * <p>Storing the world name and the two chunk coordinates costs nothing, loads nothing, and is exactly
 * what was already being written to disk, so the save format is unchanged and no migration is needed.
 *
 * @author Daniel McCoy Stephenson
 */
public class ClaimedChunk {
    private int x;
    private int z;
    private String world;
    private String faction;
    private String fief;

    public ClaimedChunk() {

    }

    public ClaimedChunk(Chunk chunk, String faction, String fief) {
        this.x = chunk.getX();
        this.z = chunk.getZ();
        this.world = chunk.getWorld().getName();
        this.faction = faction;
        this.fief = fief;
    }

    public ClaimedChunk(Map<String, String> data) {
        this.load(data);
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public void setFaction(String newFaction) {
        faction = newFaction;
    }

    public String getFaction() {
        return faction;
    }

    public void setFief(String newHolder) {
        fief = newHolder;
    }

    public String getFief() {
        return fief;
    }

    public void setWorld(String worldName) {
        world = worldName;
    }

    public String getWorld() {
        return world;
    }

    /** Whether this claim refers to the same chunk as the given Bukkit chunk. */
    public boolean isAt(Chunk chunk) {
        return x == chunk.getX() && z == chunk.getZ() && chunk.getWorld().getName().equals(world);
    }

    /** Whether this claim refers to the given world name and chunk coordinates. */
    public boolean isAt(String worldName, int chunkX, int chunkZ) {
        return x == chunkX && z == chunkZ && worldName.equals(world);
    }

    public Map<String, String> save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Map<String, String> saveMap = new HashMap<>();
        saveMap.put("X", gson.toJson(x));
        saveMap.put("Z", gson.toJson(z));
        saveMap.put("world", gson.toJson(world));
        saveMap.put("faction", gson.toJson(faction));
        saveMap.put("fief", gson.toJson(fief));

        return saveMap;
    }

    private void load(Map<String, String> data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        world = gson.fromJson(data.get("world"), String.class);
        faction = gson.fromJson(data.get("faction"), String.class);
        fief = gson.fromJson(data.get("fief"), String.class);
        x = gson.fromJson(data.get("X"), Integer.TYPE);
        z = gson.fromJson(data.get("Z"), Integer.TYPE);
    }
}
