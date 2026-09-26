package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionUnclaimedChunkEvent;
import dansplugins.fiefs.FakeMedievalFactionsApi;
import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefClaimStatus;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.services.ConfigService;
import dansplugins.fiefs.services.DeferredUnclaimStore;
import dansplugins.fiefs.services.StorageService;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionUnclaimDeferralTest {
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ClaimedChunk claim(String world, int x, int z, String fief) {
        return new ClaimedChunk(Map.of("world", "\"" + world + "\"", "X", Integer.toString(x),
                "Z", Integer.toString(z), "faction", "\"realm\"", "fief", "\"" + fief + "\""));
    }

    private static FactionEventListener.CoverageGate coverageGate(AtomicBoolean ready) {
        return new FactionEventListener.CoverageGate() {
            @Override public boolean suspend() { return ready.getAndSet(false); }
            @Override public void restore(boolean wasReady) { ready.set(wasReady); }
        };
    }

    @Test
    void unloadedWorldCleanupSurvivesRestartAndOnlyTouchesExactWorldAndCoordinates() throws Exception {
        ServerMock server = MockBukkit.mock();
        FakeMedievalFactionsApi factions = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class,
                factions, MockBukkit.createMockPlugin("MedievalFactions"),
                ServicePriority.Normal);
        World first = server.addSimpleWorld("first");
        World other = server.addSimpleWorld("other");
        factions.setFactionClaim(first.getChunkAt(-7, 13), new FactionId("realm"));
        factions.setFactionClaim(other.getChunkAt(-7, 12), new FactionId("realm"));
        Fiefs plugin = MockBukkit.load(Fiefs.class);
        PersistentData data = new PersistentData(null);
        data.addChunk(claim("first", -7, 12, "Old Camp"));
        data.addChunk(claim("first", -7, 13, "Neighbour"));
        data.addChunk(claim("other", -7, 12, "Other World"));
        StorageService storage = new StorageService(new ConfigService(plugin), plugin, data,
                new Logger(plugin), null);
        DeferredUnclaimStore pending = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        pending.load();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean coverage = new AtomicBoolean(true);

        // Simulate the MF event arriving after Bukkit has unloaded this UUID. No claim may be
        // removed by guessing a world name, and the event must reach disk before a restart.
        FactionEventListener firstSession = new FactionEventListener(data, null, pending, storage,
                factions, coverageGate(coverage),
                failure::set, id -> null);
        firstSession.handle(new FactionUnclaimedChunkEvent(new FactionId("realm"),
                first.getUID(), -7, 12));
        assertEquals(FiefClaimStatus.CLAIMED, data.getClaimStatus("first", -7, 12));
        assertEquals(1, pending.forWorld(first.getUID()).size());

        DeferredUnclaimStore afterRestart = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        afterRestart.load();
        FactionEventListener resumed = new FactionEventListener(data, null, afterRestart, storage,
                factions, coverageGate(coverage),
                failure::set, id -> id.equals(first.getUID()) ? first : other);
        resumed.handle(new WorldLoadEvent(other));
        assertEquals(FiefClaimStatus.CLAIMED, data.getClaimStatus("first", -7, 12));
        resumed.handle(new WorldLoadEvent(first));

        assertEquals(FiefClaimStatus.UNCLAIMED, data.getClaimStatus("first", -7, 12));
        assertEquals(FiefClaimStatus.CLAIMED, data.getClaimStatus("first", -7, 13));
        assertEquals(FiefClaimStatus.CLAIMED, data.getClaimStatus("other", -7, 12));
        assertTrue(afterRestart.forWorld(first.getUID()).isEmpty());
        DeferredUnclaimStore disk = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        disk.load();
        assertTrue(disk.forWorld(first.getUID()).isEmpty(), "acknowledgment must persist");
        storage.verifyPersistence();
        assertTrue(coverage.get());
        assertNull(failure.get());
    }

    @Test
    void failedClaimDurabilityBarrierLeavesExactEventQueuedAndFailsClosed() throws Exception {
        ServerMock server = MockBukkit.mock();
        FakeMedievalFactionsApi factions = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class,
                factions, MockBukkit.createMockPlugin("MedievalFactions"),
                ServicePriority.Normal);
        World world = server.addSimpleWorld("camp");
        Fiefs plugin = MockBukkit.load(Fiefs.class);
        PersistentData data = new PersistentData(null);
        data.addChunk(claim("camp", 3, -4, "Old Camp"));
        StorageService storage = new StorageService(new ConfigService(plugin), plugin, data,
                new Logger(plugin), null) {
            @Override public void forceClaimCleanupDurably() throws IOException {
                throw new IOException("simulated directory force failure");
            }
        };
        DeferredUnclaimStore pending = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        pending.load();
        AtomicReference<Throwable> failedClosed = new AtomicReference<>();
        AtomicBoolean coverage = new AtomicBoolean(true);
        FactionEventListener listener = new FactionEventListener(data, null, pending, storage,
                factions, coverageGate(coverage),
                failedClosed::set, id -> world);

        assertThrows(IllegalStateException.class, () -> listener.handle(
                new FactionUnclaimedChunkEvent(new FactionId("realm"), world.getUID(), 3, -4)));
        assertNotNull(failedClosed.get());
        assertEquals(false, coverage.get(), "absence must remain unavailable after barrier failure");
        assertEquals(FiefClaimStatus.UNCLAIMED, data.getClaimStatus("camp", 3, -4));
        DeferredUnclaimStore disk = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        disk.load();
        assertEquals(1, disk.forWorld(world.getUID()).size(),
                "a crash can replay cleanup when the claim save was not proven durable");
    }

    @Test
    void preEventReconciliationFailureNeverRestoresClaimCoverage() throws Exception {
        ServerMock server = MockBukkit.mock();
        FakeMedievalFactionsApi factions = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class,
                factions, MockBukkit.createMockPlugin("MedievalFactions"),
                ServicePriority.Normal);
        World world = server.addSimpleWorld("old-camp");
        Fiefs plugin = MockBukkit.load(Fiefs.class);
        PersistentData data = new PersistentData(null);
        data.addChunk(claim("old-camp", 8, 9, "Old Camp"));
        StorageService storage = new StorageService(new ConfigService(plugin), plugin, data,
                new Logger(plugin), null) {
            @Override public void forceClaimCleanupDurably() throws IOException {
                throw new IOException("simulated file force failure");
            }
        };
        DeferredUnclaimStore pending = new DeferredUnclaimStore(
                plugin.getDataFolder().toPath().resolve("pendingUnclaims.bin"));
        pending.load();
        AtomicBoolean coverage = new AtomicBoolean(true);
        AtomicReference<Throwable> failedClosed = new AtomicReference<>();
        FactionEventListener listener = new FactionEventListener(data, null, pending, storage,
                factions, coverageGate(coverage), failedClosed::set, id -> world);

        assertThrows(IllegalStateException.class, () -> listener.handle(new WorldLoadEvent(world)));
        assertNotNull(failedClosed.get());
        assertEquals(false, coverage.get());
        assertEquals(FiefClaimStatus.UNCLAIMED, data.getClaimStatus("old-camp", 8, 9));
        assertTrue(pending.forWorld(world.getUID()).isEmpty(),
                "the crash window has no MF event to queue; startup must still find stale claims");
    }
}
