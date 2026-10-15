package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import static org.mockito.Mockito.*;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block break and place handlers refuse on protected fief land by themselves, without relying on
 * the interact handler having cancelled the click that usually precedes them (#211).
 */
class BlockProtectionTest {
    /** {@code getFlag(...)} always logs, and the real logger needs a running plugin (as in FiefFlagsTest). */
    private static final Logger NULL_LOGGER = new Logger(null) {
        @Override
        public void log(String message) {
        }
    };

    private final PersistentData persistentData = new PersistentData(null);
    private final InteractionListener listener =
            new InteractionListener(new ChunkService(persistentData, null), persistentData, NULL_LOGGER, null, mock(MedievalFactionsApi.class));
    private final Chunk fiefLand = BukkitTestDoubles.chunk("world", 3, 4);
    private final Fief north = new Fief(null, "North", UUID.randomUUID(), "faction-1", NULL_LOGGER);

    BlockProtectionTest() {
        persistentData.addFief(north);
        persistentData.addChunk(new ClaimedChunk(fiefLand, "faction-1", "North"));
    }

    private Player player(UUID id, List<String> messages) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(fiefLand.getWorld());
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(player).sendMessage(anyString());
        return player;
    }

    private static BlockPlaceEvent place(Block block, Player player) {
        return new BlockPlaceEvent(block, null, block, null, player, true, EquipmentSlot.HAND);
    }

    @Test
    void breakingOnFiefLandIsRefusedForAPlayerInNoFief() {
        List<String> messages = new ArrayList<>();
        BlockBreakEvent event = new BlockBreakEvent(BukkitTestDoubles.blockIn(fiefLand),
                player(UUID.randomUUID(), messages));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("North"), messages.get(0));
    }

    @Test
    void placingOnFiefLandIsRefusedForAPlayerInNoFief() {
        List<String> messages = new ArrayList<>();
        BlockPlaceEvent event = place(BukkitTestDoubles.blockIn(fiefLand),
                player(UUID.randomUUID(), messages));

        listener.handle(event);

        assertTrue(event.isCancelled());
        assertEquals(1, messages.size());
    }

    @Test
    void theFiefsOwnMemberMayBreakAndPlace() {
        List<String> messages = new ArrayList<>();
        Player owner = player(north.getOwnerUUID(), messages);
        BlockBreakEvent breakEvent = new BlockBreakEvent(BukkitTestDoubles.blockIn(fiefLand), owner);
        BlockPlaceEvent placeEvent = place(BukkitTestDoubles.blockIn(fiefLand), owner);

        listener.handle(breakEvent);
        listener.handle(placeEvent);

        assertFalse(breakEvent.isCancelled());
        assertFalse(placeEvent.isCancelled());
        assertTrue(messages.isEmpty());
    }

    @Test
    void unclaimedLandIsLeftAlone() {
        List<String> messages = new ArrayList<>();
        BlockBreakEvent event = new BlockBreakEvent(BukkitTestDoubles.blockIn(BukkitTestDoubles.chunk("world", 0, 0)),
                player(UUID.randomUUID(), messages));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }
}
