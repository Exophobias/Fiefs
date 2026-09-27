package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.event.EmbassyOfferAttemptEvent;
import dansplugins.fiefs.FakeMedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.externalapi.FiefClaimStatus;
import dansplugins.fiefs.objects.ClaimedChunk;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbassyOfferBoundaryListenerTest {
    private static final FactionId HOST = new FactionId("host");
    private static final FactionId GUEST = new FactionId("guest");

    private static World world(UUID id, String name) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private static EmbassyOfferAttemptEvent offer(UUID worldId, int x, int z, boolean accepting,
                                                   boolean asynchronous) {
        return new EmbassyOfferAttemptEvent(worldId, x, z, HOST, GUEST, accepting, asynchronous);
    }

    private static ClaimedChunk fiefClaim(String world, int x, int z) {
        return new ClaimedChunk(Map.of("X", Integer.toString(x), "Z", Integer.toString(z),
                "world", "\"" + world + "\"", "faction", "\"host\"",
                "fief", "\"Host Fief\""));
    }

    @Test
    void vetoesBothOfferAndAcceptanceOnExactFiefChunkEvenFromAnAsyncCaller() {
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "world");
        PersistentData data = new PersistentData(null);
        data.addChunk(fiefClaim("world", -2, 7));
        EmbassyOfferBoundaryListener listener = new EmbassyOfferBoundaryListener(
                new FiefsAPI(data, null, null, () -> true), data,
                new FakeMedievalFactionsApi(), List.of(world), ignored -> { });

        EmbassyOfferAttemptEvent offered = offer(worldId, -2, 7, false, true);
        EmbassyOfferAttemptEvent accepted = offer(worldId, -2, 7, true, true);
        listener.onEmbassyOffer(offered);
        listener.onEmbassyOffer(accepted);
        assertTrue(offered.isCancelled());
        assertTrue(accepted.isCancelled());

        EmbassyOfferAttemptEvent neighbor = offer(worldId, -1, 7, false, true);
        listener.onEmbassyOffer(neighbor);
        assertFalse(neighbor.isCancelled());
    }

    @Test
    void unknownOrUnavailableWorldCoverageCannotBecomeAnOffer() {
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "world");
        AtomicBoolean ready = new AtomicBoolean(false);
        PersistentData data = new PersistentData(null);
        EmbassyOfferBoundaryListener listener = new EmbassyOfferBoundaryListener(
                new FiefsAPI(data, null, null, ready::get), data,
                new FakeMedievalFactionsApi(), List.of(world), ignored -> { });

        EmbassyOfferAttemptEvent unavailable = offer(worldId, 0, 0, false, false);
        listener.onEmbassyOffer(unavailable);
        assertTrue(unavailable.isCancelled());

        ready.set(true);
        EmbassyOfferAttemptEvent free = offer(worldId, 0, 0, false, false);
        listener.onEmbassyOffer(free);
        assertFalse(free.isCancelled());

        listener.onWorldUnload(new WorldUnloadEvent(world));
        EmbassyOfferAttemptEvent unloaded = offer(worldId, 0, 0, false, false);
        listener.onEmbassyOffer(unloaded);
        assertTrue(unloaded.isCancelled());

        listener.onWorldLoad(new WorldLoadEvent(world));
        EmbassyOfferAttemptEvent reloaded = offer(worldId, 0, 0, true, false);
        listener.onEmbassyOffer(reloaded);
        assertFalse(reloaded.isCancelled());
    }

    @Test
    void startupAuditReportsSavedOverlapWithoutRemovingTheFiefClaim() {
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "world");
        PersistentData data = new PersistentData(null);
        data.addChunk(fiefClaim("world", 3, -4));
        FakeMedievalFactionsApi factions = new FakeMedievalFactionsApi();
        factions.setEmbassyReserved(worldId, 3, -4, true);
        List<String> reports = new ArrayList<>();
        EmbassyOfferBoundaryListener listener = new EmbassyOfferBoundaryListener(
                new FiefsAPI(data, null, null, () -> true), data, factions,
                List.of(world), reports::add);

        listener.auditLoadedWorlds();

        assertTrue(reports.stream().anyMatch(line -> line.contains("overlaps an embassy")));
        assertEquals(FiefClaimStatus.CLAIMED, data.getClaimStatus("world", 3, -4));
        EmbassyOfferAttemptEvent offeredAgain = offer(worldId, 3, -4, false, false);
        listener.onEmbassyOffer(offeredAgain);
        assertTrue(offeredAgain.isCancelled());
    }
}
