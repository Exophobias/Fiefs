package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.MedievalFactionsFactions;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DescCommand}: the description is read from double quotes, and setting it is
 * confirmed rather than met with silence (#237).
 */
class DescCommandTest {

    private static final Logger NULL_LOGGER = new Logger(null);

    private final List<String> messages = new ArrayList<>();
    private final FactionView sendersFaction = MedievalFactionsFactions.faction("faction-1", "Alpha");

    private final MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(null) {
        @Override
        public FactionView getFactionForPlayer(Player player) {
            return sendersFaction;
        }
    };

    private final PersistentData persistentData = new PersistentData(null);
    private final DescCommand descCommand = new DescCommand(integrator, persistentData);

    private final UUID ownerId = UUID.randomUUID();
    private final Player owner = BukkitTestDoubles.playerWithId(ownerId, messages);

    private Fief addFief() {
        Fief fief = new Fief(null, "North", ownerId, "faction-1", NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = descCommand.execute(console, new String[]{"\"A quiet village\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
    }

    @Test
    void execute_withAQuotedDescription_setsItAndConfirms() {
        Fief fief = addFief();

        boolean result = descCommand.execute(owner, new String[]{"\"A", "quiet", "village\""});

        assertTrue(result);
        assertEquals("A quiet village", fief.getDescription());
        assertEquals(Collections.singletonList("Description set."), messages);
    }

    @Test
    void execute_withoutDoubleQuotes_asksForThemAndLeavesTheDescriptionAlone() {
        Fief fief = addFief();
        String before = fief.getDescription();

        boolean result = descCommand.execute(owner, new String[]{"A", "quiet", "village"});

        assertFalse(result);
        assertEquals(Collections.singletonList("New description must be between double quotes."), messages);
        assertEquals(before, fief.getDescription());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOne() {
        boolean result = descCommand.execute(owner, new String[]{"\"A quiet village\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
    }
}
