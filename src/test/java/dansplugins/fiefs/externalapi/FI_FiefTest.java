package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Characterization tests for {@link FI_Fief}, the read-only view of a {@link Fief} handed to
 * other plugins. Each accessor delegates to the wrapped fief, so what is pinned here is that
 * the view stays in step with the fief behind it and what it reports for an unknown flag.
 *
 * {@code isMember(Player)} is not exercised: it needs a live Bukkit {@code Player}, and the
 * membership check it delegates to is covered by {@code FiefTest}.
 */
class FI_FiefTest {

    /**
     * {@link Logger#log(String)} dereferences its {@code Fiefs} plugin reference, which is
     * null outside a running server. {@code getFlag(...)} always logs, so tests need a logger
     * that no-ops instead of throwing.
     */
    private static final class NoOpLogger extends Logger {
        NoOpLogger() {
            super(null);
        }

        @Override
        public void log(String message) {
            // no-op: avoids dereferencing the null Fiefs plugin reference in tests
        }
    }

    private Fief newFief(String name, UUID owner) {
        return new Fief(null, name, owner, "faction-1", new NoOpLogger());
    }

    @Test
    void getName_returnsTheWrappedFiefsName() {
        FI_Fief view = new FI_Fief(newFief("Testopia", UUID.randomUUID()));

        assertEquals("Testopia", view.getName());
    }

    @Test
    void getOwner_returnsTheWrappedFiefsOwnerUUID() {
        UUID owner = UUID.randomUUID();

        FI_Fief view = new FI_Fief(newFief("Testopia", owner));

        assertEquals(owner, view.getOwner());
    }

    @Test
    void getFlag_returnsTheDefaultValueOfAKnownFlag() {
        FI_Fief view = new FI_Fief(newFief("Testopia", UUID.randomUUID()));

        assertEquals(true, view.getFlag("claimedLandProtected"));
    }

    /**
     * An unknown flag reports {@code false} rather than null, so a caller cannot tell it apart
     * from a boolean flag that is genuinely set to false.
     */
    @Test
    void getFlag_returnsFalseForAnUnknownFlag() {
        FI_Fief view = new FI_Fief(newFief("Testopia", UUID.randomUUID()));

        assertEquals(false, view.getFlag("noSuchFlag"));
    }

    /**
     * A known flag with no stored value reports null, unlike an unknown flag, which reports false.
     */
    @Test
    void getFlag_returnsNullWhenAKnownFlagHasNoStoredValue() {
        Fief fief = newFief("Testopia", UUID.randomUUID());
        fief.getFlags().getBooleanValues().remove("claimedLandProtected");

        assertNull(new FI_Fief(fief).getFlag("claimedLandProtected"));
    }

    /**
     * The view holds the fief itself rather than a copy of its state, so later changes to the
     * fief are visible through a view handed out beforehand.
     */
    @Test
    void accessors_reflectLaterChangesToTheWrappedFief() {
        Fief fief = newFief("Testopia", UUID.randomUUID());
        FI_Fief view = new FI_Fief(fief);
        UUID newOwner = UUID.randomUUID();

        fief.setName("Renamed");
        fief.setOwnerUUID(newOwner);
        fief.getFlags().getBooleanValues().put("claimedLandProtected", false);

        assertEquals("Renamed", view.getName());
        assertEquals(newOwner, view.getOwner());
        assertEquals(false, view.getFlag("claimedLandProtected"));
    }

    @Test
    void getFlag_readsTheFlagStateOfTheWrappedFiefRatherThanASharedDefault() {
        Fief protectedFief = newFief("Testopia", UUID.randomUUID());
        Fief unprotectedFief = newFief("Otherton", UUID.randomUUID());
        unprotectedFief.getFlags().getBooleanValues().put("claimedLandProtected", false);

        assertEquals(true, new FI_Fief(protectedFief).getFlag("claimedLandProtected"));
        assertEquals(false, new FI_Fief(unprotectedFief).getFlag("claimedLandProtected"));
    }
}
