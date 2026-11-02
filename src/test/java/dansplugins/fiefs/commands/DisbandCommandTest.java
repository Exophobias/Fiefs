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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link DisbandCommand}: only the owner may disband a fief, and
 * disbanding it releases its land without touching any other fief's.
 *
 * <p>The integrator is replaced with one that answers the faction lookup from a field, so a test
 * can put the sender in a faction or leave them out of one, and record whether the lookup was
 * reached.
 */
class DisbandCommandTest {

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

    private final PersistentData persistentData = new PersistentData(null);
    private final DisbandCommand disbandCommand = new DisbandCommand(integrator, persistentData);

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

        boolean result = disbandCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookupAndKeepsTheFief() {
        sendersFaction = null;
        Fief fief = addFief("North", ownerId);

        boolean result = disbandCommand.execute(owner);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertEquals(Collections.singletonList(fief), persistentData.getFiefs());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOne() {
        boolean result = disbandCommand.execute(owner);

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_refusesAndKeepsTheFiefAndItsLand() {
        Fief fief = addFief("North", UUID.randomUUID());
        ClaimedChunk claim = addClaim("North");
        UUID memberId = UUID.randomUUID();
        fief.addMember(memberId);
        Player member = BukkitTestDoubles.playerWithId(memberId, messages);

        boolean result = disbandCommand.execute(member);

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be the owner of your fief to disband it."), messages);
        assertEquals(Collections.singletonList(fief), persistentData.getFiefs());
        assertEquals(Collections.singletonList(claim), persistentData.getClaimedChunks());
        assertTrue(fief.isMember(memberId));
    }

    @Test
    void execute_forTheOwner_removesTheFiefAndReleasesItsLand() {
        Fief fief = addFief("North", ownerId);
        fief.addMember(UUID.randomUUID());
        addClaim("North");
        addClaim("North");

        boolean result = disbandCommand.execute(owner);

        assertTrue(result);
        assertEquals(Collections.singletonList("Fief disbanded."), messages);
        assertTrue(persistentData.getFiefs().isEmpty());
        assertTrue(persistentData.getClaimedChunks().isEmpty());
    }

    @Test
    void execute_forTheOwner_leavesOtherFiefsAndTheirLandInPlace() {
        addFief("North", ownerId);
        addClaim("North");
        Fief otherFief = addFief("South", UUID.randomUUID());
        ClaimedChunk otherClaim = addClaim("South");

        boolean result = disbandCommand.execute(owner);

        assertTrue(result);
        assertEquals(Collections.singletonList(otherFief), persistentData.getFiefs());
        assertEquals(Collections.singletonList(otherClaim), persistentData.getClaimedChunks());
    }

    @Test
    void execute_withArguments_behavesAsWithout() {
        addFief("North", ownerId);

        boolean result = disbandCommand.execute(owner, new String[]{"North"});

        assertTrue(result);
        assertEquals(Collections.singletonList("Fief disbanded."), messages);
        assertTrue(persistentData.getFiefs().isEmpty());
    }
}
