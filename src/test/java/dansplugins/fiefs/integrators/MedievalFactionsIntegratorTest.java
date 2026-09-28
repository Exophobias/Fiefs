package dansplugins.fiefs.integrators;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.FakeMedievalFactionsApi;
import dansplugins.fiefs.testsupport.FakeBukkitServer;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upstream #190's regression tests adapted to the stable ServicesManager API. A plugin name
 * alone is insufficient: MF publishes its service from onEnable(), after plugin construction.
 * Construction must neither resolve it nor log through the not-yet-ready config service.
 */
class MedievalFactionsIntegratorTest {
    private final List<String> logged = new ArrayList<>();
    private final Logger recordingLogger = new Logger(null) {
        @Override public void log(String message) { logged.add(message); }
    };
    private final Logger notYetUsableLogger = new Logger(null) {
        @Override public void log(String message) {
            throw new IllegalStateException("logged before the plugin was ready: " + message);
        }
    };

    @BeforeEach void installServer() { FakeBukkitServer.install(); }
    @AfterEach void removeServer() { FakeBukkitServer.uninstall(); }

    private FakeMedievalFactionsApi registerApi() {
        FakeMedievalFactionsApi api = new FakeMedievalFactionsApi();
        Bukkit.getServicesManager().register(MedievalFactionsApi.class, api,
                FakeBukkitServer.registerPlugin("MedievalFactions"), ServicePriority.Normal);
        return api;
    }

    @Test
    void constructorDoesNotResolveOrLogEvenWhenMedievalFactionsHasPublishedItsApi() {
        registerApi();
        MedievalFactionsIntegrator integrator = assertDoesNotThrow(
                () -> new MedievalFactionsIntegrator(notYetUsableLogger));
        assertNull(integrator.getAPI());
    }

    @Test
    void resolveFindsAndLogsThePublishedStableApi() {
        FakeMedievalFactionsApi api = registerApi();
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(recordingLogger);
        assertTrue(integrator.resolve());
        assertSame(api, integrator.getAPI());
        assertEquals(List.of("[DEBUG] Medieval Factions was found successfully!"), logged);
    }

    @Test
    void aPluginWithTheRightNameButNoStableApiRemainsUnavailable() {
        FakeBukkitServer.registerPlugin("MedievalFactions");
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(recordingLogger);
        assertFalse(integrator.resolve());
        assertNull(integrator.getAPI());
        assertEquals(List.of("[DEBUG] The Medieval Factions API was not available."), logged);
    }

    @Test
    void resolveCanBeRetriedAfterTheServiceIsPublished() {
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(recordingLogger);
        assertFalse(integrator.resolve());
        FakeMedievalFactionsApi api = registerApi();
        assertTrue(integrator.resolve());
        assertSame(api, integrator.getAPI());
    }
}
