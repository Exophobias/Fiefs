package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link LeaveCommand}: a member leaving drops only that member, while
 * the owner leaving delegates succession while preserving the fief and land.
 *
 * <p>The integrator is replaced with one that answers the faction lookup from a field, so a test
 * can put the sender in a faction or leave them out of one, and record whether the lookup was
 * reached.
 */
class LeaveCommandTest {

    private static final Logger NULL_LOGGER = new Logger(null);
    private static final String NOT_IN_A_FACTION = "You must be in a faction to use this command.";

    private final List<String> messages = new ArrayList<>();
    private final List<Player> factionLookups = new ArrayList<>();
    private FactionView sendersFaction = MedievalFactionsFactions.faction("faction-1", "Alpha");

    private final MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(null) {
        @Override
        public FactionView getFactionForPlayer(Player player) {
            factionLookups.add(player);
            if (sendersFaction == null) {
                player.sendMessage(NOT_IN_A_FACTION);
            }
            return sendersFaction;
        }
    };

    private final PersistentData persistentData = new PersistentData(null) {
        @Override public String getFactionNameOfFief(Fief fief) { return "Alpha"; }
    };
    private final dansplugins.fiefs.services.SuccessionService succession =
            org.mockito.Mockito.mock(dansplugins.fiefs.services.SuccessionService.class);
    private final LeaveCommand leaveCommand = new LeaveCommand(integrator, persistentData, succession);

    private final UUID ownerId = UUID.randomUUID();
    private final Player owner = BukkitTestDoubles.playerWithId(ownerId, messages);

    private Fief addFief(String name, UUID fiefOwnerId) {
        Fief fief = new Fief(null, name, fiefOwnerId, "faction-1", NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    private ClaimedChunk addClaim(String fiefName) {
        ClaimedChunk chunk = new ClaimedChunk();
        chunk.setFief(fiefName);
        persistentData.addChunk(chunk);
        return chunk;
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = leaveCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookupAndLeavesTheFiefAlone() {
        sendersFaction = null;
        Fief fief = addFief("North", ownerId);

        boolean result = leaveCommand.execute(owner);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertEquals(Collections.singletonList(fief), persistentData.getFiefs());
        assertTrue(fief.isMember(ownerId));
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOne() {
        boolean result = leaveCommand.execute(owner);

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_dropsOnlyThatMember() {
        Fief fief = addFief("North", ownerId);
        ClaimedChunk claim = addClaim("North");
        UUID memberId = UUID.randomUUID();
        fief.addMember(memberId);
        Player member = BukkitTestDoubles.playerWithId(memberId, messages);

        boolean result = leaveCommand.execute(member);

        assertTrue(result);
        assertEquals(Collections.singletonList("Left."), messages);
        assertFalse(fief.isMember(memberId));
        assertTrue(fief.isMember(ownerId));
        assertEquals(ownerId, fief.getOwnerUUID());
        assertEquals(Collections.singletonList(fief), persistentData.getFiefs());
        assertEquals(Collections.singletonList(claim), persistentData.getClaimedChunks());
    }

    @Test
    void execute_forTheOwner_delegatesSuccessionAndPreservesLand() {
        Fief fief = addFief("North", ownerId);
        ClaimedChunk claim = addClaim("North");
        Fief other = addFief("South", UUID.randomUUID());
        org.mockito.Mockito.when(succession.succeedFrom(fief, ownerId)).thenReturn(
                new dansplugins.fiefs.services.SuccessionService.Succession(
                        dansplugins.fiefs.services.SuccessionService.Outcome.REVERTED_TO_FACTION, null, null));
        assertTrue(leaveCommand.execute(owner));
        org.mockito.Mockito.verify(succession).succeedFrom(fief, ownerId);
        assertEquals(List.of("Left. North has reverted to Alpha."), messages);
        assertEquals(List.of(fief, other), persistentData.getFiefs());
        assertEquals(List.of(claim), persistentData.getClaimedChunks());
    }

    @Test
    void execute_withArguments_behavesAsWithout() {
        Fief fief = addFief("North", ownerId);
        UUID memberId = UUID.randomUUID();
        fief.addMember(memberId);
        Player member = BukkitTestDoubles.playerWithId(memberId, messages);

        boolean result = leaveCommand.execute(member, new String[]{"ignored"});

        assertTrue(result);
        assertEquals(Collections.singletonList("Left."), messages);
        assertFalse(fief.isMember(memberId));
        assertSame(fief, persistentData.getFief("North"));
    }
}
