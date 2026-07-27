package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Decides, and applies, who takes a fief when its holder departs.
 *
 * <p>The order is fixed:
 *
 * <ol>
 *   <li>the heir the departing holder named, if they named one and it is still good;</li>
 *   <li>otherwise the longest-standing remaining member of the fief;</li>
 *   <li>otherwise the fief <b>reverts to its parent faction</b>, whose head may regrant it.</li>
 * </ol>
 *
 * <p>Reverting rather than disbanding is the point of the whole class. A fief is held FROM a faction,
 * not owned outright, so a fief with nobody to inherit it falls back to the faction that granted it
 * exactly as it would have historically. Disbanding it instead would destroy land, members and a name
 * because one player left, and leaving it ownerless-but-untouchable would strand it forever.
 *
 * <p><b>The hard constraint:</b> nobody who has left the parent faction may inherit. A fief is held
 * from that faction, so its holder must be one of its people. The check runs against Medieval
 * Factions at the moment of succession rather than against the fief's own member list, because that
 * list is a cache of MF's membership and can drift - most obviously across a restart, where the
 * departure event that would have pruned it was never delivered.
 *
 * <p>Nothing here touches power. Fief members are already faction members, so Medieval Factions has
 * already counted their power once at the faction level; adding a fief contribution on top would let
 * a faction inflate itself for free by subdividing. {@link Fief#getCumulativePowerLevel()} is a
 * read-only sum used to size a fief's own demesne and is never fed back to MF.
 */
public class SuccessionService {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public SuccessionService(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    /** Which of the three rules decided the outcome. */
    public enum Outcome {
        /** The departing holder's named heir took the fief. */
        HEIR,
        /** No usable heir, so the longest-standing remaining member took the fief. */
        LONGEST_STANDING_MEMBER,
        /** Nobody was left to inherit, so the fief returned to the faction that granted it. */
        REVERTED_TO_FACTION
    }

    /**
     * The result of a succession.
     *
     * @param outcome    which rule decided it.
     * @param newOwnerId the new holder, or null when the fief reverted to the faction.
     */
    public record Succession(Outcome outcome, UUID newOwnerId) {
        public boolean reverted() {
            return outcome == Outcome.REVERTED_TO_FACTION;
        }
    }

    /**
     * Applies the succession rule to a fief whose holder has departed, and tells everyone concerned.
     *
     * <p>The departing holder is removed from the fief first, so they can neither inherit from
     * themselves nor be found by the longest-standing search. That also withdraws their own heir
     * nomination if they had somehow named themselves.
     *
     * @param fief             the fief that has lost its holder.
     * @param departingHolder  the player who is leaving.
     * @return what happened, so the caller can word its own message.
     */
    public Succession succeedFrom(Fief fief, UUID departingHolder) {
        fief.removeMember(departingHolder);

        Succession succession = choose(fief);

        if (succession.newOwnerId() != null) {
            // Belt and braces. Every route to a nomination goes through /fi heir, which demands fief
            // membership, so this is normally a no-op - but a holder must never be outside their own
            // fief, and a hand-edited save file should not be able to produce one.
            fief.addMember(succession.newOwnerId());
        }
        fief.setOwnerUUID(succession.newOwnerId());
        // The nomination belongs to the holder who made it, never to the seat: a new holder names
        // their own heir. Leaving it in place would let a long-departed holder's choice decide the
        // NEXT succession too.
        fief.setHeirUUID(null);
        persistentData.markDirty();

        announce(fief, departingHolder, succession);
        return succession;
    }

    /**
     * Runs the three rules in order against the fief as it stands, without changing anything.
     */
    private Succession choose(Fief fief) {
        FactionView faction = parentFactionOf(fief);

        UUID heir = fief.getHeirUUID();
        if (isEligible(faction, heir)) {
            return new Succession(Outcome.HEIR, heir);
        }

        // getMembers() preserves join order, so the first eligible entry is the longest-standing
        // member. Ineligible members are skipped rather than blocking: somebody who has left the
        // faction cannot inherit, but their presence in a stale list must not disinherit everyone
        // behind them.
        for (UUID member : fief.getMembers()) {
            if (isEligible(faction, member)) {
                return new Succession(Outcome.LONGEST_STANDING_MEMBER, member);
            }
        }
        return new Succession(Outcome.REVERTED_TO_FACTION, null);
    }

    /**
     * The faction a fief is held from, or null if Medieval Factions no longer has it.
     *
     * <p>Resolved once per succession rather than once per candidate: the check below runs over every
     * member of the fief.
     */
    private FactionView parentFactionOf(Fief fief) {
        return medievalFactionsIntegrator.getAPI().getFaction(new FactionId(fief.getFactionId()));
    }

    /**
     * Whether this player may take a fief held from the given faction: they must still be one of its
     * members.
     *
     * <p>A faction that no longer exists makes nobody eligible, so the fief reverts. That is the safe
     * direction - reverting destroys nothing, and MF disbanding a faction removes its fiefs outright
     * through {@code FactionEventListener} anyway.
     */
    private boolean isEligible(FactionView faction, UUID playerId) {
        return playerId != null && faction != null && faction.getMemberIds().contains(playerId);
    }

    /**
     * Tells the fief's remaining members what happened, and on a reversion tells the faction's head,
     * who is the only person who can then regrant it. Offline players are simply skipped; this is
     * news, not state.
     */
    private void announce(Fief fief, UUID departingHolder, Succession succession) {
        UUIDChecker uuidChecker = new UUIDChecker();
        String departedName = uuidChecker.findPlayerNameBasedOnUUID(departingHolder);

        Component message;
        if (succession.reverted()) {
            message = Component.text(departedName + " no longer holds " + fief.getName()
                    + ", and it has reverted to " + persistentData.getFactionNameOfFief(fief) + ".",
                    NamedTextColor.AQUA);
        } else {
            String successorName = uuidChecker.findPlayerNameBasedOnUUID(succession.newOwnerId());
            message = Component.text(successorName + " has succeeded " + departedName + " as holder of "
                    + fief.getName() + ".", NamedTextColor.AQUA);
        }

        for (UUID memberId : fief.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(message);
            }
        }

        if (succession.reverted()) {
            notifyFactionHead(fief, message);
        }
    }

    private void notifyFactionHead(Fief fief, Component message) {
        FactionView faction = parentFactionOf(fief);
        if (faction == null || faction.getPrimaryOwnerId() == null) {
            return;
        }
        Player head = Bukkit.getPlayer(faction.getPrimaryOwnerId());
        if (head != null && !fief.isMember(head.getUniqueId())) {
            head.sendMessage(message);
            head.sendMessage(Component.text("Use /fi grant \"" + fief.getName()
                    + "\" (playerName) to grant it to somebody.", NamedTextColor.AQUA));
        }
    }
}
