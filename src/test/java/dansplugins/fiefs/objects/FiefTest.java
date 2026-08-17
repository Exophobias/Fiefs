package dansplugins.fiefs.objects;

import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link Fief}'s in-memory state and the save()/load()
 * round trip. The Medieval Factions integrator and Bukkit types are not exercised
 * here since {@link Fief}'s constructors and the methods under test don't call into them.
 */
class FiefTest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private Fief newFief(UUID owner, String factionId) {
        return new Fief(null, "Test Fief", owner, factionId, NULL_LOGGER);
    }

    @Test
    void constructor_addsOwnerAsMember() {
        UUID owner = UUID.randomUUID();
        Fief fief = newFief(owner, "faction-1");

        assertTrue(fief.isMember(owner));
        assertEquals(1, fief.getNumMembers());
    }

    @Test
    void constructor_initializesDefaultFlags() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");

        assertEquals(true, fief.getFlags().getBooleanValues().get("claimedLandProtected"));
    }

    @Test
    void addMember_addsNewPlayer() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID member = UUID.randomUUID();

        fief.addMember(member);

        assertTrue(fief.isMember(member));
        assertEquals(2, fief.getNumMembers());
    }

    @Test
    void addMember_doesNotDuplicateExistingMember() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID member = UUID.randomUUID();

        fief.addMember(member);
        fief.addMember(member);

        assertEquals(2, fief.getNumMembers());
    }

    @Test
    void removeMember_removesExistingMember() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID member = UUID.randomUUID();
        fief.addMember(member);

        fief.removeMember(member);

        assertFalse(fief.isMember(member));
    }

    @Test
    void removeMember_isNoOpForNonMember() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        int before = fief.getNumMembers();

        fief.removeMember(UUID.randomUUID());

        assertEquals(before, fief.getNumMembers());
    }

    @Test
    void getMembers_returnsUnmodifiableSnapshot() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        List<UUID> members = fief.getMembers();

        assertThrows(UnsupportedOperationException.class, () -> members.add(UUID.randomUUID()));
    }

    @Test
    void invitePlayer_addsInvite() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID invitee = UUID.randomUUID();

        fief.invitePlayer(invitee);

        assertTrue(fief.isInvited(invitee));
    }

    @Test
    void invitePlayer_doesNotDuplicateExistingInvite() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID invitee = UUID.randomUUID();

        fief.invitePlayer(invitee);
        fief.invitePlayer(invitee);

        assertTrue(fief.isInvited(invitee));
    }

    @Test
    void uninvitePlayer_removesInvite() {
        Fief fief = newFief(UUID.randomUUID(), "faction-1");
        UUID invitee = UUID.randomUUID();
        fief.invitePlayer(invitee);

        fief.uninvitePlayer(invitee);

        assertFalse(fief.isInvited(invitee));
    }

    @Test
    void isSameFief_trueForTheSameStableIdAfterSaveAndLoad() {
        UUID owner = UUID.randomUUID();
        Fief original = newFief(owner, "faction-1");
        Fief loaded = new Fief(original.save(), null, NULL_LOGGER);

        assertTrue(original.isSameFief(loaded));
    }

    @Test
    void isSameFief_falseForDistinctFiefsEvenWhenMutableFieldsMatch() {
        UUID owner = UUID.randomUUID();
        Fief a = newFief(owner, "faction-1");
        Fief b = newFief(owner, "faction-1");

        assertFalse(a.isSameFief(b));
    }

    /**
     * Regression guard for Dans-Plugins/Fiefs#150: the load-from-storage constructor used to call
     * load(fiefData) before assigning `flags`, so load() NPE'd on `flags.setIntegerValues(...)`.
     * This is the exact constructor StorageService.loadFiefs() uses on every plugin startup, so
     * the defect meant any server restart with saved fief data failed to load it.
     */
    @Test
    void loadingFromSaveData_doesNotThrow() {
        Map<String, String> saved = newFief(UUID.randomUUID(), "faction-1").save();

        assertDoesNotThrow(() -> new Fief(saved, null, NULL_LOGGER));
    }

    @Test
    void saveThenLoad_preservesScalarFields() {
        UUID owner = UUID.randomUUID();
        Fief original = newFief(owner, "faction-1");
        original.setDescription("A cozy fief");

        Fief loaded = new Fief(original.save(), null, NULL_LOGGER);

        assertEquals("Test Fief", loaded.getName());
        assertEquals("A cozy fief", loaded.getDescription());
        assertEquals(owner, loaded.getOwnerUUID());
        assertEquals("faction-1", loaded.getFactionId());
    }

    @Test
    void saveThenLoad_preservesMembers() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Fief original = newFief(owner, "faction-1");
        original.addMember(member);

        Fief loaded = new Fief(original.save(), null, NULL_LOGGER);

        assertEquals(2, loaded.getNumMembers());
        assertTrue(loaded.isMember(owner));
        assertTrue(loaded.isMember(member));
    }

    @Test
    void saveThenLoad_preservesFlagValues() {
        Fief original = newFief(UUID.randomUUID(), "faction-1");
        original.getFlags().getBooleanValues().put("claimedLandProtected", false);

        Fief loaded = new Fief(original.save(), null, NULL_LOGGER);

        assertEquals(false, loaded.getFlags().getBooleanValues().get("claimedLandProtected"));
    }

    /**
     * Save data written before a flag existed has no entry for it; load() must fall back to the
     * default via FiefFlags.loadMissingFlagsIfNecessary() rather than leaving it absent.
     */
    @Test
    void loadingSaveDataWithoutFlagValues_fallsBackToDefaults() {
        Map<String, String> saved = new HashMap<>(newFief(UUID.randomUUID(), "faction-1").save());
        saved.remove("integerFlagValues");
        saved.remove("booleanFlagValues");
        saved.remove("doubleFlagValues");
        saved.remove("stringFlagValues");

        Fief loaded = new Fief(saved, null, NULL_LOGGER);

        assertEquals(true, loaded.getFlags().getBooleanValues().get("claimedLandProtected"));
    }
}
