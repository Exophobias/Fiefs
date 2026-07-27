package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.objects.Fief;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class FI_Fief {
    private final Fief fief;

    public FI_Fief(Fief fief) {
        this.fief = fief;
    }

    public String getName() {
        return fief.getName();
    }

    /**
     * The player holding this fief, or <b>null</b> if it has reverted to its faction and awaits a
     * regrant. Callers must handle null: a fief is held from a faction rather than owned outright, so
     * "held by the faction itself" is a normal state.
     */
    public UUID getOwner() {
        return fief.getOwnerUUID();
    }

    /** Whether the fief currently has no holder. Equivalent to {@code getOwner() == null}. */
    public boolean isVacant() {
        return fief.isVacant();
    }

    /**
     * The player named to inherit this fief, or null if none is named. A nomination only: they hold
     * nothing until the current holder departs, and it may still be overtaken if they leave the
     * faction first.
     */
    public UUID getHeir() {
        return fief.getHeirUUID();
    }

    public boolean isMember(Player player) {
        return fief.isMember(player.getUniqueId());
    }

    public Object getFlag(String flag) {
        return fief.getFlags().getFlag(flag);
    }
}
