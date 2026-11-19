package dansplugins.fiefs.listeners;

import dansplugins.fiefs.data.PersistentData;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import static org.mockito.Mockito.*;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interaction handlers other than block break and place (which {@link BlockProtectionTest}
 * covers): clicking a block, filling a bucket, breaking a hanging entity, and right-clicking an
 * armor stand or item frame on fief land.
 *
 * <p>The bucket-empty handler, and the item-frame branch of the plain entity-interact handler,
 * read {@code Fiefs.isDebugEnabled()} directly and so need a running plugin; they are not
 * exercised here.
 */
class InteractionProtectionTest {
    /** {@code getFlag(...)} always logs, and the real logger needs a running plugin (as in FiefFlagsTest). */
    private static final Logger NULL_LOGGER = new Logger(null) {
        @Override
        public void log(String message) {
        }
    };

    private final PersistentData persistentData = new PersistentData(null);
    private final InteractionListener listener =
            new InteractionListener(new ChunkService(persistentData, null, null), persistentData, NULL_LOGGER, null, mock(MedievalFactionsApi.class));
    private final Chunk fiefLand = BukkitTestDoubles.chunk("world", 3, 4);
    private final Chunk unclaimedLand = BukkitTestDoubles.chunk("world", 0, 0);
    private final Fief north = new Fief(null, "North", UUID.randomUUID(), "faction-1", NULL_LOGGER);
    private final List<String> messages = new ArrayList<>();

    InteractionProtectionTest() {
        persistentData.addFief(north);
        persistentData.addChunk(new ClaimedChunk(fiefLand, "faction-1", "North"));
    }

    private Player outsider() {
        return player(UUID.randomUUID());
    }

    private Player owner() {
        return player(north.getOwnerUUID());
    }

    private Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(fiefLand.getWorld());
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(player).sendMessage(anyString());
        return player;
    }

    private static PlayerInteractEvent click(Player player, Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
    }

    private static PlayerBucketFillEvent fill(Player player, Block block) {
        return new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.BUCKET, null);
    }

    private static PlayerInteractAtEntityEvent clickAt(Player player, org.bukkit.entity.Entity entity) {
        return new PlayerInteractAtEntityEvent(player, entity, new Vector(0, 0, 0));
    }

    // --- PlayerInteractEvent ---

    @Test
    void clickingABlockOnFiefLandIsRefusedForAnOutsider() {
        PlayerInteractEvent event = click(outsider(), BukkitTestDoubles.blockIn(fiefLand));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("North"), messages.get(0));
    }

    @Test
    void clickingABlockOnFiefLandIsAllowedForTheFiefsMember() {
        PlayerInteractEvent event = click(owner(), BukkitTestDoubles.blockIn(fiefLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void clickingABlockOnUnclaimedLandIsLeftAlone() {
        PlayerInteractEvent event = click(outsider(), BukkitTestDoubles.blockIn(unclaimedLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    /**
     * Spigot already reports a click with no block as cancelled (its clicked-block result defaults
     * to DENY), so only the absence of a refusal message distinguishes the handler's early return.
     */
    @Test
    void clickingAirSendsNoRefusal() {
        PlayerInteractEvent event = new PlayerInteractEvent(outsider(), Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP);
        assertTrue(event.isCancelled(), "Spigot's default for a click with no block");

        listener.handle(event);

        assertTrue(messages.isEmpty());
    }

    // --- PlayerBucketFillEvent ---

    @Test
    void fillingABucketOnFiefLandIsRefusedForAnOutsider() {
        PlayerBucketFillEvent event = fill(outsider(), BukkitTestDoubles.blockIn(fiefLand));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
    }

    @Test
    void fillingABucketOnFiefLandIsAllowedForTheFiefsMember() {
        PlayerBucketFillEvent event = fill(owner(), BukkitTestDoubles.blockIn(fiefLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void fillingABucketOnUnclaimedLandIsLeftAlone() {
        PlayerBucketFillEvent event = fill(outsider(), BukkitTestDoubles.blockIn(unclaimedLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    // --- HangingBreakByEntityEvent ---

    @Test
    void breakingAHangingEntityOnFiefLandIsRefusedForAnOutsider() {
        HangingBreakByEntityEvent event =
                new HangingBreakByEntityEvent(BukkitTestDoubles.entityIn(ItemFrame.class, fiefLand), outsider(), mock(DamageSource.class));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
    }

    @Test
    void breakingAHangingEntityOnFiefLandIsAllowedForTheFiefsMember() {
        HangingBreakByEntityEvent event =
                new HangingBreakByEntityEvent(BukkitTestDoubles.entityIn(ItemFrame.class, fiefLand), owner(), mock(DamageSource.class));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void aHangingEntityBrokenByANonPlayerIsLeftAlone() {
        HangingBreakByEntityEvent event = new HangingBreakByEntityEvent(
                BukkitTestDoubles.entityIn(ItemFrame.class, fiefLand), BukkitTestDoubles.entityIn(Creeper.class, fiefLand), mock(DamageSource.class));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    // --- PlayerInteractAtEntityEvent ---

    @Test
    void rightClickingAnArmorStandOnFiefLandIsRefusedForAnOutsider() {
        PlayerInteractAtEntityEvent event = clickAt(outsider(), BukkitTestDoubles.entityIn(ArmorStand.class, fiefLand));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
    }

    @Test
    void rightClickingAnItemFrameAtAPointOnFiefLandIsRefusedForAnOutsider() {
        PlayerInteractAtEntityEvent event = clickAt(outsider(), BukkitTestDoubles.entityIn(ItemFrame.class, fiefLand));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
    }

    @Test
    void rightClickingAnArmorStandOnFiefLandIsAllowedForTheFiefsMember() {
        PlayerInteractAtEntityEvent event = clickAt(owner(), BukkitTestDoubles.entityIn(ArmorStand.class, fiefLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void rightClickingAnArmorStandOnUnclaimedLandIsLeftAlone() {
        PlayerInteractAtEntityEvent event =
                clickAt(outsider(), BukkitTestDoubles.entityIn(ArmorStand.class, unclaimedLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    /** Only armor stands and item frames are protected by this handler; other entities are not looked up. */
    @Test
    void rightClickingAnyOtherEntityOnFiefLandIsLeftAlone() {
        PlayerInteractAtEntityEvent event = clickAt(outsider(), BukkitTestDoubles.entityIn(Villager.class, fiefLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }

    // --- PlayerInteractEntityEvent (non-item-frame branch only; see class comment) ---

    @Test
    void plainRightClickOnAnEntityOtherThanAnItemFrameIsLeftAlone() {
        PlayerInteractEntityEvent event =
                new PlayerInteractEntityEvent(outsider(), BukkitTestDoubles.entityIn(ArmorStand.class, fiefLand));

        listener.handle(event);

        assertFalse(event.isCancelled());
        assertTrue(messages.isEmpty());
    }
}
