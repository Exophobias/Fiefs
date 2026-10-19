package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.github.exophobias.patriamwelcome.api.*;
import dansplugins.fiefs.welcome.WelcomeFeatureProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;

class WelcomeFeatureProviderTest {
    private ServerMock server;
    @AfterEach void close() { MockBukkit.unmock(); }
    private static final class View implements HubFeatureView {
        HubFeature feature; String unavailable;
        public void show(HubFeature value) { feature = value; }
        public void unavailable(String message) { unavailable = message; }
        public boolean active() { return true; }
        public void back() { }
        public void home() { }
    }
    @Test void currentSelfHoldingAndActualAllowanceRecheckReadPermission() {
        server = MockBukkit.mock();
        var api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);
        var player = server.addPlayer("Holder");
        api.createFaction("realm-1", "Realm", player.getUniqueId());
        api.setPower(player.getUniqueId(), 12);
        Fiefs plugin = MockBukkit.load(Fiefs.class);
        assertTrue(player.performCommand("fi create Mill"));
        var provider = new WelcomeFeatureProvider(plugin);
        View current = new View(); provider.open(player, current);
        assertNotNull(current.feature);
        assertTrue(current.feature.rows().stream().anyMatch(row -> row.label().equals("Holding") && row.value().equals("Mill")));
        assertTrue(current.feature.rows().stream().anyMatch(row -> row.label().equals("Land allowance") && row.value().equals("12")));
        assertTrue(current.feature.help().stream().noneMatch(entry -> entry.staff()));
        player.addAttachment(plugin, "fiefs.info", false);
        View revoked = new View(); provider.open(player, revoked);
        assertNotNull(revoked.feature);
        assertFalse(revoked.feature.rows().stream().anyMatch(row -> row.value().equals("Mill")));
        assertFalse(revoked.feature.actions().stream().anyMatch(action -> action.command().equals("fi info")));
        assertFalse(revoked.feature.help().stream().anyMatch(entry -> entry.permission().equals("fiefs.info")));
    }
}
