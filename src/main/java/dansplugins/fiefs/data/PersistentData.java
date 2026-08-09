package dansplugins.fiefs.data;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class PersistentData {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    /**
     * Every fief, and every chunk one holds.
     *
     * <p><b>CopyOnWriteArrayList, not ArrayList, and the reason is a reader in another plugin.</b>
     * These are mutated on the main thread by commands and by the Medieval Factions event listeners,
     * and PatriamMFAddon's rebellion layer reads them off it -- from a command thread when somebody
     * asks what they could rise with, and from its asynchronous sweep when a war starts. A plain
     * ArrayList iterated while another thread adds to it throws
     * {@link java.util.ConcurrentModificationException} at best and returns a half-built list at
     * worst.
     *
     * <p>Copy-on-write rather than synchronised because the ratio is right: these are read on
     * movement, on interaction, on every territory check and now from another plugin, and written
     * only when somebody claims, unclaims, founds or disbands. Writes copy the array, reads take no
     * lock at all, and an iterator is a snapshot -- which is exactly the guarantee a cross-plugin
     * reader needs and cannot arrange for itself.
     */
    private final List<Fief> fiefs = new CopyOnWriteArrayList<>();
    private final List<ClaimedChunk> claimedChunks = new CopyOnWriteArrayList<>();

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

    /**
     * Every fief.
     *
     * <p>{@link List} rather than {@code ArrayList} since the backing collection became
     * copy-on-write. Callers iterate it; nothing needs the concrete type, and returning the
     * interface is what lets the implementation carry a thread-safety guarantee a caller in another
     * plugin depends on.
     */
    public List<Fief> getFiefs() {
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

    /**
     * The fief with this stable id, or null.
     *
     * <p>Named differently from the three {@code getFief} overloads on purpose. Two of those already
     * take a {@link UUID} and both mean a PLAYER's uuid, so an id lookup added as a fourth overload
     * would be picked by the compiler and not by the caller: {@code getFief(someFiefId)} would resolve
     * to "the fief this player is in", compile cleanly, and answer null for every fief on the server.
     *
     * @param fiefId the fief's own id, from {@link Fief#getId()}, not a player's
     */
    public Fief getFiefById(UUID fiefId) {
        if (fiefId == null) {
            return null;
        }
        for (Fief fief : fiefs) {
            if (fiefId.equals(fief.getId())) {
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
            player.sendMessage(Component.text("You are not in a faction.", NamedTextColor.RED));
            return;
        }

        ArrayList<Fief> listOfFiefs = getFiefsOfFaction(faction);

        if (listOfFiefs.size() == 0) {
            player.sendMessage(Component.text("Your faction doesn't have any fiefs yet.", NamedTextColor.AQUA));
            return;
        }

        player.sendMessage(Component.text("=== Fiefs of " + faction.getName() + " ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("P: power, M: members, L: land", NamedTextColor.AQUA));
        player.sendMessage(Component.text("-----", NamedTextColor.AQUA));
        for (Fief fief : listOfFiefs) {
            player.sendMessage(Component.text(String.format("%-25s %10s %10s %10s", fief.getName(), "P: " +
                    fief.getCumulativePowerLevel(), "M: " + fief.getNumMembers(), "L: " +
                    getNumChunksClaimedByFief(fief)), NamedTextColor.AQUA));
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

    /** Every claimed chunk. See {@link #getFiefs()} for why this is a {@link List}. */
    public List<ClaimedChunk> getClaimedChunks() {
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

        player.sendMessage(Component.text("=== " + playersFief.getName() + " Fief Info ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Name: " + playersFief.getName(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Faction: " + getFactionNameOfFief(playersFief), NamedTextColor.AQUA));
        // A vacant fief has no holder to name; say who does hold it instead of printing "null".
        String holder = playersFief.isVacant()
                ? getFactionNameOfFief(playersFief) + " (vacant)"
                : uuidChecker.findPlayerNameBasedOnUUID(playersFief.getOwnerUUID());
        player.sendMessage(Component.text("Owner: " + holder, NamedTextColor.AQUA));
        if (playersFief.getHeirUUID() != null) {
            player.sendMessage(Component.text("Heir: "
                    + uuidChecker.findPlayerNameBasedOnUUID(playersFief.getHeirUUID()), NamedTextColor.AQUA));
        }
        player.sendMessage(Component.text("Members: " + playersFief.getNumMembers(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Power Level: " + cumulativePowerLevel, NamedTextColor.AQUA));
        player.sendMessage(Component.text("Demesne Size: " + getNumChunksClaimedByFief(playersFief) + "/" + cumulativePowerLevel, NamedTextColor.AQUA));
    }
}