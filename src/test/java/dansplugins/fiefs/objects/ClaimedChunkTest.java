package dansplugins.fiefs.objects;

import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.Chunk;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link ClaimedChunk}, covering the on-disk shape of
 * {@code claimedChunks.json} and coordinate-only claim matching.
 */
class ClaimedChunkTest {

    @Test
    void constructor_takesTheWorldNameFromTheChunk() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        assertEquals("world", claimedChunk.getWorld());
    }

    @Test
    void constructor_storesTheCoordinatesFactionAndFiefAsGiven() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);

        ClaimedChunk claimedChunk = new ClaimedChunk(chunk, "faction-1", "Testopia");

        assertEquals(1, claimedChunk.getX());
        assertEquals(2, claimedChunk.getZ());
        assertEquals("faction-1", claimedChunk.getFaction());
        assertEquals("Testopia", claimedChunk.getFief());
    }

    @Test
    void isAt_matchesAnEquivalentChunkWithoutRetainingTheLiveChunk() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        assertTrue(claimedChunk.isAt(BukkitTestDoubles.chunk("world", 1, 2)));
        assertFalse(claimedChunk.isAt(BukkitTestDoubles.chunk("world", 1, 3)));
        assertFalse(claimedChunk.isAt(BukkitTestDoubles.chunk("world_nether", 1, 2)));
    }

    @Test
    void isAt_matchesAWorldNameAndCoordinates() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        assertTrue(claimedChunk.isAt("world", 1, 2));
        assertFalse(claimedChunk.isAt("world", 2, 1));
        assertFalse(claimedChunk.isAt("world_nether", 1, 2));
    }

    @Test
    void save_writesEveryFieldOfTheOnDiskFormatAsJson() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, -2), "faction-1", "Testopia");

        Map<String, String> saved = claimedChunk.save();

        // These five keys and their JSON encoding are the claimedChunks.json format that
        // StorageService writes and ClaimedChunk(Map) reads back; changing either without the
        // other silently orphans every chunk already claimed on a live server.
        assertEquals(5, saved.size());
        assertEquals("1", saved.get("X"));
        assertEquals("-2", saved.get("Z"));
        assertEquals("\"world\"", saved.get("world"));
        assertEquals("\"faction-1\"", saved.get("faction"));
        assertEquals("\"Testopia\"", saved.get("fief"));
    }

    @Test
    void mapConstructor_roundTripsTheStoredClaimWithoutLoadingAChunk() {
        ClaimedChunk original = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, -2), "faction-1", "Testopia");

        ClaimedChunk restored = new ClaimedChunk(original.save());

        assertEquals(1, restored.getX());
        assertEquals(-2, restored.getZ());
        assertEquals("world", restored.getWorld());
        assertEquals("faction-1", restored.getFaction());
        assertEquals("Testopia", restored.getFief());
        assertEquals(original.save(), restored.save());
    }

    @Test
    void setWorld_replacesTheStoredWorldName() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        claimedChunk.setWorld("world_nether");

        assertEquals("world_nether", claimedChunk.getWorld());
        assertEquals("\"world_nether\"", claimedChunk.save().get("world"));
    }

    @Test
    void setFief_replacesTheOwningFief() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        claimedChunk.setFief("OtherFief");

        assertEquals("OtherFief", claimedChunk.getFief());
    }

    @Test
    void setFaction_replacesTheOwningFaction() {
        ClaimedChunk claimedChunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2), "faction-1", "Testopia");

        claimedChunk.setFaction("faction-2");

        assertEquals("faction-2", claimedChunk.getFaction());
    }
}
