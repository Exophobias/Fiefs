package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class FiefsAPI {
    private final PersistentData persistentData;

    public FiefsAPI(PersistentData persistentData) {
        this.persistentData = persistentData;
    }

    public FI_Fief getFief(String fiefName) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(fiefName);
        return fief == null ? null : new FI_Fief(fief);
    }

    public FI_Fief getFief(Player player) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(player);
        return fief == null ? null : new FI_Fief(fief);
    }

    public FI_Fief getFief(UUID playerUUID) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(playerUUID);
        return fief == null ? null : new FI_Fief(fief);
    }

    public ArrayList<FI_Fief> getFiefsOfFaction(String factionId) {
        ArrayList<Fief> fiefs = persistentData.getFiefsOfFaction(factionId);
        ArrayList<FI_Fief> toReturn = new ArrayList<>();
        for (Fief fief : fiefs) {
            toReturn.add(new FI_Fief(fief));
        }
        return toReturn;
    }

    /**
     * The fief this player <b>holds</b>, or null if they hold none.
     *
     * <p>Deliberately distinct from {@link #getFief(UUID)}, which answers "which fief is this player
     * <em>in</em>". Every member of a fief is not entitled to speak for it, and a consumer asking
     * about authority -- a title, a rising, anything a holder may do and a member may not -- wants
     * this one. The two differ for every fief with more than one member, so getting them confused is
     * a mistake that works in testing and fails on a live server.
     *
     * <p>Null for a vacant fief even when asked about its previous holder: a fief that has reverted
     * to its faction is held by nobody, which is a real state rather than an error.
     */
    public FI_Fief getFiefHeldBy(UUID playerUUID) {
        Fief fief = persistentData.getFief(playerUUID);
        if (fief == null || !fief.isOwner(playerUUID)) {
            return null;
        }
        return new FI_Fief(fief);
    }

    /**
     * Every chunk a fief holds, as {@code world name, chunk x, chunk z} triples.
     *
     * <p>Published because a consumer that has to move a fief's land -- a secession does exactly
     * that -- needs to know which chunks they are, and the claim records were internal. Returned as
     * coordinates rather than as Bukkit {@code Chunk}s on purpose: materialising one loads, and if
     * necessary generates, the chunk, so a caller merely counting a fief's land would generate all
     * of it.
     *
     * @return a list of {@code int[]{x, z}} keyed by world name; empty for an unknown fief
     */
    public Map<String, List<int[]>> getClaimedChunksOfFief(String fiefName) {
        Map<String, List<int[]>> byWorld = new LinkedHashMap<>();
        for (ClaimedChunk chunk : persistentData.getClaimedChunks()) {
            if (chunk.getFief() != null && chunk.getFief().equalsIgnoreCase(fiefName)) {
                byWorld.computeIfAbsent(chunk.getWorld(), world -> new ArrayList<>())
                        .add(new int[] {chunk.getX(), chunk.getZ()});
            }
        }
        return byWorld;
    }
}
