package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.ClaimView;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

/**
 * @author Daniel McCoy Stephenson
 */
public class ChunkService {
    private final PersistentData persistentData;
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final ConfigService configService;

    public ChunkService(PersistentData persistentData, MedievalFactionsIntegrator medievalFactionsIntegrator, ConfigService configService) {
        this.persistentData = persistentData;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.configService = configService;
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
            player.sendMessage(Component.text("You can't claim land that your faction hasn't claimed.", NamedTextColor.RED));
            return false;
        }

        // Verify the MF claim belongs to the player's faction
        FactionView playerFaction = medievalFactionsIntegrator.getAPI().getFactionByPlayer(player.getUniqueId());
        if (playerFaction == null || !mfClaim.getFactionId().equals(playerFaction.getId())) {
            player.sendMessage(Component.text("You can't claim land that your faction hasn't claimed.", NamedTextColor.RED));
            return false;
        }

        ClaimedChunk claimedChunk = getClaimedChunk(chunk);
        if (claimedChunk != null) {
            player.sendMessage(Component.text("This chunk is already claimed by " + claimedChunk.getFief() + ".", NamedTextColor.RED));
            return false;
        }

        // The limitLand option was previously written to config and echoed by /fi config show, but
        // never read -- the limit applied regardless, so the config lied to the admin.
        if (configService.getBoolean("limitLand")
                && persistentData.getNumChunksClaimedByFief(fief) >= fief.getCumulativePowerLevel()) {
            player.sendMessage(Component.text("Your fief has reached its demesne limit.", NamedTextColor.RED));
            return false;
        }

        ClaimedChunk newClaimedChunk = new ClaimedChunk(chunk, fief.getFactionId(), fief.getName());
        persistentData.addChunk(newClaimedChunk);
        player.sendMessage(Component.text("Claimed.", NamedTextColor.GREEN));
        return true;
    }

    public boolean attemptToUnclaimChunk(Chunk chunk, Fief fief, Player player) {
        // check that chunk is actually claimed
        ClaimedChunk claimedChunk = getClaimedChunk(chunk);
        if (claimedChunk == null) {
            player.sendMessage(Component.text("That chunk is not claimed by a fief.", NamedTextColor.RED));
            return false;
        }

        // check that chunk is claimed by the player's fief
        if (!claimedChunk.getFief().equalsIgnoreCase(fief.getName())) {
            player.sendMessage(Component.text("That chunk doesn't belong to your fief.", NamedTextColor.RED));
            return false;
        }

        // unclaim the chunk
        persistentData.removeChunk(claimedChunk);
        // A capital standing on land the fief no longer holds would be a seat nobody can attack, and
        // one defended by ground somebody else owns. Cleared here rather than checked at every read.
        //
        // NOT the only place this happens: Medieval Factions unclaiming the chunk underneath -- by
        // /f unclaim, a disband, or a conquest -- reaches FactionEventListener instead, which clears
        // it there for the same reason. An earlier version of this comment claimed otherwise and the
        // other path had no clearing at all.
        if (fief.capitalIsAt(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())) {
            fief.clearCapital();
            persistentData.markDirty();
            player.sendMessage(Component.text("That was your capital, so " + fief.getName()
                    + " no longer has one. Name another with /fi capital, or it cannot be risen "
                    + "with.", NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("Unclaimed.", NamedTextColor.GREEN));
        return true;
    }
}