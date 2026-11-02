package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InviteCommand}'s sender-only overload. Ponder sends a bare {@code /fi invite}
 * there, and it used to return {@code false} without a word, the same fault #207 fixed for
 * {@code /fi info}; it must take the same path as the two-argument overload with no arguments.
 *
 * <p>The integrator is replaced with one that refuses the faction lookup the way the real one
 * does for a player in no faction, so the tests can see whether that lookup was reached.
 */
class InviteCommandTest {

    private static final String NOT_IN_A_FACTION = "You must be in a faction to use this command.";

    private final List<String> messages = new ArrayList<>();
    private final List<Player> factionLookups = new ArrayList<>();

    private final MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(null) {
        @Override
        public FactionView getFactionForPlayer(Player player) {
            factionLookups.add(player);
            player.sendMessage(NOT_IN_A_FACTION);
            return null;
        }
    };

    private final InviteCommand inviteCommand = new InviteCommand(integrator, new PersistentData(null));

    @Test
    void execute_senderOnlyOverload_fromAPlayer_isNotSilent() {
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        boolean result = inviteCommand.execute(player);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(1, messages.size());
        assertEquals(NOT_IN_A_FACTION, messages.get(0));
    }

    @Test
    void execute_senderOnlyOverload_matchesTheNoArgumentOverload() {
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        inviteCommand.execute(player);
        List<String> bare = new ArrayList<>(messages);
        messages.clear();
        inviteCommand.execute(player, new String[0]);

        assertEquals(messages, bare);
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        assertFalse(inviteCommand.execute(console));
        assertFalse(inviteCommand.execute(console, new String[]{"Someone"}));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("Only players can use this command."));
        assertTrue(factionLookups.isEmpty());
    }
}
