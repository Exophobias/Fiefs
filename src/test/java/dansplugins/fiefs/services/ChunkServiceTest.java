package dansplugins.fiefs.services;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link ChunkService}'s claim lookup and unclaim path.
 * {@code attemptToClaimChunk(...)} is not exercised here: it reaches through the Medieval
 * Factions integrator into the claim, player and faction services, none of which can be
 * stood up outside a running server. Everything below runs with null integrator and config
 * services, which the unclaim methods under test never dereference.
 */
class ChunkServiceTest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private final PersistentData persistentData = new PersistentData(null);
    private final ChunkService chunkService = new ChunkService(persistentData, null, null);
    private final List<String> messages = new ArrayList<>();
    private final Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

    private Fief newFief(String name) {
        return new Fief(null, name, UUID.randomUUID(), "faction-1", NULL_LOGGER);
    }

    private ClaimedChunk claim(Chunk chunk, String fiefName) {
        ClaimedChunk claimedChunk = new ClaimedChunk(chunk, "faction-1", fiefName);
        persistentData.addChunk(claimedChunk);
        return claimedChunk;
    }

    private String lastMessage() {
        return messages.get(messages.size() - 1);
    }

    @Test
    void getClaimedChunk_returnsTheClaimAtThoseCoordinates() {
        ClaimedChunk claimedChunk = claim(BukkitTestDoubles.chunk("world", 1, 2), "Testopia");

        // The lookup is made with a distinct Chunk instance describing the same location, since
        // Bukkit hands out different Chunk objects for one location and identity is not usable.
        assertSame(claimedChunk, chunkService.getClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2)));
    }

    @Test
    void getClaimedChunk_returnsNullWhenNoClaimIsAtThoseCoordinates() {
        claim(BukkitTestDoubles.chunk("world", 1, 2), "Testopia");

        assertNull(chunkService.getClaimedChunk(BukkitTestDoubles.chunk("world", 3, 4)));
    }

    @Test
    void getClaimedChunk_doesNotMatchTheSameCoordinatesInAnotherWorld() {
        claim(BukkitTestDoubles.chunk("world", 1, 2), "Testopia");

        assertNull(chunkService.getClaimedChunk(BukkitTestDoubles.chunk("world_nether", 1, 2)));
    }

    @Test
    void getClaimedChunk_returnsNullWhenNothingIsClaimed() {
        assertNull(chunkService.getClaimedChunk(BukkitTestDoubles.chunk("world", 1, 2)));
    }

    @Test
    void attemptToUnclaimChunk_removesTheClaimAndConfirmsToThePlayer() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
        claim(chunk, "Testopia");

        boolean unclaimed = chunkService.attemptToUnclaimChunk(chunk, newFief("Testopia"), player);

        assertTrue(unclaimed);
        assertEquals(0, persistentData.getNumChunks());
        assertTrue(lastMessage().contains("Unclaimed."));
    }

    @Test
    void attemptToUnclaimChunk_matchesTheOwningFiefNameCaseInsensitively() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
        claim(chunk, "TESTOPIA");

        boolean unclaimed = chunkService.attemptToUnclaimChunk(chunk, newFief("testopia"), player);

        assertTrue(unclaimed);
        assertEquals(0, persistentData.getNumChunks());
    }

    @Test
    void attemptToUnclaimChunk_rejectsAChunkNoFiefHasClaimed() {
        boolean unclaimed = chunkService.attemptToUnclaimChunk(
                BukkitTestDoubles.chunk("world", 1, 2), newFief("Testopia"), player);

        assertFalse(unclaimed);
        assertTrue(lastMessage().contains("That chunk is not claimed by a fief."));
    }

    @Test
    void attemptToUnclaimChunk_rejectsAChunkClaimedByAnotherFiefAndLeavesItClaimed() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
        claim(chunk, "OtherFief");

        boolean unclaimed = chunkService.attemptToUnclaimChunk(chunk, newFief("Testopia"), player);

        assertFalse(unclaimed);
        assertEquals(1, persistentData.getNumChunks());
        assertEquals("OtherFief", persistentData.getClaimedChunks().get(0).getFief());
        assertTrue(lastMessage().contains("That chunk doesn't belong to your fief."));
    }

    @Test
    void attemptToUnclaimChunk_onlyRemovesTheChunkBeingUnclaimed() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
        claim(chunk, "Testopia");
        claim(BukkitTestDoubles.chunk("world", 3, 4), "Testopia");

        chunkService.attemptToUnclaimChunk(chunk, newFief("Testopia"), player);

        assertEquals(1, persistentData.getNumChunks());
        assertNull(chunkService.getClaimedChunk(chunk));
        assertNotNull(chunkService.getClaimedChunk(BukkitTestDoubles.chunk("world", 3, 4)));
    }
}
