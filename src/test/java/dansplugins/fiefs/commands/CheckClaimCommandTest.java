package dansplugins.fiefs.commands;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link CheckClaimCommand}.
 *
 * <p>The branch where the chunk is claimed by a fief that is still registered is not exercised
 * here: reporting it calls {@code PersistentData.getFactionNameOfFief(...)}, which reaches
 * through the Medieval Factions integrator. The full MockBukkit command tier covers that path;
 * these focused upstream cases run with a null integrator, which the
 * code paths below never dereference.
 */
class CheckClaimCommandTest {

    private final PersistentData persistentData = new PersistentData(null);
    private final ChunkService chunkService = new ChunkService(persistentData, null);
    private final CheckClaimCommand checkClaimCommand = new CheckClaimCommand(persistentData, chunkService);
    private final List<String> messages = new ArrayList<>();

    private String lastMessage() {
        return messages.get(messages.size() - 1);
    }

    @Test
    void execute_refusesANonPlayerSender() {
        CommandSender console = BukkitTestDoubles.messageCapturingConsole(messages);

        boolean result = checkClaimCommand.execute(console);

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(lastMessage().contains("Only players can use this command."));
    }

    @Test
    void execute_reportsAnUnclaimedChunk() {
        Player player = BukkitTestDoubles.playerInChunk(BukkitTestDoubles.chunk("world", 1, 2), messages);

        boolean result = checkClaimCommand.execute(player);

        assertTrue(result);
        assertTrue(lastMessage().contains("This land is currently not claimed by a fief."));
    }

    @Test
    void execute_reportsAChunkClaimedElsewhereInTheWorldAsUnclaimed() {
        persistentData.addChunk(new ClaimedChunk(BukkitTestDoubles.chunk("world", 3, 4), "faction-1", "Testopia"));
        Player player = BukkitTestDoubles.playerInChunk(BukkitTestDoubles.chunk("world", 1, 2), messages);

        boolean result = checkClaimCommand.execute(player);

        assertTrue(result);
        assertTrue(lastMessage().contains("This land is currently not claimed by a fief."));
    }

    @Test
    void execute_namesTheClaimingFiefWhenThatFiefIsNoLongerRegistered() {
        Chunk chunk = BukkitTestDoubles.chunk("world", 1, 2);
        persistentData.addChunk(new ClaimedChunk(chunk, "faction-1", "Testopia"));
        Player player = BukkitTestDoubles.playerInChunk(chunk, messages);

        boolean result = checkClaimCommand.execute(player);

        assertTrue(result);
        assertTrue(lastMessage().contains("This land is claimed by Testopia."));
    }

    @Test
    void executeWithArguments_ignoresThemAndBehavesAsTheSenderOnlyOverload() {
        Player player = BukkitTestDoubles.playerInChunk(BukkitTestDoubles.chunk("world", 1, 2), messages);

        boolean result = checkClaimCommand.execute(player, new String[]{"unexpected"});

        assertTrue(result);
        assertTrue(lastMessage().contains("This land is currently not claimed by a fief."));
    }
}
