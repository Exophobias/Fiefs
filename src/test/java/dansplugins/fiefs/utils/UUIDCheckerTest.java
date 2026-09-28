package dansplugins.fiefs.utils;

import dansplugins.fiefs.testsupport.FakeBukkitServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Characterization tests for {@link UUIDChecker}, which resolves between player names and UUIDs
 * through online players and Bukkit's offline-player cache. The cache is supplied by
 * {@link FakeBukkitServer}, which rejects enumeration and uncached profile lookup, so
 * these tests pin how the lookup behaves for names the server has never seen and for entries
 * whose name it cannot resolve — the cases that reach players as "That player wasn't found."
 */
class UUIDCheckerTest {

    private final UUIDChecker uuidChecker = new UUIDChecker();

    @BeforeEach
    void installServer() {
        FakeBukkitServer.install();
    }

    @AfterEach
    void removeServer() { FakeBukkitServer.uninstall(); }

    @Test
    void findUUIDBasedOnPlayerName_prefersTheOnlinePlayerToACachedName() {
        var online = MockBukkit.getMock().addPlayer("Laughingspade");
        FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertEquals(online.getUniqueId(), uuidChecker.findUUIDBasedOnPlayerName("Laughingspade"));
    }

    @Test
    void findUUIDBasedOnPlayerName_returnsTheUuidOfTheMatchingOfflinePlayer() {
        UUID uuid = FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertEquals(uuid, uuidChecker.findUUIDBasedOnPlayerName("Laughingspade"));
    }

    @Test
    void findUUIDBasedOnPlayerName_matchesCaseInsensitively() {
        UUID uuid = FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertEquals(uuid, uuidChecker.findUUIDBasedOnPlayerName("LAUGHINGSPADE"));
    }

    @Test
    void findUUIDBasedOnPlayerName_returnsNullWhenNoOfflinePlayerHasThatName() {
        FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertNull(uuidChecker.findUUIDBasedOnPlayerName("DanTheTechMan"));
    }

    @Test
    void findUUIDBasedOnPlayerName_returnsNullWhenTheServerKnowsNoOfflinePlayers() {
        assertNull(uuidChecker.findUUIDBasedOnPlayerName("Laughingspade"));
    }

    @Test
    void findUUIDBasedOnPlayerName_skipsEntriesWithoutAResolvedName() {
        FakeBukkitServer.registerOfflinePlayer(null);
        UUID uuid = FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertEquals(uuid, uuidChecker.findUUIDBasedOnPlayerName("Laughingspade"));
    }

    @Test
    void findPlayerNameBasedOnUUID_returnsTheNameTheServerHasForThatUuid() {
        UUID uuid = FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        assertEquals("Laughingspade", uuidChecker.findPlayerNameBasedOnUUID(uuid));
    }

    @Test
    void findPlayerNameBasedOnUUID_fallsBackToTheUuidWhenTheServerHasNoNameForIt() {
        UUID uuid = UUID.randomUUID();

        assertEquals(uuid.toString(), uuidChecker.findPlayerNameBasedOnUUID(uuid));
    }

    @Test
    void findPlayerNameBasedOnUUID_fallsBackToTheUuidWhenTheKnownEntryHasNoName() {
        UUID uuid = UUID.randomUUID();
        FakeBukkitServer.registerOfflinePlayer(uuid, null);

        assertEquals(uuid.toString(), uuidChecker.findPlayerNameBasedOnUUID(uuid));
    }
}
