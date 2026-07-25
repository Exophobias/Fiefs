package dansplugins.fiefs.listeners;

import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.services.ConfigService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * @author Daniel McCoy Stephenson
 */
public class MoveListener implements Listener {
    private final ConfigService configService;
    private final ChunkService chunkService;
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    public MoveListener(ConfigService configService, ChunkService chunkService, MedievalFactionsIntegrator medievalFactionsIntegrator) {
        this.configService = configService;
        this.chunkService = chunkService;
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
    }

    @EventHandler()
    public void handle(PlayerMoveEvent event) {
        // PlayerMoveEvent fires for every mouse movement, not just for walking. Nothing below this
        // line can change its answer unless the player actually left a block, so this single check
        // removes the overwhelming majority of invocations before any lookup happens.
        if (!event.hasChangedBlock()) {
            return;
        }

        if (!configService.getBoolean("enableTerritoryAlerts")) {
            // territory alerts are disabled
            return;
        }

        Player player = event.getPlayer();

        ClaimedChunk fromChunk = chunkService.getClaimedChunk(event.getFrom().getChunk());
        if (event.getTo() == null) {
            return;
        }
        ClaimedChunk toChunk = chunkService.getClaimedChunk(event.getTo().getChunk());

        // if moving from unclaimed land into claimed land
        if (fromChunk == null && toChunk != null) {
            player.sendMessage(ChatColor.GREEN + "Entering " + toChunk.getFief());
            return;
        }

        // if moving from claimed land into claimed land
        if (fromChunk != null && toChunk != null) {
            // if the holders of the chunks are different
            if (!fromChunk.getFief().equalsIgnoreCase(toChunk.getFief())) {
                player.sendMessage(ChatColor.AQUA + "Entering " + toChunk.getFief());
                return;
            }
        }

        // if moving into unclaimed land
        if (fromChunk != null && toChunk == null) {
            if (medievalFactionsIntegrator.getAPI().getClaimAt(event.getTo().getChunk()) != null) {
                player.sendMessage(ChatColor.AQUA + "Leaving " + fromChunk.getFief());
            }
        }

    }

    @EventHandler(ignoreCancelled = true)
    public void handle(BlockFromToEvent event) {
        // this event handler method will deal with liquid moving from one block to another

        // Liquid flow is one of the highest-frequency events on the server, and the vast majority of
        // it never leaves its own chunk -- downward flow never does. Both cancel branches below
        // require the two chunks to differ, so a same-chunk flow cannot possibly be cancelled: check
        // the coordinates first and skip the lookups entirely. Deriving them by bit-shift rather than
        // Block#getChunk() also avoids resolving a Chunk object per event.
        if ((event.getBlock().getX() >> 4) == (event.getToBlock().getX() >> 4)
                && (event.getBlock().getZ() >> 4) == (event.getToBlock().getZ() >> 4)
                && event.getBlock().getWorld().equals(event.getToBlock().getWorld())) {
            return;
        }

        ClaimedChunk fromChunk = chunkService.getClaimedChunk(event.getBlock().getChunk());
        ClaimedChunk toChunk = chunkService.getClaimedChunk(event.getToBlock().getChunk());

        // if moving from unclaimed land into claimed land
        if (fromChunk == null && toChunk != null) {
            event.setCancelled(true);
            return;
        }

        // if moving from claimed land into claimed land
        if (fromChunk != null && toChunk != null) {
            // if the holders of the chunks are different
            if (!fromChunk.getFief().equalsIgnoreCase(toChunk.getFief())) {
                event.setCancelled(true);
            }
        }

    }
}