package dansplugins.fiefs.listeners;

import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;

/**
 * @author Daniel McCoy Stephenson
 */
public class InteractionListener implements Listener {
    private final ChunkService chunkService;
    private final PersistentData persistentData;
    private final Logger logger;
    private final Fiefs fiefs;

    public InteractionListener(ChunkService chunkService, PersistentData persistentData, Logger logger, Fiefs fiefs) {
        this.chunkService = chunkService;
        this.persistentData = persistentData;
        this.logger = logger;
        this.fiefs = fiefs;
    }

    @EventHandler()
    public void handle(BlockBreakEvent event) {
        Player player = event.getPlayer();

        Block brokenBlock = event.getBlock();
        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(brokenBlock.getChunk());
        if (claimedChunk == null) {
            return;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            return;
        }

        if (shouldEventBeCancelled(claimedChunk, player)) {
            logger.log("Cancelling Block Break event.");
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        Block clickedBlock = event.getBlock();
        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(clickedBlock.getChunk());
        if (claimedChunk == null) {
            return;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            return;
        }

        if (shouldEventBeCancelled(claimedChunk, player)) {
            logger.log("Cancelling Block Place event.");
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            return;
        }

        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(clickedBlock.getChunk());
        if (claimedChunk == null) {
            return;
        }

        if (shouldEventBeCancelled(claimedChunk, player)) {
            logger.log("Cancelling Player Interact event.");
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        Location location = null;

        if (clickedEntity instanceof ArmorStand) {
            ArmorStand armorStand = (ArmorStand) clickedEntity;

            // get chunk that armor stand is in
            location = armorStand.getLocation();
        }
        else if (clickedEntity instanceof ItemFrame) {
            logger.log("DEBUG: ItemFrame interaction captured in PlayerInteractAtEntityEvent!");
            ItemFrame itemFrame = (ItemFrame) clickedEntity;

            // get chunk that armor stand is in
            location = itemFrame.getLocation();
        }

        if (location != null) {
            Chunk chunk = location.getChunk();
            ClaimedChunk claimedChunk = chunkService.getClaimedChunk(chunk);

            if (shouldEventBeCancelled(claimedChunk, player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler()
    public void handle(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getRemover();

        Entity entity = event.getEntity();

        // get chunk that entity is in
        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(entity.getLocation().getChunk());

        if (shouldEventBeCancelled(claimedChunk, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(PlayerBucketFillEvent event) {
        logger.log("A player is attempting to fill a bucket!");

        Player player = event.getPlayer();

        Block clickedBlock = event.getBlockClicked();

        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(clickedBlock.getChunk());

        if (shouldEventBeCancelled(claimedChunk, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(PlayerBucketEmptyEvent event) {
        if (fiefs.isDebugEnabled()) { System.out.println("DEBUG: A player is attempting to empty a bucket!"); }

        Player player = event.getPlayer();

        Block clickedBlock = event.getBlockClicked();

        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(clickedBlock.getChunk());

        if (shouldEventBeCancelled(claimedChunk, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void handle(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        if (clickedEntity instanceof ItemFrame) {
            if (fiefs.isDebugEnabled()) {
                logger.log("ItemFrame interaction captured in PlayerInteractEntityEvent!");
            }
            ItemFrame itemFrame = (ItemFrame) clickedEntity;

            // get chunk that armor stand is in
            Location location = itemFrame.getLocation();
            Chunk chunk = location.getChunk();
            ClaimedChunk claimedChunk = chunkService.getClaimedChunk(chunk);

            if (shouldEventBeCancelled(claimedChunk, player)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean shouldEventBeCancelled(ClaimedChunk claimedChunk, Player player) {
        if (claimedChunk == null) {
            logger.log("Claimed chunk was null.");
            return false;
        }
        Fief chunkHolder = persistentData.getFief(claimedChunk.getFief());
        Fief playersFief = persistentData.getFief(player);

        if (chunkHolder == null) {
            // A claim naming a fief that no longer exists. Deny and report rather than allowing:
            // returning false here would silently open the protection bypass this method exists to
            // close. Should be unreachable now that /fi rename re-points claims and disbanding
            // unclaims them, so log it as a real inconsistency if it ever fires.
            logger.log("Claim at " + claimedChunk.getWorld() + " " + claimedChunk.getX() + ","
                    + claimedChunk.getZ() + " names unknown fief '" + claimedChunk.getFief() + "'.");
            return true;
        }

        if (playersFief == null) {
            return true;
        }

        boolean claimedLandProtected = (boolean) playersFief.getFlags().getFlag("claimedLandProtected");

        if (!claimedLandProtected) {
            return false;
        }

        return !chunkHolder.isSameFief(playersFief);
    }
}