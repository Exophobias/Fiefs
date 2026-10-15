package dansplugins.fiefs.listeners;

import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whose claimedLandProtected flag decides: the fief that holds the land, never the acting player's. */
class LandProtectionTest {
    /** {@code getFlag(...)} always logs, and the real logger needs a running plugin (as in FiefFlagsTest). */
    private static final Logger NULL_LOGGER = new Logger(null) {
        @Override
        public void log(String message) {
        }
    };

    private static Fief fief(String name) {
        return new Fief(null, name, UUID.randomUUID(), "faction-1", NULL_LOGGER);
    }

    private static void unprotect(Fief fief) {
        fief.getFlags().setFlag("claimedLandProtected", "false", BukkitTestDoubles.messageCapturingPlayer(new ArrayList<>()));
    }

    @Test
    void protectedLandRefusesOtherFiefsAndPlayersInNoFief() {
        Fief north = fief("North");
        assertTrue(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, fief("South")));
        assertTrue(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, null));
        assertFalse(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, north));
    }

    @Test
    void anotherFiefTurningItsOwnFlagOffDoesNotOpenThisLand() {
        Fief north = fief("North");
        Fief south = fief("South");
        unprotect(south);
        assertTrue(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, south));
    }

    @Test
    void theHoldersFlagOffOpensItsLandToEveryone() {
        Fief north = fief("North");
        unprotect(north);
        assertFalse(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, fief("South")));
        assertFalse(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(north, null));
    }

    @Test
    void orphanedFiefClaimsFailClosed() {
        assertTrue(dansplugins.fiefs.services.FiefInteractionPolicy.refuses(null, fief("South")));
    }
}
