package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link FiefsAPI}, the entry point other plugins call into.
 * The lookups delegate to {@link PersistentData}, so what is pinned here is the wrapping
 * behaviour of the boundary itself: which {@link Fief} each overload resolves to, and what
 * is handed back when no fief matches.
 *
 * {@code getFief(Player)} is not exercised: it needs a live Bukkit {@code Player}, and the
 * UUID overload covers the same {@link PersistentData} lookup. The Medieval Factions
 * integrator is not exercised either, since none of the methods under test call into it.
 */
class FiefsAPITest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private PersistentData newPersistentData() {
        return new PersistentData(null);
    }

    private Fief newFief(String name, UUID owner, String factionId) {
        return new Fief(null, name, owner, factionId, NULL_LOGGER);
    }

    @Test
    void getFiefByName_wrapsTheMatchingFief() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        FI_Fief wrapped = api.getFief("Testopia");

        assertEquals("Testopia", wrapped.getName());
    }

    @Test
    void getFiefByName_matchesCaseInsensitively() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        FI_Fief wrapped = api.getFief("teSTopIA");

        assertEquals("Testopia", wrapped.getName());
    }

    @Test
    void getFiefByName_returnsNullForUnknownName() {
        FiefsAPI api = new FiefsAPI(newPersistentData());

        assertNull(api.getFief("no-such-fief"));
    }

    @Test
    void getFiefByPlayerUUID_wrapsTheFiefContainingThatMember() {
        PersistentData persistentData = newPersistentData();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Fief fief = newFief("Testopia", owner, "faction-1");
        fief.addMember(member);
        persistentData.addFief(fief);
        persistentData.addFief(newFief("Otherton", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        assertEquals("Testopia", api.getFief(member).getName());
        assertEquals("Testopia", api.getFief(owner).getName());
    }

    @Test
    void getFiefByPlayerUUID_returnsNullWhenNoFiefContainsThePlayer() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        assertNull(api.getFief(UUID.randomUUID()));
    }

    @Test
    void getFiefsOfFaction_returnsOnlyTheFiefsOfThatFaction() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        persistentData.addFief(newFief("Otherton", UUID.randomUUID(), "faction-2"));
        persistentData.addFief(newFief("Thirdville", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        ArrayList<FI_Fief> fiefs = api.getFiefsOfFaction("faction-1");

        assertEquals(2, fiefs.size());
        assertEquals("Testopia", fiefs.get(0).getName());
        assertEquals("Thirdville", fiefs.get(1).getName());
    }

    @Test
    void getFiefsOfFaction_returnsEmptyListForUnknownFaction() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        assertTrue(api.getFiefsOfFaction("faction-99").isEmpty());
    }

    /**
     * Faction ids are matched exactly, unlike fief names, which are matched case-insensitively.
     */
    @Test
    void getFiefsOfFaction_matchesFactionIdCaseSensitively() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        assertTrue(api.getFiefsOfFaction("FACTION-1").isEmpty());
    }

    /**
     * Each call builds fresh wrappers, so wrappers cannot be compared by identity even when
     * they stand for the same fief.
     */
    @Test
    void getFief_returnsANewWrapperOnEachCall() {
        PersistentData persistentData = newPersistentData();
        persistentData.addFief(newFief("Testopia", UUID.randomUUID(), "faction-1"));
        FiefsAPI api = new FiefsAPI(persistentData);

        assertNotSame(api.getFief("Testopia"), api.getFief("Testopia"));
    }
}
