package dansplugins.fiefs.objects;

import dansplugins.fiefs.utils.Logger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link FiefFlags}'s flag storage and lookup logic.
 * {@code setFlag(...)} and {@code sendFlagList(...)} are not exercised here since they
 * require a live Bukkit {@code Player} to send messages to.
 */
class FiefFlagsTest {

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

    private FiefFlags newFlags() {
        return new FiefFlags(new NoOpLogger());
    }

    @Test
    void constructor_doesNotPopulateValuesUntilInitialized() {
        FiefFlags flags = newFlags();

        assertEquals(0, flags.getNumFlags());
    }

    @Test
    void initializeFlagValues_setsClaimedLandProtectedDefaultTrue() {
        FiefFlags flags = newFlags();

        flags.initializeFlagValues();

        assertEquals(true, flags.getBooleanValues().get("claimedLandProtected"));
    }

    @Test
    void loadMissingFlagsIfNecessary_addsDefaultWhenAbsent() {
        FiefFlags flags = newFlags();

        flags.loadMissingFlagsIfNecessary();

        assertEquals(true, flags.getBooleanValues().get("claimedLandProtected"));
    }

    @Test
    void loadMissingFlagsIfNecessary_doesNotOverrideExistingValue() {
        FiefFlags flags = newFlags();
        flags.initializeFlagValues();
        flags.getBooleanValues().put("claimedLandProtected", false);

        flags.loadMissingFlagsIfNecessary();

        assertEquals(false, flags.getBooleanValues().get("claimedLandProtected"));
    }

    @Test
    void getFlag_returnsValueForKnownFlag() {
        FiefFlags flags = newFlags();
        flags.initializeFlagValues();

        assertEquals(true, flags.getFlag("claimedLandProtected"));
    }

    @Test
    void getFlag_returnsFalseForUnknownFlag() {
        FiefFlags flags = newFlags();

        assertEquals(false, flags.getFlag("notARealFlag"));
    }

    @Test
    void getFlag_returnsNullWhenKnownFlagHasNoStoredValue() {
        FiefFlags flags = newFlags();

        // "claimedLandProtected" is a declared flag name, but initializeFlagValues()/
        // loadMissingFlagsIfNecessary() haven't run, so no value map contains it yet.
        assertNull(flags.getFlag("claimedLandProtected"));
    }

    @Test
    void getNumFlags_reflectsBooleanValueCount() {
        FiefFlags flags = newFlags();
        assertEquals(0, flags.getNumFlags());

        flags.initializeFlagValues();

        assertEquals(1, flags.getNumFlags());
    }

    @Test
    void integerValues_roundTripThroughGetterAndSetter() {
        FiefFlags flags = newFlags();
        HashMap<String, Integer> values = new HashMap<>();
        values.put("someIntFlag", 42);

        flags.setIntegerValues(values);

        assertEquals(42, flags.getIntegerValues().get("someIntFlag"));
    }

    @Test
    void doubleValues_roundTripThroughGetterAndSetter() {
        FiefFlags flags = newFlags();
        HashMap<String, Double> values = new HashMap<>();
        values.put("someDoubleFlag", 3.14);

        flags.setDoubleValues(values);

        assertEquals(3.14, flags.getDoubleValues().get("someDoubleFlag"));
    }

    @Test
    void stringValues_roundTripThroughGetterAndSetter() {
        FiefFlags flags = newFlags();
        HashMap<String, String> values = new HashMap<>();
        values.put("someStringFlag", "hello");

        flags.setStringValues(values);

        assertEquals("hello", flags.getStringValues().get("someStringFlag"));
    }

    @Test
    void booleanValues_roundTripThroughGetterAndSetter() {
        FiefFlags flags = newFlags();
        HashMap<String, Boolean> values = new HashMap<>();
        values.put("someBooleanFlag", true);

        flags.setBooleanValues(values);

        assertTrue(flags.getBooleanValues().get("someBooleanFlag"));
        assertFalse(flags.getBooleanValues().containsKey("claimedLandProtected"));
    }
}
