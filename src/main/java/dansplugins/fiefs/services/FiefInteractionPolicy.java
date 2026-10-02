package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** The live claim decision shared by native listeners and external admission checks. */
public final class FiefInteractionPolicy {
    private final ChunkService chunks;
    private final PersistentData data;
    private final MedievalFactionsApi factions;
    private final Logger logger;

    public FiefInteractionPolicy(ChunkService chunks, PersistentData data, MedievalFactionsApi factions, Logger logger) {
        this.chunks = chunks; this.data = data; this.factions = factions; this.logger = logger;
    }

    /** Main-thread read. No event calls, chat, persistence, interaction modes, or world mutation. */
    public boolean canInteractWithBlock(Player player, Block block) {
        return player != null && block != null && player.getWorld().equals(block.getWorld())
                && !denies(chunks.getClaimedChunk(block.getChunk()), player);
    }

    public boolean denies(ClaimedChunk claim, Player player) {
        if (claim == null) return false;
        if (embassyConflict(claim, player)) return true;
        Fief holder = data.getFief(claim.getFief());
        Fief actor = data.getFief(player);
        if (holder == null) {
            logger.log("Claim at " + claim.getWorld() + " " + claim.getX() + "," + claim.getZ()
                    + " names unknown fief '" + claim.getFief() + "'.");
            return true;
        }
        if (actor == null) return true;
        boolean protectedLand = (boolean) actor.getFlags().getFlag("claimedLandProtected");
        return protectedLand && !holder.isSameFief(actor);
    }

    /** Corrupt fief/embassy overlap remains frozen except for the existing MF staff permission. */
    public boolean embassyConflict(ClaimedChunk claim, Player player) {
        if (!claim.getWorld().equals(player.getWorld().getName())) return true;
        try {
            return factions.isEmbassyReservedAt(player.getWorld().getUID(), claim.getX(), claim.getZ())
                    && !player.hasPermission("mf.bypass");
        } catch (RuntimeException | LinkageError unavailable) { return true; }
    }
}
