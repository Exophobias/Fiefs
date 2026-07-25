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

    @EventHandler()
    public void handle(BlockFromToEvent event) {
        // this event handler method will deal with liquid moving from one block to another

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