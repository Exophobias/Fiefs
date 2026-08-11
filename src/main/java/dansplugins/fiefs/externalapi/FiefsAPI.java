package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class FiefsAPI {
    private final PersistentData persistentData;

    /**
     * The one shared succession service, NOT a copy of anything.
     *
     * <p>Load-bearing, and the trap is easy to walk into. {@code Fiefs.getAPI()} returns a
     * <b>new</b> {@code FiefsAPI} on every call, so the ServicesManager holds one instance while a
     * consumer that called {@code getAPI()} holds another. A policy kept as a field on this class
     * would therefore be registered on one object and invisible to the other, and the symptom would
     * be a government layer that reports itself installed and decides nothing. The policy lives on
     * the service, which there is exactly one of, and this class only forwards.
     */
    private final SuccessionService successionService;

    public FiefsAPI(PersistentData persistentData, SuccessionService successionService) {
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    public FI_Fief getFief(String fiefName) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(fiefName);
        return fief == null ? null : new FI_Fief(fief);
    }

    public FI_Fief getFief(Player player) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(player);
        return fief == null ? null : new FI_Fief(fief);
    }

    public FI_Fief getFief(UUID playerUUID) {
        // Null in, null out. Wrapping a null Fief handed callers a non-null FI_Fief around nothing,
        // so their own != null check could not detect "no such fief".
        Fief fief = persistentData.getFief(playerUUID);
        return fief == null ? null : new FI_Fief(fief);
    }

    /**
     * The fief with this stable id, or null.
     *
     * <p>The lookup a consumer holding a stored reference to a fief wants, and the only one that is
     * safe across {@code /fi rename}. Named apart from the {@code getFief} overloads for the reason
     * {@code PersistentData.getFiefById} is: two of those take a UUID and both mean a player's.
     *
     * @param fiefId a fief's own id, from {@link FI_Fief#getId()}
     */
    public FI_Fief getFiefById(UUID fiefId) {
        Fief fief = persistentData.getFiefById(fiefId);
        return fief == null ? null : new FI_Fief(fief);
    }

    public ArrayList<FI_Fief> getFiefsOfFaction(String factionId) {
        ArrayList<Fief> fiefs = persistentData.getFiefsOfFaction(factionId);
        ArrayList<FI_Fief> toReturn = new ArrayList<>();
        for (Fief fief : fiefs) {
            toReturn.add(new FI_Fief(fief));
        }
        return toReturn;
    }

    /**
     * The fief this player <b>holds</b>, or null if they hold none.
     *
     * <p>Deliberately distinct from {@link #getFief(UUID)}, which answers "which fief is this player
     * <em>in</em>". Every member of a fief is not entitled to speak for it, and a consumer asking
     * about authority -- a title, a rising, anything a holder may do and a member may not -- wants
     * this one. The two differ for every fief with more than one member, so getting them confused is
     * a mistake that works in testing and fails on a live server.
     *
     * <p>Null for a vacant fief even when asked about its previous holder: a fief that has reverted
     * to its faction is held by nobody, which is a real state rather than an error.
     */
    public FI_Fief getFiefHeldBy(UUID playerUUID) {
        Fief fief = persistentData.getFief(playerUUID);
        if (fief == null || !fief.isOwner(playerUUID)) {
            return null;
        }
        return new FI_Fief(fief);
    }

    /**
     * Every chunk a fief holds, as {@code world name, chunk x, chunk z} triples.
     *
     * <p>Published because a consumer that has to move a fief's land -- a secession does exactly
     * that -- needs to know which chunks they are, and the claim records were internal. Returned as
     * coordinates rather than as Bukkit {@code Chunk}s on purpose: materialising one loads, and if
     * necessary generates, the chunk, so a caller merely counting a fief's land would generate all
     * of it.
     *
     * @return a list of {@code int[]{x, z}} keyed by world name; empty for an unknown fief
     */
    public Map<String, List<int[]>> getClaimedChunksOfFief(String fiefName) {
        Map<String, List<int[]>> byWorld = new LinkedHashMap<>();
        for (ClaimedChunk chunk : persistentData.getClaimedChunks()) {
            if (chunk.getFief() != null && chunk.getFief().equalsIgnoreCase(fiefName)) {
                byWorld.computeIfAbsent(chunk.getWorld(), world -> new ArrayList<>())
                        .add(new int[] {chunk.getX(), chunk.getZ()});
            }
        }
        return byWorld;
    }

    // ---- the succession seam ----------------------------------------------

    /**
     * Hands another plugin the decision of who inherits a fief. At most one, and the last one wins.
     *
     * <p>Read {@link FiefSuccessionPolicy} before implementing one. The two things worth knowing
     * before you open it are that returning null defers to Fiefs' own ladder and is always safe, and
     * that a policy that throws is stood down for the rest of the session rather than allowed to cost
     * a player their fief.
     *
     * <p>Registering prints a line naming {@code owner}, so a plugin that believes it registered and
     * did not is visible in the boot log rather than merely absent from it.
     *
     * @param owner  your plugin. Named in every message about the policy, and the answer to
     *               {@link #getSuccessionPolicyOwner()}.
     * @param policy the rule.
     */
    public void registerSuccessionPolicy(Plugin owner, FiefSuccessionPolicy policy) {
        successionService.registerSuccessionPolicy(owner, policy);
    }

    /** Withdraws a policy. Silent if it was not the one in force. */
    public void unregisterSuccessionPolicy(FiefSuccessionPolicy policy) {
        successionService.unregisterSuccessionPolicy(policy);
    }

    /**
     * The plugin whose policy is in force, or null when Fiefs' own ladder is deciding.
     *
     * <p>Exists so a registering plugin can check that the registration it believes it made is the
     * one actually in force. That check is worth writing: the dangerous state is not "the government
     * layer is absent", it is "the government layer is present and every fief is quietly resolving by
     * the old rule while the server believes forms are honoured", and this is the only way to detect
     * it from the side that can tell right from wrong.
     *
     * <p>Null covers never-registered, withdrawn and stood-down alike, because all three mean fiefs
     * are not following their realms.
     */
    public String getSuccessionPolicyOwner() {
        return successionService.getSuccessionPolicyOwner();
    }

    /**
     * Recomputes what this fief stands to pass to, and tells the fief if the answer moved.
     *
     * <p>Call it after anything on your side that can change the answer - a vote cast, an investiture
     * set - and nowhere else. It reads Medieval Factions once per member of the fief, so it must
     * never be reached from a per-tick, per-move or per-chat path.
     *
     * <p>Calling this from inside {@link FiefSuccessionPolicy#standingFor} is a no-op rather than a
     * stack overflow, but that is a guard and not an invitation.
     *
     * @param fiefId a fief's own stable id, from {@link FI_Fief#getId()}. An unknown id does nothing.
     */
    public void refreshSuccession(UUID fiefId) {
        successionService.refreshSuccession(fiefId);
    }

    /**
     * The stable id of every fief on the server.
     *
     * <p>Published for a consumer that keeps a row per fief and needs to prune the rows of fiefs that
     * no longer exist. A fief can vanish two ways that publish no event a consumer can see -
     * {@code /fi disband}, and a faction disbanding, which removes all of its fiefs at once - so a
     * consumer with its own store has no other way to notice.
     *
     * <p><b>An empty set is not proof that there are no fiefs.</b> It is also exactly what a Fiefs
     * that failed to load looks like, so a prune that reads "no fiefs exist" as "delete every row"
     * would destroy a server's state because a dependency was late. Treat empty as unknown and skip
     * the prune.
     */
    public Set<UUID> getFiefIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Fief fief : persistentData.getFiefs()) {
            ids.add(fief.getId());
        }
        return ids;
    }
}
