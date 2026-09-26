package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.event.FactionDisbandedEvent;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import com.dansplugins.factionsystem.api.event.FactionUnclaimedChunkEvent;
import com.dansplugins.factionsystem.api.ClaimView;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefClaimStatus;
import dansplugins.fiefs.heraldry.HeraldryPresence;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.DeferredUnclaimStore;
import dansplugins.fiefs.services.StorageService;
import dansplugins.fiefs.services.SuccessionService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final DeferredUnclaimStore deferredUnclaims;
    private final StorageService storage;
    private final MedievalFactionsApi factions;
    private final CoverageGate coverage;
    private final Consumer<Throwable> failClosed;
    private final Function<UUID, World> worldLookup;

    /** Withhold positional absence until reconciliation and its disk barrier both complete. */
    public interface CoverageGate {
        boolean suspend();
        void restore(boolean wasReady);
    }

    public FactionEventListener(PersistentData persistentData, SuccessionService successionService,
                                DeferredUnclaimStore deferredUnclaims, StorageService storage,
                                MedievalFactionsApi factions, CoverageGate coverage,
                                Consumer<Throwable> failClosed) {
        this(persistentData, successionService, deferredUnclaims, storage, factions, coverage,
                failClosed, Bukkit::getWorld);
    }

    FactionEventListener(PersistentData persistentData, SuccessionService successionService,
                         DeferredUnclaimStore deferredUnclaims, StorageService storage,
                         MedievalFactionsApi factions, CoverageGate coverage,
                         Consumer<Throwable> failClosed, Function<UUID, World> worldLookup) {
        this.persistentData = persistentData;
        this.successionService = successionService;
        this.deferredUnclaims = deferredUnclaims;
        this.storage = storage;
        this.factions = Objects.requireNonNull(factions, "factions");
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.failClosed = failClosed;
        this.worldLookup = worldLookup;
    }

    // Faction renames need no handling: fiefs store the faction id, which is stable across renames.

    @EventHandler
    public void handle(FactionUnclaimedChunkEvent event) {
        World world = worldLookup.apply(event.getWorldId());
        boolean possibleFiefClaim = world != null
                ? persistentData.getClaimStatus(world.getName(), event.getChunkX(), event.getChunkZ())
                    != FiefClaimStatus.UNCLAIMED
                : persistentData.getClaimedChunks().stream().anyMatch(chunk ->
                    chunk.getX() == event.getChunkX() && chunk.getZ() == event.getChunkZ());
        if (!possibleFiefClaim) return;
        // The API event identifies the world by UUID. Fiefs' legacy claim rows store only its
        // name, so an unloaded world cannot be matched yet. Queue the exact position durably
        // before touching claim state; a restart must not restore protection left at an old camp.
        try {
            deferredUnclaims.record(new DeferredUnclaimStore.Position(event.getWorldId(),
                    event.getChunkX(), event.getChunkZ()));
        } catch (IOException failure) {
            throw unavailable(failure);
        }
        if (world != null) drain(world);
    }

    @EventHandler
    public void handle(WorldLoadEvent event) {
        drain(event.getWorld());
    }

    /** Also drains worlds which were already loaded before Fiefs registered its listener. */
    public void drainLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) drain(world);
    }

    private void drain(World world) {
        List<DeferredUnclaimStore.Position> due = deferredUnclaims.forWorld(world.getUID());
        // A crash can occur after MF commits an unclaim but before its deferred API event runs.
        // Reconcile every persisted row when its world is available, even without a queued event.
        List<ClaimedChunk> inWorld = persistentData.getClaimedChunks().stream()
                .filter(chunk -> world.getName().equals(chunk.getWorld())).toList();
        if (due.isEmpty() && inWorld.isEmpty()) return;
        boolean wasReady = coverage.suspend();
        try {
            boolean changed = false;
            for (DeferredUnclaimStore.Position position : due) {
                changed |= removeAt(world, position.x(), position.z());
            }
            Set<ClaimedChunk> live = new HashSet<>(persistentData.getClaimedChunks());
            for (ClaimedChunk chunk : inWorld) {
                if (!live.contains(chunk)) continue;
                ClaimView mfClaim = factions.getClaimAt(world, chunk.getX(), chunk.getZ());
                if (mfClaim == null || !mfClaim.getFactionId().getValue().equals(chunk.getFaction())) {
                    removeClaim(world, chunk);
                    changed = true;
                }
            }
            // Save the claim removal before acknowledging the event. If either write fails,
            // the durable queue remains and the same exact cleanup is safe to retry.
            if (changed) {
                storage.save();
                storage.verifyPersistence();
                storage.forceClaimCleanupDurably();
            }
            deferredUnclaims.acknowledge(due);
            coverage.restore(wasReady);
        } catch (IOException | RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private boolean removeAt(World world, int x, int z) {
        boolean removed = false;
        for (ClaimedChunk chunk : persistentData.getClaimedChunks()) {
            if (!chunk.isAt(world.getName(), x, z)) continue;
            removeClaim(world, chunk);
            removed = true;
        }
        return removed;
    }

    private void removeClaim(World world, ClaimedChunk chunk) {
        persistentData.removeChunk(chunk);
        // A capital must not survive on ground its faction has unclaimed or lost.
        Fief owner = persistentData.getFief(chunk.getFief());
        if (owner != null && owner.capitalIsAt(world.getName(), chunk.getX(), chunk.getZ())) {
            owner.clearCapital();
            persistentData.markDirty();
        }
    }

    private IllegalStateException unavailable(Throwable failure) {
        failClosed.accept(failure);
        return new IllegalStateException("Fiefs claim cleanup is unavailable; staff must restart after repair",
                failure);
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
        // Somebody leaving the FACTION stops being eligible to inherit even if a stale fief list still
        // holds their name, so this is one of the few changes that can move a fief's standing answer
        // without anybody in the fief doing anything at all. The fief is told only if it moved.
        successionService.refreshSuccession(fief);
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
            HeraldryPresence.publicationChanged(
                    fief.getId(), HeraldryPresence.PublicationChange.EXISTENCE);
        }
    }
}
