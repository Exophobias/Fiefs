package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.ApiResult;
import com.dansplugins.factionsystem.api.ClaimOverrideProvider;
import com.dansplugins.factionsystem.api.ClaimView;
import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionRoleView;
import com.dansplugins.factionsystem.api.FactionView;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An in-memory stand-in for Medieval Factions.
 *
 * <p>This is the payoff of binding Fiefs to the stable API rather than MF's internals: faking the
 * whole dependency is one class implementing eleven methods. Faking MF's internal service graph
 * ({@code MfFactionService}, {@code MfPlayerService}, {@code MfClaimService}, plus Kotlin value
 * classes) would not have been practical.
 */
public class FakeMedievalFactionsApi implements MedievalFactionsApi {

    private final Map<String, FakeFaction> factionsById = new HashMap<>();
    private final Map<UUID, String> factionIdByPlayer = new HashMap<>();
    private final Map<String, String> factionIdByChunkKey = new HashMap<>();
    private final Map<UUID, Double> powerByPlayer = new HashMap<>();

    // --- test setup helpers ---

    public FactionId createFaction(String id, String name, UUID... members) {
        FakeFaction faction = new FakeFaction(new FactionId(id), name);
        for (UUID member : members) {
            faction.memberIds.add(member);
            factionIdByPlayer.put(member, id);
        }
        factionsById.put(id, faction);
        return faction.id;
    }

    /** Marks a chunk as claimed by a faction, as MF would after /f claim. */
    public void setFactionClaim(Chunk chunk, FactionId faction) {
        factionIdByChunkKey.put(key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()), faction.getValue());
    }

    public void setPower(UUID playerId, double power) {
        powerByPlayer.put(playerId, power);
    }

    private static String key(UUID worldId, int x, int z) {
        return worldId + ":" + x + ":" + z;
    }

    // --- MedievalFactionsApi ---

    @Override
    public FactionView getFaction(@NotNull FactionId id) {
        return factionsById.get(id.getValue());
    }

    @Override
    public FactionView getFactionByName(@NotNull String name) {
        return factionsById.values().stream().filter(f -> f.name.equals(name)).findFirst().orElse(null);
    }

    @Override
    public FactionView getFactionByPlayer(@NotNull UUID playerId) {
        String id = factionIdByPlayer.get(playerId);
        return id == null ? null : factionsById.get(id);
    }

    @Override
    public FactionView getFactionAt(@NotNull Chunk chunk) {
        ClaimView claim = getClaimAt(chunk);
        return claim == null ? null : factionsById.get(claim.getFactionId().getValue());
    }

    @Override
    public ClaimView getClaimAt(@NotNull Chunk chunk) {
        String factionId = factionIdByChunkKey.get(key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        if (factionId == null) {
            return null;
        }
        return new FakeClaim(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ(), new FactionId(factionId));
    }

    /** The chunk-load-free overload, matching {@link #isClaimed}. */
    @Override
    public ClaimView getClaimAt(@NotNull World world, int chunkX, int chunkZ) {
        String factionId = factionIdByChunkKey.get(key(world.getUID(), chunkX, chunkZ));
        if (factionId == null) {
            return null;
        }
        return new FakeClaim(world.getUID(), chunkX, chunkZ, new FactionId(factionId));
    }

    @Override
    public boolean isClaimed(@NotNull World world, int chunkX, int chunkZ) {
        return factionIdByChunkKey.containsKey(key(world.getUID(), chunkX, chunkZ));
    }

    @Override
    public double getPower(@NotNull UUID playerId) {
        return powerByPlayer.getOrDefault(playerId, 0.0);
    }

    @Override
    public @NotNull ApiResult setHome(@NotNull FactionId faction, @NotNull Location location) {
        return ApiResult.success();
    }

    @Override
    public @NotNull ApiResult claim(@NotNull FactionId faction, @NotNull Chunk chunk) {
        setFactionClaim(chunk, faction);
        return ApiResult.success();
    }

    @Override
    public @NotNull ApiResult unclaim(@NotNull Chunk chunk) {
        factionIdByChunkKey.remove(key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        return ApiResult.success();
    }

    @Override
    public @NotNull ApiResult forcePeace(@NotNull FactionId faction, @NotNull FactionId otherFaction) {
        return ApiResult.success();
    }

    // Fiefs registers no claim-override provider; these exist to satisfy the interface. Recorded
    // rather than ignored so a test can assert on them if Fiefs ever does register one.
    private final List<ClaimOverrideProvider> claimOverrideProviders = new ArrayList<>();

    @Override
    public void registerClaimOverrideProvider(@NotNull ClaimOverrideProvider provider) {
        if (!claimOverrideProviders.contains(provider)) {
            claimOverrideProviders.add(provider);
        }
    }

    @Override
    public void unregisterClaimOverrideProvider(@NotNull ClaimOverrideProvider provider) {
        claimOverrideProviders.remove(provider);
    }

    public List<ClaimOverrideProvider> getClaimOverrideProviders() {
        return claimOverrideProviders;
    }

    // --- view types ---

    private static class FakeFaction implements FactionView {
        private final FactionId id;
        private final String name;
        private final List<UUID> memberIds = new ArrayList<>();

        FakeFaction(FactionId id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public @NotNull FactionId getId() { return id; }
        @Override public @NotNull String getName() { return name; }
        @Override public @NotNull String getDescription() { return ""; }
        @Override public @Nullable Location getHome() { return null; }
        @Override public @NotNull List<UUID> getMemberIds() { return memberIds; }
        @Override public int getClaimCount() { return 0; }
        @Override public @NotNull List<FactionId> getFactionsAtWarWith() { return new ArrayList<>(); }
        @Override public boolean isAtWarWith(@NotNull FactionId other) { return false; }
        @Override public @Nullable FactionRoleView roleOf(@NotNull UUID playerId) { return null; }
    }

    private record FakeClaim(UUID worldId, int chunkX, int chunkZ, FactionId factionId) implements ClaimView {
        @Override public @NotNull UUID getWorldId() { return worldId; }
        @Override public int getChunkX() { return chunkX; }
        @Override public int getChunkZ() { return chunkZ; }
        @Override public @NotNull FactionId getFactionId() { return factionId; }
    }
}
