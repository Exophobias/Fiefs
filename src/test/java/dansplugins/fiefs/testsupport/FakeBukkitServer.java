package dansplugins.fiefs.testsupport;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Upstream's offline-player registry adapted to Patriam's nonblocking cached lookup.
 * Enumerating every offline player or resolving an uncached name must fail these tests.
 * Each test installs a fresh server and calls {@link #uninstall()} afterward; the Bukkit
 * singleton must never outlive a test or replace the server used by the lifecycle suite.
 */
public final class FakeBukkitServer {
    private static RegistryServer server;

    private FakeBukkitServer() { }

    public static void install() { server = MockBukkit.mock(new RegistryServer()); }

    public static void uninstall() {
        MockBukkit.unmock();
        server = null;
    }

    public static Plugin registerPlugin(String name) { return MockBukkit.createMockPlugin(name); }

    public static UUID registerOfflinePlayer(String name) {
        UUID uuid = UUID.randomUUID();
        registerOfflinePlayer(uuid, name);
        return uuid;
    }

    public static void registerOfflinePlayer(UUID uuid, String name) {
        server.players.put(uuid, new OfflinePlayerMock(uuid, name));
    }

    private static final class RegistryServer extends ServerMock {
        private final Map<UUID, OfflinePlayer> players = new LinkedHashMap<>();

        @Override public OfflinePlayer[] getOfflinePlayers() {
            throw new AssertionError("Name resolution must use the already-loaded player cache");
        }

        @Override public OfflinePlayer getOfflinePlayerIfCached(String name) {
            return players.values().stream()
                    .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }

        @Override public OfflinePlayer getOfflinePlayer(String name) {
            throw new AssertionError("Name resolution must never attempt a blocking profile lookup");
        }

        @Override public OfflinePlayer getOfflinePlayer(UUID uuid) {
            return players.getOrDefault(uuid, new OfflinePlayerMock(uuid, null));
        }
    }
}
