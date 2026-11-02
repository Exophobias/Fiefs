package dansplugins.fiefs.services;

import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Upstream setter cases against Patriam's atomic, allowlisted configuration writer. */
class ConfigServiceTest {
    @Test void mixedCaseBooleanUsesCanonicalKeyAndAtomicWriter() {
        Fiefs plugin = mock(Fiefs.class);
        when(plugin.updateConfigBoolean("debugMode", true)).thenReturn(true);
        List<String> messages = new ArrayList<>();
        new ConfigService(plugin).setConfigOption("DEBUGMODE", "TRUE", BukkitTestDoubles.messageCapturingConsole(messages));
        verify(plugin).updateConfigBoolean("debugMode", true);
        assertEquals(List.of("Boolean set."), messages);
    }
    @Test void invalidBooleanAndUnknownKeysCannotWrite() {
        Fiefs plugin = mock(Fiefs.class);
        List<String> messages = new ArrayList<>();
        ConfigService service = new ConfigService(plugin);
        for (String value : List.of("yes", "1", "", "false ")) {
            service.setConfigOption("limitLand", value, BukkitTestDoubles.messageCapturingConsole(messages));
        }
        for (String key : List.of("config-version", "version", "usage-reporting.enabled", "extension")) {
            service.setConfigOption(key, "true", BukkitTestDoubles.messageCapturingConsole(messages));
        }
        verifyNoInteractions(plugin);
        assertEquals(8, messages.size());
        assertTrue(messages.get(0).contains("true or false"));
    }
    @Test void failedAtomicWriteDoesNotReportSuccess() {
        Fiefs plugin = mock(Fiefs.class);
        List<String> messages = new ArrayList<>();
        new ConfigService(plugin).setConfigOption("limitLand", "false", BukkitTestDoubles.messageCapturingConsole(messages));
        verify(plugin).updateConfigBoolean("limitLand", false);
        assertEquals(List.of("Config update was blocked; the previous settings remain active."), messages);
    }
}
