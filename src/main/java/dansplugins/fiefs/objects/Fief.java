package dansplugins.fiefs.objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.utils.Logger;
import dansplugins.fiefs.utils.UUIDChecker;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class Fief {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;

    private String name;
    private String description = "Default Description";
    private UUID ownerUUID;
    private String factionId;
    private ArrayList<UUID> members = new ArrayList<>();
    private final FiefFlags flags;
    private final ArrayList<UUID> invitedPlayers = new ArrayList<>();

    public Fief(MedievalFactionsIntegrator medievalFactionsIntegrator, String name, UUID ownerUUID, String factionId, Logger logger) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.factionId = factionId;
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

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getFactionId() {
        return factionId;
    }

    public void addMember(UUID playerUUID) {
        if (!isMember(playerUUID)) {
            members.add(playerUUID);
        }
    }

    public void removeMember(UUID playerUUID) {
        if (isMember(playerUUID)) {
            members.remove(playerUUID);
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

    public int getCumulativePowerLevel() {
        double cumulativePowerLevel = 0.0;
        for (UUID memberUUID : members) {
            com.dansplugins.factionsystem.player.MfPlayer mfPlayer =
                medievalFactionsIntegrator.getAPI().getServices().getPlayerService().getPlayerByPlayerId(memberUUID.toString());
            double memberPowerLevel = mfPlayer != null ? mfPlayer.getPower() : 0.0;
            cumulativePowerLevel += memberPowerLevel;
        }
        return (int) Math.round(cumulativePowerLevel);
    }

    public int getNumMembers() {
        return members.size();
    }

    public void sendMembersListToPlayer(Player player) {
        UUIDChecker uuidChecker = new UUIDChecker();

        player.sendMessage(ChatColor.AQUA + "=== Members of " + name + " ===");
        for (UUID playerUUID : members) {
            player.sendMessage(ChatColor.AQUA + uuidChecker.findPlayerNameBasedOnUUID(playerUUID));
        }
    }

    public FiefFlags getFlags() {
        return flags;
    }

    public boolean equals(Fief fief) {
        return fief.getOwnerUUID().equals(this.getOwnerUUID())
                && fief.getName().equals(this.getName())
                && fief.getFactionId().equals(this.getFactionId());
    }

    public Map<String, String> save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();;

        Map<String, String> saveMap = new HashMap<>();
        saveMap.put("name", gson.toJson(name));
        saveMap.put("description", gson.toJson(description));
        saveMap.put("ownerUUID", gson.toJson(ownerUUID));
        saveMap.put("factionId", gson.toJson(factionId));
        saveMap.put("members", gson.toJson(members));

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
        ownerUUID = UUID.fromString(gson.fromJson(data.get("ownerUUID"), String.class));
        factionId = gson.fromJson(data.get("factionId"), String.class);

        members = gson.fromJson(data.get("members"), arrayListTypeUUID);

        flags.setIntegerValues(gson.fromJson(data.getOrDefault("integerFlagValues", "[]"), stringToIntegerMapType));
        flags.setBooleanValues(gson.fromJson(data.getOrDefault("booleanFlagValues", "[]"), stringToBooleanMapType));
        flags.setDoubleValues(gson.fromJson(data.getOrDefault("doubleFlagValues", "[]"), stringToDoubleMapType));
        flags.setStringValues(gson.fromJson(data.getOrDefault("stringFlagValues", "[]"), stringToStringMapType));

        flags.loadMissingFlagsIfNecessary();
    }
}
