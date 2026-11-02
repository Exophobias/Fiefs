package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.FakeBukkitServer;
import dansplugins.fiefs.testsupport.MedievalFactionsFactions;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link TransferCommand}: only the owner can hand a fief on, only to
 * someone already in it, and the hand-over changes the owner and nothing else.
 *
 * <p>The integrator is replaced with one that answers the faction lookup from a field, so a test
 * can put the sender in a faction or leave them out of one, and record whether the lookup was
 * reached. Target names are resolved through {@link FakeBukkitServer}'s offline-player cache,
 * which is what {@code UUIDChecker} reads.
 */
class TransferCommandTest {

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
    private final TransferCommand transferCommand = new TransferCommand(integrator, persistentData);

    private UUID ownerId;
    private Player owner;

    @BeforeEach
    void setUp() {
        FakeBukkitServer.install();
        ownerId = FakeBukkitServer.registerOfflinePlayer("DanTheTechMan");
        owner = BukkitTestDoubles.namedPlayer(ownerId, "DanTheTechMan", messages);
    }

    private Fief addFief(String name, UUID fiefOwnerId) {
        Fief fief = new Fief(null, name, fiefOwnerId, "faction-1", NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    @org.junit.jupiter.api.AfterEach
    void removeServer() { FakeBukkitServer.uninstall(); }

    @Test
    void execute_withNoArguments_givesTheUsageLineWithoutAnyLookup() {
        boolean result = transferCommand.execute(owner);

        assertFalse(result);
        assertEquals(Collections.singletonList("Usage: /fiefs transfer (playerName)"), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_withNoArguments_fromTheConsole_givesTheUsageLineToo() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = transferCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList("Usage: /fiefs transfer (playerName)"), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = transferCommand.execute(console, new String[]{"Laughingspade"});

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookupAndKeepsTheOwner() {
        sendersFaction = null;
        Fief fief = addFief("North", ownerId);
        UUID memberId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        fief.addMember(memberId);

        boolean result = transferCommand.execute(owner, new String[]{"Laughingspade"});

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertEquals(ownerId, fief.getOwnerUUID());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOne() {
        boolean result = transferCommand.execute(owner, new String[]{"Laughingspade"});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_refusesAndKeepsTheOwner() {
        Fief fief = addFief("North", ownerId);
        UUID memberId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        fief.addMember(memberId);
        Player member = BukkitTestDoubles.namedPlayer(memberId, "Laughingspade", messages);

        boolean result = transferCommand.execute(member, new String[]{"DanTheTechMan"});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be the owner of your fief to transfer it."), messages);
        assertEquals(ownerId, fief.getOwnerUUID());
    }

    @Test
    void execute_toTheOwnersOwnName_inAnyCase_refuses() {
        Fief fief = addFief("North", ownerId);

        boolean result = transferCommand.execute(owner, new String[]{"danthetechman"});

        assertFalse(result);
        assertEquals(Collections.singletonList("You can't transfer your fief to yourself."), messages);
        assertEquals(ownerId, fief.getOwnerUUID());
    }

    @Test
    void execute_toAnUnknownPlayer_saysThePlayerWasNotFound() {
        Fief fief = addFief("North", ownerId);

        boolean result = transferCommand.execute(owner, new String[]{"Nobody"});

        assertFalse(result);
        assertEquals(Collections.singletonList("That player wasn't found."), messages);
        assertEquals(ownerId, fief.getOwnerUUID());
    }

    @Test
    void execute_toAPlayerOutsideTheFief_refusesEvenWhenTheyOwnAnotherFief() {
        Fief fief = addFief("North", ownerId);
        UUID otherOwnerId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        Fief otherFief = addFief("South", otherOwnerId);

        boolean result = transferCommand.execute(owner, new String[]{"Laughingspade"});

        assertFalse(result);
        assertEquals(Collections.singletonList("That player is not in your fief."), messages);
        assertEquals(ownerId, fief.getOwnerUUID());
        assertEquals(otherOwnerId, otherFief.getOwnerUUID());
    }

    @Test
    void execute_toAMember_makesThemTheOwnerAndKeepsEveryoneInTheFief() {
        Fief fief = addFief("North", ownerId);
        UUID memberId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        fief.addMember(memberId);

        boolean result = transferCommand.execute(owner, new String[]{"Laughingspade"});

        assertTrue(result);
        assertEquals(Collections.singletonList("Transferred."), messages);
        assertEquals(memberId, fief.getOwnerUUID());
        assertEquals(Arrays.asList(ownerId, memberId), fief.getMembers());
        assertEquals(Collections.singletonList(fief), persistentData.getFiefs());
    }

    @Test
    void execute_toAMember_matchesTheirNameRegardlessOfCase() {
        Fief fief = addFief("North", ownerId);
        UUID memberId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        fief.addMember(memberId);

        boolean result = transferCommand.execute(owner, new String[]{"LAUGHINGSPADE"});

        assertTrue(result);
        assertEquals(memberId, fief.getOwnerUUID());
    }

    @Test
    void execute_afterATransfer_theFormerOwnerIsAnOrdinaryMember() {
        Fief fief = addFief("North", ownerId);
        UUID memberId = FakeBukkitServer.registerOfflinePlayer("Laughingspade");
        fief.addMember(memberId);
        transferCommand.execute(owner, new String[]{"Laughingspade"});
        messages.clear();

        boolean result = transferCommand.execute(owner, new String[]{"Laughingspade"});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be the owner of your fief to transfer it."), messages);
        assertEquals(memberId, fief.getOwnerUUID());
        assertTrue(fief.isMember(ownerId));
    }
}
