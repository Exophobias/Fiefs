package dansplugins.fiefs;

import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fief's stable id, and the migration that gives one to a fief that predates it.
 *
 * <p>The failure this tier exists to catch is a fresh id minted on every boot. It has no symptom worth
 * noticing: the fiefs load, every command works, and the only casualty is whatever was keyed on the id.
 * The first such consumer is PatriamHeraldry's armorial, so the visible effect is a coat of arms that
 * attaches, works all session, and belongs to nobody after the next restart -- with nothing logged
 * anywhere and nothing to connect it to the restart.
 *
 * <p>No MockBukkit here on purpose, matching {@code FiefCapitalTest}: {@link Fief} serialises to a plain
 * string map, so the round trip that actually matters needs no server. {@code FiefIdMigrationTest}
 * covers the half that does, which is the file being rewritten during the boot that mints.
 */
class FiefIdentityTest {

    private static final Logger LOGGER = new Logger(null);

    private Fief fief(String name, UUID holder) {
        return new Fief(null, name, holder, "f-rhundal", LOGGER);
    }

    @Test
    @DisplayName("a new fief has an id, and no two fiefs share one")
    void aNewFiefHasAnId() {
        Fief ashford = fief("Ashford", UUID.randomUUID());
        Fief bramley = fief("Bramley", UUID.randomUUID());

        assertNotNull(ashford.getId());
        assertNotEquals(ashford.getId(), bramley.getId());
        assertFalse(ashford.hasMintedId(), "a fief created now was not migrated into having an id");
    }

    @Test
    @DisplayName("the id is safe to use as a PatriamHeraldry subject id")
    void theIdIsALegalSubjectId() {
        // SubjectKey's constructor throws IllegalArgumentException for an id that is blank or contains
        // a colon, and it would throw in front of whoever ran /arms set. A UUID's string form is
        // neither, which is the reason a UUID was chosen over anything derived from the fief's name.
        String id = fief("Ashford", UUID.randomUUID()).getId().toString();

        assertFalse(id.isBlank());
        assertFalse(id.contains(":"), "a colon would make SubjectKey throw");
    }

    @Test
    @DisplayName("a persisted id is read back unchanged")
    void aPersistedIdSurvivesTheRoundTrip() {
        Fief ashford = fief("Ashford", UUID.randomUUID());
        UUID id = ashford.getId();

        Fief loaded = new Fief(ashford.save(), null, LOGGER);

        assertEquals(id, loaded.getId());
        assertFalse(loaded.hasMintedId(), "reading an id is not minting one");
    }

    @Test
    @DisplayName("a fief saved before ids existed is minted one, and keeps it on the next load")
    void aLegacyFiefIsMigratedOnceAndOnlyOnce() {
        // The whole migration, in the order a server experiences it. The second load is the assertion
        // that matters: minting again there is the silent failure this tier exists for.
        Map<String, String> legacy = fief("Ashford", UUID.randomUUID()).save();
        legacy.remove("id");

        Fief firstBoot = new Fief(legacy, null, LOGGER);
        UUID minted = firstBoot.getId();
        assertNotNull(minted, "a legacy fief must be given an id rather than left without one");
        assertTrue(firstBoot.hasMintedId(), "the mint must be reported so the file gets rewritten");

        Fief secondBoot = new Fief(firstBoot.save(), null, LOGGER);

        assertEquals(minted, secondBoot.getId(), "the id must not change on the boot after the mint");
        assertFalse(secondBoot.hasMintedId(), "the second boot has nothing to migrate");

        // And a third, because "idempotent" means every boot after the first, not just the second.
        assertEquals(minted, new Fief(secondBoot.save(), null, LOGGER).getId());
    }

    @Test
    @DisplayName("an unreadable id is replaced rather than thrown out of, so the fief is not lost")
    void anUnreadableIdIsReplaced() {
        // Throwing here would send the row to StorageService's quarantine, which takes the fief out of
        // play entirely: its holder loses the fief and every claim under it. Minting costs only what
        // was keyed on the broken id, and it is printed rather than logged so it is not invisible.
        Map<String, String> mangled = fief("Ashford", UUID.randomUUID()).save();
        mangled.put("id", "\"not-a-uuid\"");

        Fief loaded = new Fief(mangled, null, LOGGER);

        assertNotNull(loaded.getId());
        assertTrue(loaded.hasMintedId());
        assertEquals("Ashford", loaded.getName(), "the fief itself must survive a broken id");
    }

    @Test
    @DisplayName("renaming a fief does not change its id, which is what lets its arms survive")
    void renamingDoesNotChangeTheId() {
        // Step 10's acceptance criterion, from this side of it: set a fief's arms, rename the fief, the
        // arms survive. They survive because the armorial keys on this and /fi rename does not touch it.
        Fief ashford = fief("Ashford Mill", UUID.randomUUID());
        UUID id = ashford.getId();

        ashford.setName("Ashford Keep");

        assertEquals(id, ashford.getId());
        assertEquals(id, new Fief(ashford.save(), null, LOGGER).getId(),
                "the rename must not be undone by a save and load either");
    }

    @Test
    @DisplayName("isSameFief follows the id, so it holds across a rename and a regrant")
    void isSameFiefFollowsTheId() {
        // It used to compare holder, name and faction: three fields that all change while the fief
        // stays the same fief. A renamed fief compared false against its own saved copy.
        Fief ashford = fief("Ashford Mill", UUID.randomUUID());
        Fief sameFief = new Fief(ashford.save(), null, LOGGER);
        ashford.setName("Ashford Keep");
        ashford.setOwnerUUID(UUID.randomUUID());

        assertTrue(ashford.isSameFief(sameFief));
        assertTrue(sameFief.isSameFief(ashford));
        assertFalse(ashford.isSameFief(fief("Ashford Mill", UUID.randomUUID())),
                "two fiefs that agree on every other field are still two fiefs");
    }
}
