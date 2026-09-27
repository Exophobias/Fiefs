package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.event.EmbassyOfferAttemptEvent;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefClaimStatus;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.objects.ClaimedChunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Keeps a fief claim out of a proposed or accepted foreign embassy parcel. */
public final class EmbassyOfferBoundaryListener implements Listener {
    private final FiefsAPI fiefs;
    private final PersistentData claims;
    private final MedievalFactionsApi factions;
    private final Consumer<String> report;
    private final ConcurrentMap<UUID, String> worldNames = new ConcurrentHashMap<>();

    /** Called during enable on the main thread, before this listener is registered. */
    public EmbassyOfferBoundaryListener(FiefsAPI fiefs, PersistentData claims,
                                        MedievalFactionsApi factions, Collection<World> loadedWorlds,
                                        Consumer<String> report) {
        this.fiefs = Objects.requireNonNull(fiefs, "fiefs");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.factions = Objects.requireNonNull(factions, "factions");
        this.report = Objects.requireNonNull(report, "report");
        for (World world : loadedWorlds) {
            worldNames.put(world.getUID(), world.getName());
        }
    }

    /** Run after Fiefs has reconciled its saved rows with the current MF claims. */
    public void auditLoadedWorlds() {
        for (var world : worldNames.entrySet()) {
            auditWorld(world.getKey(), world.getValue());
        }
    }

    private void auditWorld(UUID worldId, String worldName) {
        for (ClaimedChunk claim : claims.getClaimedChunks()) {
            if (!worldName.equals(claim.getWorld())) continue;
            try {
                if (factions.isEmbassyReservedAt(worldId, claim.getX(), claim.getZ())) {
                    report.accept("Fief claim " + claim.getFief() + " at " + worldName + " "
                            + claim.getX() + "," + claim.getZ()
                            + " overlaps an embassy reservation. Preserving the fief claim; "
                            + "new fief claims there remain blocked until the overlap is resolved.");
                }
            } catch (RuntimeException | LinkageError unavailable) {
                report.accept("Could not audit embassy reservation at " + worldName + " "
                        + claim.getX() + "," + claim.getZ()
                        + "; new fief claims remain blocked while lookup is unavailable.");
            }
        }
    }

    /**
     * MF raises this cancellable pre-write event on the main thread alongside Fiefs claim writes.
     * The handler still uses only the prepared UUID-to-name map and Fiefs' nonloading positional
     * index. If either lookup cannot prove the parcel free, consent is withheld before MF writes.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmbassyOffer(EmbassyOfferAttemptEvent event) {
        String worldName = worldNames.get(event.getWorldId());
        if (worldName == null) {
            event.setCancelled(true);
            return;
        }
        try {
            if (fiefs.getClaimStatus(worldName,
                    event.getChunkX(), event.getChunkZ()) != FiefClaimStatus.UNCLAIMED) {
                event.setCancelled(true);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        worldNames.put(world.getUID(), world.getName());
        auditWorld(world.getUID(), world.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        worldNames.remove(event.getWorld().getUID());
    }
}
