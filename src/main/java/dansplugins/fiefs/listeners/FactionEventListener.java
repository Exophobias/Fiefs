package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.event.FactionDisbandedEvent;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import com.dansplugins.factionsystem.api.event.FactionUnclaimedChunkEvent;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Keeps fief state consistent with the faction state underneath it.
 *
 * <p>Listens to Medieval Factions' <b>stable API</b> events, not its internal ones. Besides the
 * decoupling, that buys thread safety for free: the API bridges re-fire on the next server tick, so
 * these handlers always run on the main thread. MF's internal events do not — {@code /f leave},
 * {@code /f disband}, {@code /f unclaim} and {@code /f kick} all dispatch asynchronously, and these
 * handlers mutate the very lists that {@code ChunkService} iterates from {@code PlayerMoveEvent} and
 * from nine {@code InteractionListener} handlers. Binding to the internal events was a live data race.
 *
 * @author Daniel McCoy Stephenson
 */
public class FactionEventListener implements Listener {
    private final PersistentData persistentData;
    private final SuccessionService successionService;

    public FactionEventListener(PersistentData persistentData, SuccessionService successionService) {
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    // Faction renames need no handling: fiefs store the faction id, which is stable across renames.

    @EventHandler
    public void handle(FactionUnclaimedChunkEvent event) {
        World world = Bukkit.getWorld(event.getWorldId());
        if (world == null) {
            // The world is not loaded, so no fief claim in it can be matched by name.
            return;
        }

        ClaimedChunk toRemove = null;
        for (ClaimedChunk claimedChunk : persistentData.getClaimedChunks()) {
            if (claimedChunk.isAt(world.getName(), event.getChunkX(), event.getChunkZ())) {
                toRemove = claimedChunk;
                break;
            }
        }

        if (toRemove == null) {
            return;
        }
        persistentData.removeChunk(toRemove);

        // The capital has to go with the ground, and it did not. ChunkService clears it when a
        // holder types /fi unclaim, and its comment claimed that was "the only place a fief can lose
        // a chunk it chose" -- which was never true. Medieval Factions unclaiming the chunk
        // underneath, by /f unclaim, a disband, or a conquest, arrives here instead, and left a
        // capital pointing at ground the fief no longer holds.
        //
        // That is not cosmetic. A fief's capital is what a rising is won and lost on, so a phantom
        // one standing in wilderness hands the loyalist side an instant win: the chunk is not owned
        // by the rebels, so it reads as taken the moment anybody claims it -- or, under a rule that
        // asks who owns it, is already not theirs.
        Fief owner = persistentData.getFief(toRemove.getFief());
        if (owner != null && owner.capitalIsAt(world.getName(), event.getChunkX(), event.getChunkZ())) {
            owner.clearCapital();
            persistentData.markDirty();
        }
    }

    /**
     * Covers voluntary leaves and kicks alike — the API deliberately emits one event per departure,
     * where MF internally fires both a kick event and a leave event for a single kick.
     *
     * <p>Leaving the faction is a departure in the succession sense, so a holder who walks away from
     * the faction loses the fief to their heir, to its longest-standing member, or back to the faction
     * itself. It cannot stay with them: a fief is held from the faction, and they are no longer of it.
     */
    @EventHandler
    public void handle(FactionMemberLeftEvent event) {
        UUID playerId = event.getPlayerId();
        Fief fief = persistentData.getFief(playerId);
        if (fief == null) {
            return;
        }

        if (fief.isOwner(playerId)) {
            successionService.succeedFrom(fief, playerId);
            return;
        }

        fief.removeMember(playerId);
        persistentData.markDirty();
        // TODO: inform fief members that the player left the faction
    }

    @EventHandler
    public void handle(FactionDisbandedEvent event) {
        String factionId = event.getFaction().getValue();
        ArrayList<Fief> toRemove = new ArrayList<>();
        for (Fief fief : persistentData.getFiefs()) {
            if (fief.getFactionId().equals(factionId)) {
                toRemove.add(fief);
            }
        }
        for (Fief fief : toRemove) {
            // TODO: inform fief members that the faction has been disbanded

            persistentData.removeFief(fief);
        }
    }
}
