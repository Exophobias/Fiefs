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
 * Tests for {@link InfoCommand}. Ponder sends a bare {@code /fi info} to the sender-only
 * overload, which used to return {@code false} and print nothing (#207); it must take the same
 * path as the two-argument overload with no arguments, which resolves the sender's own fief.
 *
 * <p>The integrator is replaced with one that refuses the faction lookup the way the real one
 * does for a player in no faction, so the tests can see whether that lookup was reached.
 */
class InfoCommandTest {

    private static final String NOT_IN_A_FACTION = ChatColor.RED + "You must be in a faction to use this command.";

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

    private final InfoCommand infoCommand = new InfoCommand(integrator, new PersistentData(null), null);

    @Test
    void execute_senderOnlyOverload_fromAPlayer_looksUpTheirOwnFief() {
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        boolean result = infoCommand.execute(player);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(1, messages.size());
        assertEquals(NOT_IN_A_FACTION, messages.get(0));
    }

    @Test
    void execute_senderOnlyOverload_matchesTheNoArgumentOverload() {
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        infoCommand.execute(player);
        List<String> bare = new ArrayList<>(messages);
        messages.clear();
        infoCommand.execute(player, new String[0]);

        assertEquals(messages, bare);
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        assertFalse(infoCommand.execute(console));
        assertFalse(infoCommand.execute(console, new String[]{"Somefief"}));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("Only players can use this command."));
        assertTrue(factionLookups.isEmpty());
    }
}
