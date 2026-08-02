package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.objects.Fief;
import org.bukkit.entity.Player;

import java.util.List;
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

    /** Whether this player holds the fief, rather than merely belonging to it. Null-safe. */
    public boolean isOwner(UUID playerUUID) {
        return fief.isOwner(playerUUID);
    }

    /** Everybody in the fief, holder included. Unmodifiable. */
    public List<UUID> getMembers() {
        return fief.getMembers();
    }

    /** Medieval Factions' id for the faction this fief sits inside. */
    public String getFactionId() {
        return fief.getFactionId();
    }

    /**
     * When the current holder came to hold this, as epoch milliseconds.
     *
     * <p>Zero for a fief that predates the field being recorded, which reads as "held since the
     * epoch". A consumer gating on tenure should treat that as long-held rather than as unknown: the
     * holder genuinely has held it since before anybody was counting.
     */
    public long getHeldSince() {
        return fief.getHeldSince();
    }

    // ---- the capital ------------------------------------------------------

    /**
     * Whether this fief has named a capital.
     *
     * <p>Published because a consumer that needs "the place a side must hold" has to be able to tell
     * a fief that has one from a fief that does not, and must refuse rather than pick one: a capital
     * chosen by a tie-break is chosen by whoever wrote the tie-break.
     */
    public boolean hasCapital() {
        return fief.hasCapital();
    }

    /** The capital's world NAME, or null when none is named. */
    public String getCapitalWorld() {
        return fief.getCapitalWorld();
    }

    /** The capital's chunk X. Meaningless unless {@link #hasCapital()}. */
    public int getCapitalX() {
        return fief.getCapitalX();
    }

    /** The capital's chunk Z. Meaningless unless {@link #hasCapital()}. */
    public int getCapitalZ() {
        return fief.getCapitalZ();
    }

    public Object getFlag(String flag) {
        return fief.getFlags().getFlag(flag);
    }
}
