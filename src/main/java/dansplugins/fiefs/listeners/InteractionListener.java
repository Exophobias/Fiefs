package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.services.FiefInteractionPolicy;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * @author Daniel McCoy Stephenson
 */
public class InteractionListener implements Listener {
    private final ChunkService chunkService;
    private final PersistentData persistentData;
    private final Logger logger;
    private final Fiefs fiefs;
    private final FiefInteractionPolicy interactionPolicy;

    public InteractionListener(ChunkService chunkService, PersistentData persistentData, Logger logger,
                               Fiefs fiefs, MedievalFactionsApi factions) {
        this(chunkService, persistentData, logger, fiefs,
                new FiefInteractionPolicy(chunkService, persistentData, factions, logger));
    }

    public InteractionListener(ChunkService chunkService, PersistentData persistentData, Logger logger,
                               Fiefs fiefs, FiefInteractionPolicy interactionPolicy) {
        this.chunkService = chunkService;
        this.persistentData = persistentData;
        this.logger = logger;
        this.fiefs = fiefs;
        this.interactionPolicy = interactionPolicy;
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
            // Normal Fiefs behavior leaves this case to MF. A saved fief/embassy overlap is an
            // inconsistency, so neither side may edit it while staff resolve the competing grants.
            if (embassyConflict(claimedChunk, player)) event.setCancelled(true);
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
            if (embassyConflict(claimedChunk, player)) event.setCancelled(true);
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

    /** An already-open chest must not bypass a conflict discovered after it was opened. */
    @EventHandler
    public void handle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (overlappingInventory(event.getInventory().getHolder(), player)) event.setCancelled(true);
    }

    @EventHandler
    public void handle(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (overlappingInventory(event.getInventory().getHolder(), player)) event.setCancelled(true);
    }

    private boolean overlappingInventory(InventoryHolder holder, Player player) {
        List<Block> blocks;
        if (holder instanceof DoubleChest chest) {
            blocks = java.util.stream.Stream.of(blockOf(chest.getLeftSide()),
                    blockOf(chest.getRightSide())).filter(java.util.Objects::nonNull).toList();
        } else {
            Block block = blockOf(holder);
            blocks = block == null ? List.of() : List.of(block);
        }
        for (Block block : blocks) {
            ClaimedChunk claim = chunkService.getClaimedChunk(block.getChunk());
            if (claim != null && embassyConflict(claim, player)) return true;
        }
        return false;
    }

    private static Block blockOf(InventoryHolder holder) {
        if (holder instanceof BlockInventoryHolder blockHolder) return blockHolder.getBlock();
        if (holder instanceof BlockState state) return state.getBlock();
        if (holder instanceof Entity entity) return entity.getLocation().getBlock();
        return null;
    }

    private boolean shouldEventBeCancelled(ClaimedChunk claimedChunk, Player player) {
        return interactionPolicy.denies(claimedChunk, player);
    }

    private boolean embassyConflict(ClaimedChunk claim, Player player) {
        return interactionPolicy.embassyConflict(claim, player);
    }
}
