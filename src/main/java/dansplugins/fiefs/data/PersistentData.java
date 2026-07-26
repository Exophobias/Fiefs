package dansplugins.fiefs.data;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class PersistentData {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    private final ArrayList<Fief> fiefs = new ArrayList<>();
    private final ArrayList<ClaimedChunk> claimedChunks = new ArrayList<>();

    /**
     * Whether in-memory state has diverged from disk, so the autosave can skip an idle server.
     *
     * <p>INVARIANT: every mutator here sets it. Mutating a {@link Fief} in place (rename, description,
     * flags, membership, owner) does NOT route through this class, so those call sites must call
     * {@link #markDirty()} themselves. Missing one costs at most the changes made since the last
     * save — shutdown always saves unconditionally.
     */
    private boolean dirty = false;

    public PersistentData(MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
    }

    /** Flags in-memory state as needing a write. See {@link #dirty}. */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called by StorageService after a successful write. */
    public void clearDirty() {
        dirty = false;
    }

    public ArrayList<Fief> getFiefs() {
        return fiefs;
    }

    public Fief getFief(String name) {
        for (Fief fief : fiefs) {
            if (fief.getName().equalsIgnoreCase(name)) {
                return fief;
            }
        }
        return null;
    }

    public Fief getFief(Player player) {
        for (Fief fief : fiefs) {
            if (fief.isMember(player.getUniqueId())) {
                return fief;
            }
        }
        return null;
    }

    public Fief getFief(UUID playerUUID) {
        for (Fief fief : fiefs) {
            if (fief.isMember(playerUUID)) {
                return fief;
            }
        }
        return null;
    }

    public ArrayList<Fief> getFiefsOfFaction(FactionView faction) {
        return getFiefsOfFaction(faction.getId().getValue());
    }

    public ArrayList<Fief> getFiefsOfFaction(String factionId) {
        ArrayList<Fief> toReturn = new ArrayList<>();
        for (Fief fief : fiefs) {
            if (fief.getFactionId().equals(factionId)) {
                toReturn.add(fief);
            }
        }
        return toReturn;
    }

    // Fiefs store the faction id; resolve it to the current faction name for display. Note the
    // FactionId wrapper: getFactionByName(String) also exists, and passing a raw id to it would
    // compile cleanly and then return null for every real faction.
    public String getFactionNameOfFief(Fief fief) {
        FactionView faction = medievalFactionsIntegrator.getAPI().getFaction(new FactionId(fief.getFactionId()));
        return faction != null ? faction.getName() : "Unknown";
    }

    public boolean isNameTaken(String name) {
        for (Fief fief : fiefs) {
            if (fief.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean addFief(Fief fief) {
        if (isNameTaken(fief.getName())) {
            return false;
        }
        fiefs.add(fief);
        markDirty();
        return true;
    }

    public boolean removeFief(Fief fiefToRemove) {
        // Unclaim all of the fief's land so the chunks aren't orphaned when the
        // fief is disbanded (via /fi disband or when its faction disbands). #133
        claimedChunks.removeIf(chunk -> chunk.getFief().equalsIgnoreCase(fiefToRemove.getName()));
        markDirty();
        return fiefs.remove(fiefToRemove);
    }

    public void sendListOfFiefsToPlayer(Player player) {

        FactionView faction = medievalFactionsIntegrator.getAPI().getFactionByPlayer(player.getUniqueId());

        if (faction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }

        ArrayList<Fief> listOfFiefs = getFiefsOfFaction(faction);

        if (listOfFiefs.size() == 0) {
            player.sendMessage(ChatColor.AQUA + "Your faction doesn't have any fiefs yet.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "=== Fiefs of " + faction.getName() + " ===");
        player.sendMessage(ChatColor.AQUA + "P: power, M: members, L: land");
        player.sendMessage(ChatColor.AQUA + "-----");
        for (Fief fief : listOfFiefs) {
            player.sendMessage(ChatColor.AQUA + String.format("%-25s %10s %10s %10s", fief.getName(), "P: " +
                    fief.getCumulativePowerLevel(), "M: " + fief.getNumMembers(), "L: " +
                    getNumChunksClaimedByFief(fief)));
        }
    }

    public void clearFiefs() {
        fiefs.clear();
    }

    public void clearClaimedChunks() {
        claimedChunks.clear();
    }

    public void addChunk(ClaimedChunk chunk) {
        claimedChunks.add(chunk);
        markDirty();
    }

    public void removeChunk(ClaimedChunk chunk) {
        claimedChunks.remove(chunk);
        markDirty();
    }

    public int getNumChunks() {
        return claimedChunks.size();
    }

    public ArrayList<ClaimedChunk> getClaimedChunks() {
        return claimedChunks;
    }

    public int getNumChunksClaimedByFief(Fief playersFief) {
        int count = 0;
        for (ClaimedChunk chunk : claimedChunks) {
            if (chunk.getFief().equalsIgnoreCase(playersFief.getName())) {
                count++;
            }
        }
        return count;
    }

    public void sendFiefInfoToPlayer(Player player, Fief playersFief) {
        UUIDChecker uuidChecker = new UUIDChecker();

        int cumulativePowerLevel = playersFief.getCumulativePowerLevel();

        player.sendMessage(ChatColor.AQUA + "=== " + playersFief.getName() + " Fief Info ===");
        player.sendMessage(ChatColor.AQUA + "Name: " + playersFief.getName());
        player.sendMessage(ChatColor.AQUA + "Faction: " + getFactionNameOfFief(playersFief));
        player.sendMessage(ChatColor.AQUA + "Owner: " + uuidChecker.findPlayerNameBasedOnUUID(playersFief.getOwnerUUID()));
        player.sendMessage(ChatColor.AQUA + "Members: " + playersFief.getNumMembers());
        player.sendMessage(ChatColor.AQUA + "Power Level: " + cumulativePowerLevel);
        player.sendMessage(ChatColor.AQUA + "Demesne Size: " + getNumChunksClaimedByFief(playersFief) + "/" + cumulativePowerLevel);
    }
}