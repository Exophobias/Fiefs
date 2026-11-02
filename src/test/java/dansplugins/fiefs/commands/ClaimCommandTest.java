package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link ClaimCommand}: the guards it applies before a claim is
 * attempted, and what it hands to {@link ChunkService} once they pass.
 *
 * <p>The claim itself is not exercised here. {@code ChunkService.attemptToClaimChunk(...)} reaches
 * through the Medieval Factions integrator into the claim, player and faction services, none of
 * which can be stood up outside a running server, so the chunk service is replaced with one that
 * records each attempt and answers with a fixed result. The integrator is likewise replaced with
 * one that answers the faction lookup from a field and records whether it was reached.
 */
class ClaimCommandTest {

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

    private final List<Object[]> claimAttempts = new ArrayList<>();
    private boolean claimResult = true;

    private final ChunkService chunkService = new ChunkService(persistentData, null, null) {
        @Override
        public boolean attemptToClaimChunk(Chunk chunk, Fief fief, Player player) {
            claimAttempts.add(new Object[]{chunk, fief, player});
            return claimResult;
        }
    };

    private final ClaimCommand claimCommand = new ClaimCommand(integrator, persistentData, chunkService);

    private final UUID playerId = UUID.randomUUID();
    private final Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
    private final Player player = BukkitTestDoubles.playerWithIdInChunk(playerId, chunk, messages);

    private Fief addFief(String name, UUID ownerId) {
        Fief fief = new Fief(null, name, ownerId, "faction-1", NULL_LOGGER);
        persistentData.addFief(fief);
        return fief;
    }

    @Test
    void execute_fromTheConsole_saysOnlyPlayersCanUseIt() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = claimCommand.execute(console);

        assertFalse(result);
        assertEquals(Collections.singletonList("Only players can use this command."), messages);
        assertTrue(factionLookups.isEmpty());
        assertTrue(claimAttempts.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFaction_stopsAtTheFactionLookupWithoutAttemptingAClaim() {
        sendersFaction = null;
        addFief("North", playerId);

        boolean result = claimCommand.execute(player);

        assertFalse(result);
        assertEquals(1, factionLookups.size());
        assertEquals(Collections.singletonList(NOT_IN_A_FACTION), messages);
        assertTrue(claimAttempts.isEmpty());
    }

    @Test
    void execute_forAPlayerInNoFief_saysTheyMustBeInOneWithoutAttemptingAClaim() {
        addFief("North", UUID.randomUUID());

        boolean result = claimCommand.execute(player);

        assertFalse(result);
        assertEquals(Collections.singletonList("You must be in a fief to use this command."), messages);
        assertTrue(claimAttempts.isEmpty());
    }

    @Test
    void execute_forTheOwner_attemptsToClaimTheChunkTheyStandInForTheirFief() {
        addFief("South", UUID.randomUUID());
        Fief fief = addFief("North", playerId);

        boolean result = claimCommand.execute(player);

        assertTrue(result);
        assertEquals(1, claimAttempts.size());
        assertSame(chunk, claimAttempts.get(0)[0]);
        assertSame(fief, claimAttempts.get(0)[1]);
        assertSame(player, claimAttempts.get(0)[2]);
        assertTrue(messages.isEmpty());
    }

    @Test
    void execute_forAMemberWhoIsNotTheOwner_attemptsTheClaimToo() {
        Fief fief = addFief("North", UUID.randomUUID());
        fief.addMember(playerId);

        boolean result = claimCommand.execute(player);

        assertTrue(result);
        assertEquals(1, claimAttempts.size());
        assertSame(fief, claimAttempts.get(0)[1]);
    }

    @Test
    void execute_returnsWhatTheClaimAttemptReturns() {
        addFief("North", playerId);
        claimResult = false;

        boolean result = claimCommand.execute(player);

        assertFalse(result);
        assertEquals(1, claimAttempts.size());
    }

    @Test
    void executeWithArguments_ignoresThemAndBehavesAsTheSenderOnlyOverload() {
        Fief fief = addFief("North", playerId);

        boolean result = claimCommand.execute(player, new String[]{"unexpected"});

        assertTrue(result);
        assertEquals(1, claimAttempts.size());
        assertSame(fief, claimAttempts.get(0)[1]);
    }
}
