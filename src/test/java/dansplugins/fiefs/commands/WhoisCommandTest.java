package dansplugins.fiefs.commands;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.FakeBukkitServer;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link WhoisCommand}. It is the one lookup command that reaches
 * neither the Medieval Factions integrator nor the player's own location, so every branch is
 * exercised here: the name it is given is resolved through Bukkit's offline-player cache, which
 * {@link FakeBukkitServer} supplies, and the fief that name belongs to comes from
 * {@link PersistentData}, which needs no integrator for the lookups involved.
 *
 * <p>The sender is the console throughout — the command accepts any {@code CommandSender}, and
 * using one that is not a player also pins that it does not quietly require one.
 */
class WhoisCommandTest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private final PersistentData persistentData = new PersistentData(null);
    private final WhoisCommand whoisCommand = new WhoisCommand(persistentData);
    private final List<String> messages = new ArrayList<>();
    private final CommandSender sender = BukkitTestDoubles.messageCapturingConsole(messages);

    @BeforeEach
    void installServer() {
        FakeBukkitServer.install();
    }

    @AfterEach
    void removeServer() { FakeBukkitServer.uninstall(); }

    private String lastMessage() {
        return messages.get(messages.size() - 1);
    }

    private Fief fiefNamed(String name, UUID owner) {
        return new Fief(null, name, owner, "faction-1", NULL_LOGGER);
    }

    @Test
    void execute_senderOnlyOverload_sendsTheUsageMessage() {
        boolean result = whoisCommand.execute(sender);

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(lastMessage().contains("Usage: /fi whois (playerName)"));
    }

    @Test
    void execute_withNoArguments_sendsTheUsageMessage() {
        boolean result = whoisCommand.execute(sender, new String[]{});

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(lastMessage().contains("Usage: /fi whois (playerName)"));
    }

    @Test
    void execute_withANameTheServerDoesNotKnow_reportsThePlayerWasNotFound() {
        boolean result = whoisCommand.execute(sender, new String[]{"Laughingspade"});

        assertFalse(result);
        assertTrue(lastMessage().contains("That player wasn't found."));
    }

    @Test
    void execute_withAKnownPlayerInNoFief_reportsThatTheyAreNotInOne() {
        FakeBukkitServer.registerOfflinePlayer("Laughingspade");

        boolean result = whoisCommand.execute(sender, new String[]{"Laughingspade"});

        assertTrue(result);
        assertTrue(lastMessage().contains("Laughingspade is not a member of a fief."));
    }

    @Test
    void execute_withAKnownPlayerInAFief_namesThatFief() {
        UUID target = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        persistentData.addFief(fiefNamed("Testopia", target));

        boolean result = whoisCommand.execute(sender, new String[]{"Laughingspade"});

        assertTrue(result);
        assertTrue(lastMessage().contains("Laughingspade is a member of Testopia."));
    }

    @Test
    void execute_reportsTheFiefAPlayerJoinedRatherThanOnlyTheOneTheyOwn() {
        UUID owner = FakeBukkitServer.registerOfflinePlayer("DanTheTechMan");
        UUID member = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        Fief fief = fiefNamed("Testopia", owner);
        fief.addMember(member);
        persistentData.addFief(fief);

        boolean result = whoisCommand.execute(sender, new String[]{"Laughingspade"});

        assertTrue(result);
        assertTrue(lastMessage().contains("Laughingspade is a member of Testopia."));
    }

    @Test
    void execute_resolvesTheNameCaseInsensitivelyButEchoesItAsTyped() {
        UUID target = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        persistentData.addFief(fiefNamed("Testopia", target));

        boolean result = whoisCommand.execute(sender, new String[]{"LAUGHINGSPADE"});

        assertTrue(result);
        assertTrue(lastMessage().contains("LAUGHINGSPADE is a member of Testopia."));
    }

    @Test
    void execute_withExtraArgumentsAfterTheName_looksUpOnlyTheFirst() {
        UUID target = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        persistentData.addFief(fiefNamed("Testopia", target));

        boolean result = whoisCommand.execute(sender, new String[]{"Laughingspade", "ignored"});

        assertTrue(result);
        assertTrue(lastMessage().contains("Laughingspade is a member of Testopia."));
    }
}
