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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MembersCommand}: the console is told it can't list members rather than met with
 * silence (#240), whether or not it names a fief.
 */
class MembersCommandTest {

    private static final String ONLY_PLAYERS = "Only players can use this command.";

    private final List<String> messages = new ArrayList<>();
    private final List<Player> factionLookups = new ArrayList<>();

    private final MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(null) {
        @Override
        public FactionView getFactionForPlayer(Player player) {
            factionLookups.add(player);
            return null;
        }
    };

    private final MembersCommand membersCommand = new MembersCommand(integrator, new PersistentData(null));
    private final CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        boolean result = membersCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList(ONLY_PLAYERS), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_fromTheConsoleNamingAFief_saysOnlyPlayersCanUseIt() {
        boolean result = membersCommand.execute(console, new String[]{"North"});

        assertFalse(result);
        assertEquals(Collections.singletonList(ONLY_PLAYERS), messages);
        assertTrue(factionLookups.isEmpty());
    }
}
