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
 * Tests for {@link KickCommand}: the console is told it can't kick rather than met with silence
 * (#240).
 */
class KickCommandTest {

    private final List<String> messages = new ArrayList<>();
    private final List<Player> factionLookups = new ArrayList<>();

    private final MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(null) {
        @Override
        public FactionView getFactionForPlayer(Player player) {
            factionLookups.add(player);
            return null;
        }
    };

    private final KickCommand kickCommand = new KickCommand(integrator, new PersistentData(null), org.mockito.Mockito.mock(dansplugins.fiefs.services.SuccessionService.class));

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = kickCommand.execute(console, new String[]{"Steve"});

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }
}
