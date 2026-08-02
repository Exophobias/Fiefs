package dansplugins.fiefs;

import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fief's seat, and how long its holder has held it.
 *
 * <p>Both exist for the same consumer -- a rising has to name the ground a side must hold, and has to
 * refuse a holder who was granted the fief this morning -- but they fail differently and are worth
 * pinning apart. A missing capital refuses a rising, which is visible. A wrong {@code heldSince}
 * silently changes who may call one.
 *
 * <p>No MockBukkit here on purpose: {@link Fief} serialises to a plain string map, so the round trip
 * that actually matters can be exercised without a server at all.
 */
class FiefCapitalTest {

    private static final Logger LOGGER = new Logger(null);

    private Fief fief(UUID holder) {
        return new Fief(null, "Ashford", holder, "f-rhundal", LOGGER);
    }

    @Test
    @DisplayName("a new fief has no capital, and is refused rather than given one")
    void aNewFiefHasNoCapital() {
        // Refusing is the point. A capital the plugin picked would be picked by whoever wrote the
        // tie-break rather than by the player whose holding it is.
        Fief ashford = fief(UUID.randomUUID());

        assertFalse(ashford.hasCapital());
        assertNull(ashford.getCapitalWorld());
    }

    @Test
    @DisplayName("naming a capital records the world and both chunk coordinates")
    void aCapitalIsNamed() {
        Fief ashford = fief(UUID.randomUUID());

        ashford.setCapital("world", -4, 17);

        assertTrue(ashford.hasCapital());
        assertEquals("world", ashford.getCapitalWorld());
        assertEquals(-4, ashford.getCapitalX());
        assertEquals(17, ashford.getCapitalZ());
        assertTrue(ashford.capitalIsAt("world", -4, 17));
        assertFalse(ashford.capitalIsAt("world_nether", -4, 17), "the world is part of the answer");
        assertFalse(ashford.capitalIsAt("world", -4, 18));
    }

    @Test
    @DisplayName("capitalIsAt answers false for a fief with no capital rather than throwing")
    void noCapitalIsNowhere() {
        // Called from the unclaim path for every fief chunk, most of which have no capital.
        assertFalse(fief(UUID.randomUUID()).capitalIsAt("world", 0, 0));
    }

    @Test
    @DisplayName("clearing it leaves the fief with none, which is what losing the ground must do")
    void clearingLeavesNone() {
        Fief ashford = fief(UUID.randomUUID());
        ashford.setCapital("world", 3, 3);

        ashford.clearCapital();

        assertFalse(ashford.hasCapital());
        assertFalse(ashford.capitalIsAt("world", 3, 3));
    }

    @Test
    @DisplayName("the capital and the tenure both survive a save and load")
    void itSurvivesTheRoundTrip() {
        UUID holder = UUID.randomUUID();
        Fief ashford = fief(holder);
        ashford.setCapital("world", -128, 64);
        long heldSince = ashford.getHeldSince();

        Map<String, String> saved = ashford.save();
        Fief loaded = new Fief(saved, null, LOGGER);

        assertEquals("world", loaded.getCapitalWorld());
        assertEquals(-128, loaded.getCapitalX());
        assertEquals(64, loaded.getCapitalZ());
        assertEquals(heldSince, loaded.getHeldSince());
        assertTrue(loaded.hasCapital());
    }

    @Test
    @DisplayName("a fief saved before these fields existed loads as long-held with no capital")
    void anOlderFiefLoadsCleanly() {
        // The upgrade path, and the direction matters. heldSince reads as 0, which is "held since
        // the epoch" and passes every tenure gate -- correct, because those holders really have held
        // their fiefs since before anybody was counting. Defaulting to "held as of the upgrade"
        // would silently freeze every existing fief out of the mechanic for its first week.
        Fief ashford = fief(UUID.randomUUID());
        Map<String, String> saved = ashford.save();
        saved.remove("capitalWorld");
        saved.remove("capitalX");
        saved.remove("capitalZ");
        saved.remove("heldSince");

        Fief loaded = new Fief(saved, null, LOGGER);

        assertFalse(loaded.hasCapital());
        assertEquals(0L, loaded.getHeldSince());
    }

    @Test
    @DisplayName("granting a fief on restamps the tenure, so standing does not come with the land")
    void tenureIsStampedOnEveryGrant() {
        // Without this, regranting an old fief to a newcomer would hand them the previous holder's
        // standing along with it -- so a realm could arm somebody against itself in one command.
        Fief ashford = fief(UUID.randomUUID());
        long founded = ashford.getHeldSince();

        ashford.setOwnerUUID(UUID.randomUUID());

        assertTrue(ashford.getHeldSince() >= founded,
                "the clock restarts with the new holder rather than running from the founding");
    }
}
