package dansplugins.fiefs.objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.utils.Logger;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class Fief {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    private String name;
    private String description = "Default Description";

    /**
     * The player who holds this fief, or null while it is held directly by the parent faction.
     *
     * <p><b>Nullable, deliberately.</b> A fief is held FROM its faction, not owned outright, so
     * "nobody holds it at present" is a real and correct state rather than an error: it is where a
     * fief lands when its holder departs and there is no one left to inherit. The faction's own head
     * regrants it from there. See {@code SuccessionService}.
     *
     * <p>Ask {@link #isOwner(UUID)} rather than comparing this field, or a vacant fief will throw on
     * every ownership check.
     */
    private UUID ownerUUID;

    /**
     * The player the current holder has named to inherit, or null if none is named.
     *
     * <p>A nomination, not an office: an heir holds nothing at all until they actually inherit, and
     * the nomination is dropped the moment it is used or stops being true. It is cleared whenever the
     * fief changes hands, so it always belongs to the holder who set it and never carries over to
     * their successor.
     */
    private UUID heirUUID;

    private String factionId;
    private ArrayList<UUID> members = new ArrayList<>();
    private final FiefFlags flags;
    private final ArrayList<UUID> invitedPlayers = new ArrayList<>();

    /**
     * The chunk this fief's capital stands in, or null while it has none.
     *
     * <p>A fief had no seat at all. A realm has one -- Medieval Factions calls it the home -- and
     * anything that has to name "the place a side must hold" needs the fief to have one too. That is
     * the whole reason this exists, and it is why a fief with no capital is refused a rising rather
     * than being given one the plugin picked: a capital chosen by a tie-break is chosen by whoever
     * wrote the tie-break, not by the player whose holding it is.
     *
     * <p>Stored as a world NAME and two chunk coordinates, matching {@code ClaimedChunk} and for the
     * same reason it does: holding a live {@code Chunk} means loading, and possibly generating, the
     * chunk merely to read where the capital is.
     */
    private String capitalWorld;
    private int capitalX;
    private int capitalZ;

    /**
     * When the current holder came to hold this fief, as epoch milliseconds.
     *
     * <p>Recorded so that "how long have you held this" can be answered, which a rebellion's tenure
     * gate needs: joining a realm, being granted a fief and taking the realm's land must not be able
     * to happen on one day.
     *
     * <p><b>Zero for a fief saved before this field existed</b>, which reads as "held since the
     * epoch" and so passes every tenure gate. That is the correct direction rather than a gap: those
     * holders genuinely have held their fiefs since before anybody was counting, and defaulting to
     * "held as of the upgrade" would silently freeze every existing fief out of the mechanic for its
     * first week.
     */
    private long heldSince;

    public Fief(MedievalFactionsIntegrator medievalFactionsIntegrator, String name, UUID ownerUUID, String factionId, Logger logger) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.factionId = factionId;
        this.heldSince = System.currentTimeMillis();
        members.add(ownerUUID);
        flags = new FiefFlags(logger);
        flags.initializeFlagValues();
    }

    public Fief(Map<String, String> fiefData, MedievalFactionsIntegrator medievalFactionsIntegrator, Logger logger) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        // flags MUST be assigned before load(), which dereferences it five times. javac does not track
        // definite assignment through a method call, so the old order compiled cleanly and then threw
        // NPE out of StorageService.loadFiefs() for any non-empty fiefs.json.
        flags = new FiefFlags(logger);
        this.load(fiefData);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** @return the holder, or null if the fief is currently held by the faction. See {@link #ownerUUID}. */
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    /**
     * @param ownerUUID the new holder, or null to leave the fief in the faction's hands.
     *
     * <p>Stamps {@link #heldSince}, so tenure is measured from the moment this holder came to it and
     * never from the fief's own founding. Without that, granting a fief to somebody would hand them
     * the previous holder's standing along with it, and a realm could arm a newcomer against itself
     * by regranting an old fief to them.
     *
     * <p>Stamped for a vacancy too. A fief nobody holds accrues no tenure worth keeping, and
     * measuring from the regrant is the honest reading either way.
     */
    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        this.heldSince = System.currentTimeMillis();
    }

    /** When the current holder came to hold this. See {@link #heldSince}. */
    public long getHeldSince() {
        return heldSince;
    }

    /**
     * Whether the given player holds this fief. Null-safe in both directions, which is why every
     * ownership check goes through it rather than through {@code getOwnerUUID().equals(...)}.
     */
    public boolean isOwner(UUID playerUUID) {
        return ownerUUID != null && ownerUUID.equals(playerUUID);
    }

    /** Whether nobody holds this fief, i.e. it has reverted to the faction and awaits a regrant. */
    public boolean isVacant() {
        return ownerUUID == null;
    }

    /** @return the named heir, or null if none is named. See {@link #heirUUID}. */
    public UUID getHeirUUID() {
        return heirUUID;
    }

    /** @param heirUUID the player to inherit this fief, or null to withdraw the nomination. */
    public void setHeirUUID(UUID heirUUID) {
        this.heirUUID = heirUUID;
    }

    public String getFactionId() {
        return factionId;
    }

    // ---- the capital ------------------------------------------------------

    /** Whether this fief has named a capital. See {@link #capitalWorld}. */
    public boolean hasCapital() {
        return capitalWorld != null;
    }

    /** The capital's world name, or null if none is named. */
    public String getCapitalWorld() {
        return capitalWorld;
    }

    /** The capital's chunk X. Meaningless unless {@link #hasCapital()}. */
    public int getCapitalX() {
        return capitalX;
    }

    /** The capital's chunk Z. Meaningless unless {@link #hasCapital()}. */
    public int getCapitalZ() {
        return capitalZ;
    }

    /**
     * Name where this fief's capital stands.
     *
     * <p>The caller is responsible for checking that the fief actually claims the chunk; this stores
     * what it is told. {@code CapitalCommand} is the only thing that should be calling it, and it
     * does check.
     */
    public void setCapital(String worldName, int chunkX, int chunkZ) {
        this.capitalWorld = worldName;
        this.capitalX = chunkX;
        this.capitalZ = chunkZ;
    }

    /**
     * Forget the capital, which is what unclaiming the chunk it stood in has to do.
     *
     * <p>A capital on land the fief no longer holds would be a fief that could not be attacked at
     * its seat, and one that reads as defended by ground somebody else owns.
     */
    public void clearCapital() {
        this.capitalWorld = null;
        this.capitalX = 0;
        this.capitalZ = 0;
    }

    /** Whether the capital stands in the given chunk. False when there is no capital at all. */
    public boolean capitalIsAt(String worldName, int chunkX, int chunkZ) {
        return capitalWorld != null && capitalWorld.equals(worldName)
                && capitalX == chunkX && capitalZ == chunkZ;
    }

    public void addMember(UUID playerUUID) {
        if (!isMember(playerUUID)) {
            members.add(playerUUID);
        }
    }

    /**
     * Removes a member, and withdraws their heir nomination with it.
     *
     * <p>The nomination has to go here rather than at each of the four call sites that can remove a
     * member (/fi kick, /fi leave, leaving the faction, succession), because an heir who is no longer
     * of the fief must not inherit it and a stale nomination is invisible to the holder.
     */
    public void removeMember(UUID playerUUID) {
        if (isMember(playerUUID)) {
            members.remove(playerUUID);
        }
        if (playerUUID.equals(heirUUID)) {
            heirUUID = null;
        }
    }

    public boolean isMember(UUID playerUUID) {
        return members.contains(playerUUID);
    }

    public List<UUID> getMembers() {
        return Collections.unmodifiableList(new ArrayList<>(members));
    }

    public void invitePlayer(UUID playerUUID) {
        if (!isInvited(playerUUID)) {
            invitedPlayers.add(playerUUID);
        }
    }

    public void uninvitePlayer(UUID playerUUID) {
        if (isInvited(playerUUID)) {
            invitedPlayers.remove(playerUUID);
        }
    }

    public boolean isInvited(UUID playerUUID) {
        return invitedPlayers.contains(playerUUID);
    }

    /**
     * The fief's land allowance: the sum of its members' Medieval Factions power. Unknown players
     * contribute 0, which the API guarantees, so no null handling is needed here.
     */
    public int getCumulativePowerLevel() {
        double cumulativePowerLevel = 0.0;
        for (UUID memberUUID : members) {
            cumulativePowerLevel += medievalFactionsIntegrator.getAPI().getPower(memberUUID);
        }
        return (int) Math.round(cumulativePowerLevel);
    }

    public int getNumMembers() {
        return members.size();
    }

    public void sendMembersListToPlayer(Player player) {
        UUIDChecker uuidChecker = new UUIDChecker();

        player.sendMessage(Component.text("=== Members of " + name + " ===", NamedTextColor.AQUA));
        for (UUID playerUUID : members) {
            player.sendMessage(Component.text(uuidChecker.findPlayerNameBasedOnUUID(playerUUID), NamedTextColor.AQUA));
        }
    }

    public FiefFlags getFlags() {
        return flags;
    }

    /**
     * Whether this is the same fief as the given one.
     *
     * <p>Deliberately NOT named equals: it was an overload of Object.equals, not an override, and had
     * no matching hashCode. It worked only because the single call site's static types happened to
     * select it -- change one of those to Object and the semantics silently flip to identity.
     */
    public boolean isSameFief(Fief fief) {
        // Objects.equals on the holder: it is nullable now, and a vacant fief must compare equal to
        // itself rather than throwing inside territory-protection checks.
        return Objects.equals(fief.getOwnerUUID(), this.getOwnerUUID())
                && fief.getName().equals(this.getName())
                && fief.getFactionId().equals(this.getFactionId());
    }

    public Map<String, String> save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();;

        Map<String, String> saveMap = new HashMap<>();
        saveMap.put("name", gson.toJson(name));
        saveMap.put("description", gson.toJson(description));
        // Both of these are nullable, and Gson writes null as the JSON literal null, which reads back
        // as a Java null. Anything already on disk predates them and simply has no key.
        saveMap.put("ownerUUID", gson.toJson(ownerUUID));
        saveMap.put("heirUUID", gson.toJson(heirUUID));
        saveMap.put("factionId", gson.toJson(factionId));
        saveMap.put("members", gson.toJson(members));
        // Nullable, like ownerUUID above: Gson writes null as the JSON literal, which reads back as
        // a Java null and means "no capital named". Anything already on disk predates these keys and
        // simply has none.
        saveMap.put("capitalWorld", gson.toJson(capitalWorld));
        saveMap.put("capitalX", gson.toJson(capitalX));
        saveMap.put("capitalZ", gson.toJson(capitalZ));
        saveMap.put("heldSince", gson.toJson(heldSince));

        saveMap.put("integerFlagValues", gson.toJson(flags.getIntegerValues()));
        saveMap.put("booleanFlagValues", gson.toJson(flags.getBooleanValues()));
        saveMap.put("doubleFlagValues", gson.toJson(flags.getDoubleValues()));
        saveMap.put("stringFlagValues", gson.toJson(flags.getStringValues()));

        return saveMap;
    }

    private void load(Map<String, String> data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Type arrayListTypeUUID = new TypeToken<ArrayList<UUID>>(){}.getType();
        Type stringToIntegerMapType = new TypeToken<HashMap<String, Integer>>(){}.getType();
        Type stringToBooleanMapType = new TypeToken<HashMap<String, Boolean>>(){}.getType();
        Type stringToDoubleMapType = new TypeToken<HashMap<String, Double>>(){}.getType();
        Type stringToStringMapType = new TypeToken<HashMap<String, String>>(){}.getType();

        name = gson.fromJson(data.get("name"), String.class);
        description = gson.fromJson(data.get("description"), String.class);
        ownerUUID = readUUID(gson, data.get("ownerUUID"));
        heirUUID = readUUID(gson, data.get("heirUUID"));
        factionId = gson.fromJson(data.get("factionId"), String.class);

        members = gson.fromJson(data.get("members"), arrayListTypeUUID);

        // Absent for every fief saved before the capital existed, which reads back as null and means
        // "no capital named". getOrDefault on the coordinates rather than a null check on each: a
        // fief with a world and no coordinates is not a state this can produce, since setCapital
        // writes all three.
        capitalWorld = gson.fromJson(data.get("capitalWorld"), String.class);
        capitalX = gson.fromJson(data.getOrDefault("capitalX", "0"), Integer.TYPE);
        capitalZ = gson.fromJson(data.getOrDefault("capitalZ", "0"), Integer.TYPE);
        // 0 for a fief saved before this existed, which reads as held since the epoch and so passes
        // every tenure gate. See the field: that is deliberate, not a gap.
        heldSince = gson.fromJson(data.getOrDefault("heldSince", "0"), Long.TYPE);

        flags.setIntegerValues(gson.fromJson(data.getOrDefault("integerFlagValues", "[]"), stringToIntegerMapType));
        flags.setBooleanValues(gson.fromJson(data.getOrDefault("booleanFlagValues", "[]"), stringToBooleanMapType));
        flags.setDoubleValues(gson.fromJson(data.getOrDefault("doubleFlagValues", "[]"), stringToDoubleMapType));
        flags.setStringValues(gson.fromJson(data.getOrDefault("stringFlagValues", "[]"), stringToStringMapType));

        flags.loadMissingFlagsIfNecessary();
    }

    /**
     * Reads an optional stored UUID.
     *
     * <p>Returns null both for a key that is absent (a save file written before the field existed)
     * and for one stored as JSON null (a vacant fief, or no named heir). The previous code called
     * {@code UUID.fromString} on the result unconditionally, which throws a NullPointerException on
     * either - and it throws out of {@code StorageService.loadFiefs()}, so one such fief would stop
     * the whole plugin enabling.
     */
    private static UUID readUUID(Gson gson, String json) {
        // Gson answers null for both cases, so one branch covers them.
        String value = gson.fromJson(json, String.class);
        return value == null ? null : UUID.fromString(value);
    }
}
