package dansplugins.fiefs.listeners;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.services.ConfigService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link MoveListener}: the territory alerts sent to a player crossing
 * a fief border, and the refusal of liquid flowing across one.
 *
 * <p>Not covered: the "Leaving" alert for a move from fief land into land no fief holds. That
 * branch asks Medieval Factions whether the destination is faction land, and an MF plugin instance
 * cannot be built outside a running server. The integrator is null here, so a test that reached
 * that branch would fail with a NullPointerException rather than pass silently.
 */
class MoveListenerTest {

    private final PersistentData persistentData = new PersistentData(null);
    private boolean territoryAlertsEnabled = true;

    private final ConfigService configService = new ConfigService(null) {
        @Override
        public boolean getBoolean(String option) {
            assertEquals("enableTerritoryAlerts", option);
            return territoryAlertsEnabled;
        }
    };

    private final MoveListener listener =
            new MoveListener(configService, new ChunkService(persistentData, null), null);

    private final Chunk wilderness = BukkitTestDoubles.chunk("world", 0, 0);
    private final Chunk northLand = BukkitTestDoubles.chunk("world", 1, 0);
    private final Chunk moreNorthLand = BukkitTestDoubles.chunk("world", 2, 0);
    private final Chunk southLand = BukkitTestDoubles.chunk("world", 0, 1);

    /** Locations hold their world weakly; these keep the worlds behind them reachable. */
    private final List<World> worlds = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

    MoveListenerTest() {
        persistentData.addChunk(new ClaimedChunk(northLand, "faction-1", "North"));
        persistentData.addChunk(new ClaimedChunk(moreNorthLand, "faction-1", "North"));
        persistentData.addChunk(new ClaimedChunk(southLand, "faction-1", "South"));
    }

    private Location locationIn(Chunk chunk) {
        World world = BukkitTestDoubles.worldContaining(chunk);
        worlds.add(world);
        return new Location(world, 0, 64, 0);
    }

    private PlayerMoveEvent move(Chunk from, Chunk to) {
        return new PlayerMoveEvent(player, locationIn(from), to == null ? null : locationIn(to));
    }

    private BlockFromToEvent flow(Chunk from, Chunk to) {
        return new BlockFromToEvent(BukkitTestDoubles.blockIn(from), BukkitTestDoubles.blockIn(to));
    }

    // PlayerMoveEvent: territory alerts

    @Test
    void movingFromUnclaimedLandOntoAFief_announcesTheFiefInGreen() {
        listener.handle(move(wilderness, northLand));

        assertEquals(1, messages.size());
        assertEquals(ChatColor.GREEN + "Entering North", messages.get(0));
    }

    @Test
    void movingFromOneFiefIntoAnother_announcesTheNewFiefInAqua() {
        listener.handle(move(northLand, southLand));

        assertEquals(1, messages.size());
        assertEquals(ChatColor.AQUA + "Entering South", messages.get(0));
    }

    @Test
    void movingBetweenTwoChunksOfTheSameFief_saysNothing() {
        listener.handle(move(northLand, moreNorthLand));

        assertTrue(messages.isEmpty());
    }

    @Test
    void movingBetweenChunksWhoseHoldersDifferOnlyInCase_saysNothing() {
        Chunk lowerCaseNorthLand = BukkitTestDoubles.chunk("world", 3, 0);
        persistentData.addChunk(new ClaimedChunk(lowerCaseNorthLand, "faction-1", "north"));

        listener.handle(move(northLand, lowerCaseNorthLand));

        assertTrue(messages.isEmpty());
    }

    @Test
    void movingWithinUnclaimedLand_saysNothing() {
        listener.handle(move(wilderness, BukkitTestDoubles.chunk("world", -1, -1)));

        assertTrue(messages.isEmpty());
    }

    @Test
    void aMoveWithNoDestination_saysNothing() {
        listener.handle(move(northLand, null));

        assertTrue(messages.isEmpty());
    }

    @Test
    void withTerritoryAlertsDisabled_enteringAFiefSaysNothing() {
        territoryAlertsEnabled = false;

        listener.handle(move(wilderness, northLand));
        listener.handle(move(northLand, southLand));

        assertTrue(messages.isEmpty());
    }

    // BlockFromToEvent: liquid flow across fief borders

    @Test
    void liquidFlowingFromUnclaimedLandOntoAFief_isCancelled() {
        BlockFromToEvent event = flow(wilderness, northLand);

        listener.handle(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void liquidFlowingFromOneFiefIntoAnother_isCancelled() {
        BlockFromToEvent event = flow(northLand, southLand);

        listener.handle(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void liquidFlowingWithinOneFief_isAllowed() {
        BlockFromToEvent event = flow(northLand, moreNorthLand);

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void liquidFlowingOffAFiefIntoUnclaimedLand_isAllowed() {
        BlockFromToEvent event = flow(northLand, wilderness);

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void liquidFlowingWithinUnclaimedLand_isAllowed() {
        BlockFromToEvent event = flow(wilderness, BukkitTestDoubles.chunk("world", -1, -1));

        listener.handle(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void liquidFlowIsRefusedEvenWithTerritoryAlertsDisabled() {
        territoryAlertsEnabled = false;
        BlockFromToEvent event = flow(wilderness, northLand);

        listener.handle(event);

        assertTrue(event.isCancelled());
    }
}
