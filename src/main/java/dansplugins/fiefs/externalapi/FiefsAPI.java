package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class FiefsAPI {
    private final PersistentData persistentData;

    public FiefsAPI(PersistentData persistentData) {
        this.persistentData = persistentData;
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

    public ArrayList<FI_Fief> getFiefsOfFaction(String factionId) {
        ArrayList<Fief> fiefs = persistentData.getFiefsOfFaction(factionId);
        ArrayList<FI_Fief> toReturn = new ArrayList<>();
        for (Fief fief : fiefs) {
            toReturn.add(new FI_Fief(fief));
        }
        return toReturn;
    }
}
