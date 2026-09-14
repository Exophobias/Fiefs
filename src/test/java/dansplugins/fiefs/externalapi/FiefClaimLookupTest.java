package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FiefClaimLookupTest {
    private ClaimedChunk claim(String fief, String world, int x, int z) {
        return new ClaimedChunk(Map.of("X", Integer.toString(x), "Z", Integer.toString(z),
                "world", "\"" + world + "\"", "faction", "\"missing-faction\"", "fief", "\"" + fief + "\""));
    }

    private FiefsAPI api(PersistentData data) {
        return new FiefsAPI(data, null, null, () -> true);
    }

    @Test
    void lookupIncludesOrphansNegativeCoordinatesAndExactWorldIdentityWithoutScanning() {
        PersistentData data = new PersistentData(null);
        AtomicBoolean forbidReads = new AtomicBoolean(false);
        ClaimedChunk orphan = new ClaimedChunk(claim("missing-fief", "world", -4, -8).save()) {
            @Override public String getWorld() {
                assertFalse(forbidReads.get(), "Point lookup must not scan claim records");
                return super.getWorld();
            }
            @Override public boolean isAt(String world, int x, int z) {
                assertFalse(forbidReads.get(), "Point lookup must not scan claim records");
                return super.isAt(world, x, z);
            }
        };
        data.addChunk(orphan);
        forbidReads.set(true);
        FiefsAPI api = api(data);
        assertEquals(FiefClaimStatus.CLAIMED, api.getClaimStatus("world", -4, -8));
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", -3, -8));
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("WORLD", -4, -8));
    }

    @Test
    void duplicateRowsRemovalDisbandAndBulkClearMaintainTheIndex() {
        PersistentData data = new PersistentData(null);
        FiefsAPI api = api(data);
        ClaimedChunk first = claim("one", "world", 0, 0);
        ClaimedChunk second = claim("two", "world", 0, 0);
        data.addChunk(first);
        data.addChunk(second);
        data.removeChunk(first);
        assertEquals(FiefClaimStatus.CLAIMED, api.getClaimStatus("world", 0, 0));
        data.removeChunk(first); // Removing an absent object must not consume the remaining count.
        assertEquals(FiefClaimStatus.CLAIMED, api.getClaimStatus("world", 0, 0));
        Fief fief = new Fief(null, "two", UUID.randomUUID(), "realm", new Logger(null));
        data.addFief(fief);
        data.removeFief(fief);
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", 0, 0));
        data.addChunk(first);
        data.clearClaimedChunks();
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", 0, 0));
        first.setWorld("elsewhere");
        data.addChunk(first);
        assertEquals(FiefClaimStatus.CLAIMED, api.getClaimStatus("elsewhere", 0, 0));
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", 0, 0));
    }

    @Test
    void invalidRowsAndUnknownProviderStateNeverLookUnclaimed() {
        PersistentData data = new PersistentData(null);
        AtomicBoolean ready = new AtomicBoolean(false);
        FiefsAPI api = new FiefsAPI(data, null, null, ready::get);
        assertEquals(FiefClaimStatus.UNAVAILABLE, api.getClaimStatus("world", 0, 0));
        assertEquals(FiefClaimStatus.UNAVAILABLE, new FiefsAPI(data).getClaimStatus("world", 0, 0));
        ready.set(true);
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", 0, 0));
        assertEquals(FiefClaimStatus.UNAVAILABLE, api.getClaimStatus(null, 0, 0));
        assertEquals(FiefClaimStatus.UNAVAILABLE, api.getClaimStatus(" ", 0, 0));
        ClaimedChunk invalid = new ClaimedChunk();
        data.addChunk(invalid);
        assertEquals(FiefClaimStatus.UNAVAILABLE, api.getClaimStatus("world", 0, 0));
        data.removeChunk(invalid);
        assertEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", 0, 0));
        ready.set(false);
        assertEquals(FiefClaimStatus.UNAVAILABLE, api.getClaimStatus("world", 0, 0));
    }

    @Test
    void liveRecordsCannotBypassTheOwnerIndex() {
        PersistentData data = new PersistentData(null);
        ClaimedChunk chunk = claim("one", "world", 0, 0);
        data.addChunk(chunk);
        assertThrows(UnsupportedOperationException.class, () -> data.getClaimedChunks().clear());
        assertThrows(IllegalStateException.class, () -> chunk.setWorld("another"));
        assertEquals(FiefClaimStatus.CLAIMED, api(data).getClaimStatus("world", 0, 0));
        data.removeChunk(chunk);
        chunk.setWorld("another");
        data.addChunk(chunk);
        assertEquals(FiefClaimStatus.CLAIMED, api(data).getClaimStatus("another", 0, 0));
    }

    @Test
    void concurrentDuplicateMutationsNeverHideTheClaimThatRemains() throws Exception {
        PersistentData data = new PersistentData(null);
        data.addChunk(claim("permanent", "world", -1, -1));
        FiefsAPI api = api(data);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var writer = executor.submit(() -> {
                for (int i = 0; i < 2000; i++) {
                    ClaimedChunk duplicate = claim("temporary", "world", -1, -1);
                    data.addChunk(duplicate);
                    data.removeChunk(duplicate);
                }
            });
            var reader = executor.submit(() -> {
                for (int i = 0; i < 20000; i++) {
                    assertNotEquals(FiefClaimStatus.UNCLAIMED, api.getClaimStatus("world", -1, -1));
                }
            });
            writer.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
            assertEquals(FiefClaimStatus.CLAIMED, api.getClaimStatus("world", -1, -1));
        } finally {
            executor.shutdownNow();
        }
    }
}
