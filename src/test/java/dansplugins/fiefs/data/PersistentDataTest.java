package dansplugins.fiefs.data;

import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link PersistentData}'s in-memory fief/claimed-chunk
 * collection management. The Medieval Factions integrator and Bukkit types are not
 * exercised here since none of the methods under test call into them.
 */
class PersistentDataTest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private PersistentData newPersistentData() {
        return new PersistentData(null);
    }

    private Fief newFief(String name, String factionId) {
        return new Fief(null, name, UUID.randomUUID(), factionId, NULL_LOGGER);
    }

    private ClaimedChunk newClaimedChunk(String fiefName) {
        ClaimedChunk chunk = new ClaimedChunk();
        chunk.setFief(fiefName);
        return chunk;
    }

    @Test
    void addFief_addsNewFief() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");

        boolean added = persistentData.addFief(fief);

        assertTrue(added);
        assertEquals(1, persistentData.getFiefs().size());
    }

    @Test
    void addFief_rejectsDuplicateNameCaseInsensitively() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", "faction-1"));

        boolean added = persistentData.addFief(newFief("TESTOPIA", "faction-2"));

        assertFalse(added);
        assertEquals(1, persistentData.getFiefs().size());
    }

    @Test
    void isNameTaken_trueForExistingNameRegardlessOfCase() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", "faction-1"));

        assertTrue(persistentData.isNameTaken("testopia"));
    }

    @Test
    void isNameTaken_falseForUnknownName() {
        PersistentData persistentData = newPersistentData();

        assertFalse(persistentData.isNameTaken("Nowhere"));
    }

    @Test
    void getFiefByName_returnsMatchCaseInsensitively() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addFief(fief);

        assertEquals(fief, persistentData.getFief("testopia"));
    }

    @Test
    void getFiefByName_returnsNullWhenNotFound() {
        PersistentData persistentData = newPersistentData();

        assertNull(persistentData.getFief("Nowhere"));
    }

    @Test
    void getFiefByPlayerUUID_returnsFiefContainingMember() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        UUID member = UUID.randomUUID();
        fief.addMember(member);
        persistentData.addFief(fief);

        assertEquals(fief, persistentData.getFief(member));
    }

    @Test
    void getFiefByPlayerUUID_returnsNullWhenNoFiefContainsPlayer() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", "faction-1"));

        assertNull(persistentData.getFief(UUID.randomUUID()));
    }

    @Test
    void getFiefsOfFaction_returnsOnlyFiefsOfThatFaction() {
        PersistentData persistentData = newPersistentData();
        Fief matching = newFief("Testopia", "faction-1");
        persistentData.addFief(matching);
        persistentData.addFief(newFief("Otherplace", "faction-2"));

        ArrayList<Fief> result = persistentData.getFiefsOfFaction("faction-1");

        assertEquals(1, result.size());
        assertTrue(result.contains(matching));
    }

    @Test
    void removeFief_removesFiefFromCollection() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addFief(fief);

        boolean removed = persistentData.removeFief(fief);

        assertTrue(removed);
        assertEquals(0, persistentData.getFiefs().size());
    }

    /**
     * Regression guard for Dans-Plugins/Fiefs#133: removing a fief must also release its
     * claimed chunks so they aren't orphaned (unclaimed land that no fief owns anymore).
     */
    @Test
    void removeFief_alsoRemovesItsClaimedChunks() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addFief(fief);
        persistentData.addChunk(newClaimedChunk("Testopia"));
        persistentData.addChunk(newClaimedChunk("Testopia"));
        persistentData.addChunk(newClaimedChunk("OtherFief"));

        persistentData.removeFief(fief);

        assertEquals(1, persistentData.getNumChunks());
        assertEquals("OtherFief", persistentData.getClaimedChunks().get(0).getFief());
    }

    @Test
    void removeFief_matchesClaimedChunksCaseInsensitively() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addFief(fief);
        persistentData.addChunk(newClaimedChunk("TESTOPIA"));

        persistentData.removeFief(fief);

        assertEquals(0, persistentData.getNumChunks());
    }

    @Test
    void clearFiefs_emptiesCollection() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", "faction-1"));

        persistentData.clearFiefs();

        assertEquals(0, persistentData.getFiefs().size());
    }

    @Test
    void clearClaimedChunks_emptiesCollection() {
        PersistentData persistentData = newPersistentData();
        persistentData.addChunk(newClaimedChunk("Testopia"));

        persistentData.clearClaimedChunks();

        assertEquals(0, persistentData.getNumChunks());
    }

    @Test
    void addChunk_increasesChunkCount() {
        PersistentData persistentData = newPersistentData();

        persistentData.addChunk(newClaimedChunk("Testopia"));

        assertEquals(1, persistentData.getNumChunks());
    }

    @Test
    void removeChunk_decreasesChunkCount() {
        PersistentData persistentData = newPersistentData();
        ClaimedChunk chunk = newClaimedChunk("Testopia");
        persistentData.addChunk(chunk);

        persistentData.removeChunk(chunk);

        assertEquals(0, persistentData.getNumChunks());
    }

    @Test
    void getNumChunksClaimedByFief_countsOnlyChunksOfThatFief() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addChunk(newClaimedChunk("Testopia"));
        persistentData.addChunk(newClaimedChunk("Testopia"));
        persistentData.addChunk(newClaimedChunk("OtherFief"));

        assertEquals(2, persistentData.getNumChunksClaimedByFief(fief));
    }

    @Test
    void getNumChunksClaimedByFief_matchesCaseInsensitively() {
        PersistentData persistentData = newPersistentData();
        Fief fief = newFief("Testopia", "faction-1");
        persistentData.addChunk(newClaimedChunk("TESTOPIA"));

        assertEquals(1, persistentData.getNumChunksClaimedByFief(fief));
    }
}
