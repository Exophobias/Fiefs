package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiefInteractionPolicyTest {
    @Test void sameNativeDecisionRechecksChangedMembershipProtectionAndEmbassyBoundary() {
        var data = new PersistentData(null);
        var chunks = new ChunkService(data, null, null);
        var factions = mock(MedievalFactionsApi.class);
        var world = mock(World.class); when(world.getName()).thenReturn("world");
        UUID worldId = UUID.randomUUID(); when(world.getUID()).thenReturn(worldId);
        var chunk = mock(Chunk.class); when(chunk.getWorld()).thenReturn(world);
        var block = mock(Block.class); when(block.getWorld()).thenReturn(world); when(block.getChunk()).thenReturn(chunk);
        var player = mock(Player.class); UUID actor = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(actor); when(player.getWorld()).thenReturn(world); when(player.isOnline()).thenReturn(true);
        var logger = new Logger(null);
        var home = new Fief(null, "home", actor, "realm", logger);
        var other = new Fief(null, "other", UUID.randomUUID(), "realm", logger);
        data.addFief(home); data.addFief(other);
        var claim = new ClaimedChunk(chunk, "realm", "home"); data.addChunk(claim);
        var policy = new FiefInteractionPolicy(chunks, data, factions, logger);
        assertTrue(policy.canInteractWithBlock(player, block));
        data.removeChunk(claim); claim.setFief("other"); data.addChunk(claim);
        assertFalse(policy.canInteractWithBlock(player, block));
        home.getFlags().setFlag("claimedLandProtected", "false", player);
        assertTrue(policy.canInteractWithBlock(player, block));
        when(factions.isEmbassyReservedAt(worldId, 0, 0)).thenReturn(true);
        assertFalse(policy.canInteractWithBlock(player, block));
        when(player.hasPermission("mf.bypass")).thenReturn(true);
        assertTrue(policy.canInteractWithBlock(player, block));
    }

    @Test void publicReadFailsClosedForLegacyOffThreadUnavailableAndLostReadiness() {
        var data = new PersistentData(null); var player = mock(Player.class); var block = mock(Block.class);
        when(player.isOnline()).thenReturn(true);
        AtomicBoolean ready = new AtomicBoolean(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertFalse(new FiefsAPI(data).canInteractWithBlock(player, block));
            var api = new FiefsAPI(data, null, null, ready::get, (p, b) -> true);
            assertTrue(api.canInteractWithBlock(player, block));
            ready.set(false); assertFalse(api.canInteractWithBlock(player, block));
            ready.set(true); bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertFalse(api.canInteractWithBlock(player, block));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var interrupted = new FiefsAPI(data, null, null, ready::get, (p, b) -> { ready.set(false); return true; });
            assertFalse(interrupted.canInteractWithBlock(player, block));
            ready.set(true);
            var broken = new FiefsAPI(data, null, null, ready::get, (p, b) -> { throw new LinkageError("old provider"); });
            assertFalse(broken.canInteractWithBlock(player, block));
        }
    }
}
