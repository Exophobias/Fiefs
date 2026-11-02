package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.MedievalFactionsFactions;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link UnclaimCommand}: the guards it applies, and the unclaim it
 * then performs on the chunk the sender is standing in.
 *
 * <p>Unlike claiming, unclaiming never consults Medieval Factions, so the real
 * {@link ChunkService} is used end to end. The integrator is replaced with one that answers the
 * faction lookup from a field and records whether it was reached.
 */
class UnclaimCommandTest {

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
    private final ChunkService chunkService = new ChunkService(persistentData, null, null);
    private final UnclaimCommand unclaimCommand = new UnclaimCommand(integrator, persistentData, chunkService);

    private final UUID playerId = UUID.randomUUID();
    private final Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
    private final Player player = BukkitTestDoubles.playerWithIdInChunk(playerId, chunk, messages);

    private Fief addFief(String name, UUID ownerId) {
        Fief fief = new Fief(null, name, ownerId, "faction-1", NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    private void claim(Chunk claimed, String fiefName) {
        persistentData.addChunk(new ClaimedChunk(claimed, "faction-1", fiefName));
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = unclaimCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookupAndKeepsTheClaim() {
        sendersFaction = null;
        addFief("North", playerId);
        claim(chunk, "North");

        boolean result = unclaimCommand.execute(player);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertEquals(1, persistentData.getNumChunks());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOneAndKeepsTheClaim() {
        addFief("North", UUID.randomUUID());
        claim(chunk, "North");

        boolean result = unclaimCommand.execute(player);

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
        assertEquals(1, persistentData.getNumChunks());
    }

    @Test
    void execute_onAChunkNoFiefHasClaimed_saysSo() {
        addFief("North", playerId);

        boolean result = unclaimCommand.execute(player);

        assertFalse(result);
        assertEquals(Collections.singletonList("That chunk is not claimed by a fief."), messages);
    }

    @Test
    void execute_onAChunkClaimedByAnotherFief_refusesAndKeepsTheClaim() {
        addFief("North", playerId);
        addFief("South", UUID.randomUUID());
        claim(chunk, "South");

        boolean result = unclaimCommand.execute(player);

        assertFalse(result);
        assertEquals(Collections.singletonList("That chunk doesn't belong to your fief."), messages);
        assertEquals("South", chunkService.getClaimedChunk(chunk).getFief());
    }

    @Test
    void execute_forTheOwner_unclaimsTheChunkTheyStandInAndNoOther() {
        addFief("North", playerId);
        claim(chunk, "North");
        Chunk elsewhere = BukkitTestDoubles.chunk("world", 3, 4);
        claim(elsewhere, "North");

        boolean result = unclaimCommand.execute(player);

        assertTrue(result);
        assertEquals(Collections.singletonList("Unclaimed."), messages);
        assertNull(chunkService.getClaimedChunk(chunk));
        assertNotNull(chunkService.getClaimedChunk(elsewhere));
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_unclaimsToo() {
        Fief fief = addFief("North", UUID.randomUUID());
        fief.addMember(playerId);
        claim(chunk, "North");

        boolean result = unclaimCommand.execute(player);

        assertTrue(result);
        assertNull(chunkService.getClaimedChunk(chunk));
    }

    @Test
    void executeWithArguments_ignoresThemAndBehavesAsTheSenderOnlyOverload() {
        addFief("North", playerId);
        claim(chunk, "North");

        boolean result = unclaimCommand.execute(player, new String[]{"unexpected"});

        assertTrue(result);
        assertNull(chunkService.getClaimedChunk(chunk));
    }
}
