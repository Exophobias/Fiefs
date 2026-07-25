package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.ClaimView;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

/**
 * @author Daniel McCoy Stephenson
 */
public class ChunkService {
    private final PersistentData persistentData;
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    public ChunkService(PersistentData persistentData, MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this.persistentData = persistentData;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
    }

    public ClaimedChunk getClaimedChunk(Chunk chunk) {
        for (ClaimedChunk claimedChunk : persistentData.getClaimedChunks()) {
            if (claimedChunk.isAt(chunk)) {
                return claimedChunk;
            }
        }
        return null;
    }

    public boolean attemptToClaimChunk(Chunk chunk, Fief fief, Player player) {
        ClaimView mfClaim = medievalFactionsIntegrator.getAPI().getClaimAt(chunk);

        if (mfClaim == null) {
            player.sendMessage(ChatColor.RED + "You can't claim land that your faction hasn't claimed.");
            return false;
        }

        // Verify the MF claim belongs to the player's faction
        FactionView playerFaction = medievalFactionsIntegrator.getAPI().getFactionByPlayer(player.getUniqueId());
        if (playerFaction == null || !mfClaim.getFactionId().equals(playerFaction.getId())) {
            player.sendMessage(ChatColor.RED + "You can't claim land that your faction hasn't claimed.");
            return false;
        }

        ClaimedChunk claimedChunk = getClaimedChunk(chunk);
        if (claimedChunk != null) {
            player.sendMessage(ChatColor.RED + "This chunk is already claimed by " + claimedChunk.getFief() + ".");
            return false;
        }

        if (persistentData.getNumChunksClaimedByFief(fief) >= fief.getCumulativePowerLevel()) {
            player.sendMessage(ChatColor.RED + "Your fief has reached its demesne limit.");
            return false;
        }

        ClaimedChunk newClaimedChunk = new ClaimedChunk(chunk, fief.getFactionId(), fief.getName());
        persistentData.addChunk(newClaimedChunk);
        player.sendMessage(ChatColor.GREEN + "Claimed.");
        return true;
    }

    public boolean attemptToUnclaimChunk(Chunk chunk, Fief fief, Player player) {
        // check that chunk is actually claimed
        ClaimedChunk claimedChunk = getClaimedChunk(chunk);
        if (claimedChunk == null) {
            player.sendMessage(ChatColor.RED + "That chunk is not claimed by a fief.");
            return false;
        }

        // check that chunk is claimed by the player's fief
        if (!claimedChunk.getFief().equalsIgnoreCase(fief.getName())) {
            player.sendMessage(ChatColor.RED + "That chunk doesn't belong to your fief.");
            return false;
        }

        // unclaim the chunk
        persistentData.removeChunk(claimedChunk);
        player.sendMessage(ChatColor.GREEN + "Unclaimed.");
        return true;
    }
}