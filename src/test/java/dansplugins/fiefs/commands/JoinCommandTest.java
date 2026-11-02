package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.MedievalFactionsFactions;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.ChatColor;
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
 * Tests for {@link JoinCommand}: how the fief name is read — bare or in double quotes, one word
 * or several (#237) — and who may join.
 */
class JoinCommandTest {

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
    private final JoinCommand joinCommand = new JoinCommand(integrator, persistentData, org.mockito.Mockito.mock(dansplugins.fiefs.services.SuccessionService.class));

    private final UUID playerId = UUID.randomUUID();
    private final Player player = BukkitTestDoubles.playerWithId(playerId, messages);

    private Fief addFief(String name, String factionId) {
        Fief fief = new Fief(null, name, UUID.randomUUID(), factionId, NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    @Test
    void execute_withABareName_joinsTheFief() {
        Fief fief = addFief("Millbrook", "faction-1");
        fief.invitePlayer(playerId);

        boolean result = joinCommand.execute(player, new String[]{"Millbrook"});

        assertTrue(result);
        assertEquals(Collections.singletonList("Joined."), messages);
        assertTrue(fief.isMember(playerId));
    }

    @Test
    void execute_withAQuotedName_joinsTheFief() {
        Fief fief = addFief("Millbrook", "faction-1");
        fief.invitePlayer(playerId);

        boolean result = joinCommand.execute(player, new String[]{"\"Millbrook\""});

        assertTrue(result);
        assertEquals(Collections.singletonList("Joined."), messages);
        assertTrue(fief.isMember(playerId));
    }

    @Test
    void execute_withAQuotedNameSpanningSeveralArguments_joinsTheFief() {
        Fief fief = addFief("South Reach", "faction-1");
        fief.invitePlayer(playerId);

        boolean result = joinCommand.execute(player, new String[]{"\"South", "Reach\""});

        assertTrue(result);
        assertTrue(fief.isMember(playerId));
    }

    @Test
    void execute_withABareNameSpanningSeveralArguments_joinsTheFief() {
        Fief fief = addFief("South Reach", "faction-1");
        fief.invitePlayer(playerId);

        boolean result = joinCommand.execute(player, new String[]{"South", "Reach"});

        assertTrue(result);
        assertTrue(fief.isMember(playerId));
    }

    @Test
    void execute_forAFiefThatDoesNotExist_saysItWasNotFound() {
        boolean result = joinCommand.execute(player, new String[]{"\"Nowhere\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("That fief wasn't found."), messages);
    }

    @Test
    void execute_withoutAnInvite_refuses() {
        Fief fief = addFief("Millbrook", "faction-1");

        boolean result = joinCommand.execute(player, new String[]{"\"Millbrook\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("You are not invited to this fief."), messages);
        assertFalse(fief.isMember(playerId));
    }

    @Test
    void execute_forAFiefInAnotherFaction_refuses() {
        Fief fief = addFief("Millbrook", "faction-2");
        fief.invitePlayer(playerId);

        boolean result = joinCommand.execute(player, new String[]{"Millbrook"});

        assertFalse(result);
        assertEquals(Collections.singletonList("That fief isn't in your faction."), messages);
        assertFalse(fief.isMember(playerId));
    }
}
