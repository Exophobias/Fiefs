package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.google.gson.Gson;
import dansplugins.fiefs.externalapi.FiefHolderChangedEvent;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FiefHolderNotificationLifecycleTest {
    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void loadedHoldingsAreNotReannouncedAndDisableDetachesTheObserver() throws Exception {
        var server = MockBukkit.mock();
        var api = new FakeMedievalFactionsApi();
        var host = MockBukkit.createMockPlugin("MedievalFactions");
        server.getServicesManager().register(MedievalFactionsApi.class, api, host, ServicePriority.Normal);
        var player = server.addPlayer("NewHolder");
        api.createFaction("realm", "Realm", player.getUniqueId());
        List<FiefHolderChangedEvent> changes = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void changed(FiefHolderChangedEvent event) { changes.add(event); }
        }, host);
        Fief saved = new Fief(null, "Saved", UUID.randomUUID(), "realm", new Logger(null));
        var folder = PluginDataFolder.create();
        Files.writeString(folder.toPath().resolve("fiefs.json"), new Gson().toJson(List.of(saved.save())));

        Fiefs plugin = MockBukkit.load(Fiefs.class);
        PluginDataFolder.assertIsWhereThePluginLooked(folder, plugin);
        Fief loaded = plugin.getPersistentData().getFiefById(saved.getId());
        assertNotNull(loaded);
        assertEquals(saved.getOwnerUUID(), loaded.getOwnerUUID());
        assertTrue(changes.isEmpty(), "startup data is not a new grant");

        assertTrue(player.performCommand("fi create \"Fresh\""));
        assertEquals(1, changes.size(), "the observer must actually become active after load");
        server.getPluginManager().disablePlugin(plugin);
        changes.clear();
        assertTrue(plugin.getPersistentData().removeFief(loaded));
        assertTrue(changes.isEmpty(), "a disabled owner must not retain its publication callback");
    }
}
