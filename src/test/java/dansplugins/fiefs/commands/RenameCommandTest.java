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
 * Characterization tests for {@link RenameCommand}: who may rename a fief, how the new name is
 * read, and that the fief's land follows it to the new name.
 *
 * <p>The integrator is replaced with one that answers the faction lookup from a field, so a test
 * can put the sender in a faction or leave them out of one, and record whether the lookup was
 * reached.
 */
class RenameCommandTest {

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
    private final RenameCommand renameCommand = new RenameCommand(integrator, persistentData);

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
    void execute_senderOnlyOverload_givesTheUsageLine() {
        boolean result = renameCommand.execute(owner);

        assertFalse(result);
        assertEquals(Collections.singletonList("Usage: /fiefs rename \"new name\""), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = renameCommand.execute(console, new String[]{"\"South\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookup() {
        sendersFaction = null;
        Fief fief = addFief("North", ownerId);

        boolean result = renameCommand.execute(owner, new String[]{"\"South\""});

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertEquals("North", fief.getName());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOne() {
        boolean result = renameCommand.execute(owner, new String[]{"\"South\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_refusesAndLeavesTheNameAlone() {
        Fief fief = addFief("North", UUID.randomUUID());
        UUID memberId = UUID.randomUUID();
        fief.addMember(memberId);
        Player member = BukkitTestDoubles.playerWithId(memberId, messages);

        boolean result = renameCommand.execute(member, new String[]{"\"South\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be the owner of your fief to rename it."), messages);
        assertEquals("North", fief.getName());
    }

    @Test
    void execute_withoutDoubleQuotes_asksForThem() {
        Fief fief = addFief("North", ownerId);

        boolean result = renameCommand.execute(owner, new String[]{"South"});

        assertFalse(result);
        assertEquals(Collections.singletonList(
                "You must put the new name of your fief in between double quotes."), messages);
        assertEquals("North", fief.getName());
    }

    @Test
    void execute_toANameAnotherFiefHolds_refuses() {
        Fief fief = addFief("North", ownerId);
        addFief("South", UUID.randomUUID());

        boolean result = renameCommand.execute(owner, new String[]{"\"south\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("That name is taken."), messages);
        assertEquals("North", fief.getName());
    }

    @Test
    void execute_toTheFiefsOwnNameInAnotherCase_isRefusedAsTaken() {
        // isNameTaken compares case-insensitively against every fief, the renamed one included
        Fief fief = addFief("North", ownerId);

        boolean result = renameCommand.execute(owner, new String[]{"\"NORTH\""});

        assertFalse(result);
        assertEquals(Collections.singletonList("That name is taken."), messages);
        assertEquals("North", fief.getName());
    }

    @Test
    void execute_byTheOwner_renamesTheFief() {
        Fief fief = addFief("North", ownerId);

        boolean result = renameCommand.execute(owner, new String[]{"\"South\""});

        assertTrue(result);
        assertEquals(Collections.singletonList("Fief renamed."), messages);
        assertEquals("South", fief.getName());
        assertSame(fief, persistentData.getFief("South"));
    }

    @Test
    void execute_withAQuotedNameSpanningSeveralArguments_usesTheWholeName() {
        Fief fief = addFief("North", ownerId);

        boolean result = renameCommand.execute(owner, new String[]{"\"South", "Reach\""});

        assertTrue(result);
        assertEquals("South Reach", fief.getName());
    }

    @Test
    void execute_byTheOwner_movesTheFiefsLandToTheNewNameAndNoOtherFiefsLand() {
        addFief("North", ownerId);
        addFief("East", UUID.randomUUID());
        ClaimedChunk ownLand = addClaim("North");
        ClaimedChunk otherLand = addClaim("East");

        renameCommand.execute(owner, new String[]{"\"South\""});

        assertEquals("South", ownLand.getFief());
        assertEquals("East", otherLand.getFief());
        assertEquals(1, persistentData.getNumChunksClaimedByFief(persistentData.getFief("South")));
    }
}
