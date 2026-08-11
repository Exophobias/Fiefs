package dansplugins.fiefs.externalapi;

import java.util.List;
import java.util.UUID;

/**
 * Lets another plugin decide who inherits a fief whose holder has departed, in place of Fiefs' own
 * three-tier ladder, and lets a player read that decision <em>before</em> it fires.
 *
 * <p>The motivating case is a server that models government: a realm whose form is elective wants a
 * fief of that realm inherited the way the realm itself is inherited, rather than having the
 * longest-standing member handed it. Fiefs has no concept of a government form and should not
 * acquire one. It knows who is of a fief, in what order they joined, and who is still of the parent
 * faction; that is the whole of what it publishes here, and everything else is the implementer's.
 *
 * <h2>Not a functional interface, deliberately</h2>
 *
 * <p>Medieval Factions' {@code SuccessionPolicy} is a {@code fun interface} because it has one job.
 * This one has two, and the second is not an optimisation. A succession nobody could have predicted
 * reads as the plugin picking a favourite, so a rule that only ever speaks at the moment it fires is
 * indistinguishable from no rule at all. {@link #standingFor} is what lets {@code /fi info} and
 * {@code /fi succession} answer "who inherits this, and by what rule" on any day of the week, and it
 * is also the surface that makes a silently unwired government layer visible to a player rather than
 * only to a log file.
 *
 * <h2>When these are consulted</h2>
 *
 * <p>{@link #decide} is consulted at the moment a fief's holder departs: {@code /fi leave} by the
 * holder, and a {@code FactionMemberLeftEvent} for the holder. It is consulted <b>after</b> the
 * departing holder has been removed from the fief and <b>before</b> the new holder is seated.
 *
 * <p>{@link #standingFor} is consulted from readouts and from the standing-change check, which run
 * on commands and on rare events. Neither is consulted on a per-tick or per-chat path, and neither
 * may be made one: both read Medieval Factions once per member of the fief.
 *
 * <p><b>Zero eligible members short-circuits before {@link #decide} is asked</b>, and that is
 * structural rather than an optimisation. A policy is never asked a question with no possible
 * answer, and a broken or hostile policy cannot turn a reversion into something else. Reverting to
 * the parent faction is Fiefs' own call; the route back from there is the realm head's
 * {@code /fi grant}. {@link #standingFor} <em>is</em> still asked on an empty roll, because
 * {@link FiefSuccession#holderMayNameHeir()} is a property of the form rather than of the roll and a
 * fief must not silently regain {@code /fi heir} because everybody left the faction.
 *
 * <h2>Deferring is the normal answer</h2>
 *
 * <p>Return {@code null} from either method and Fiefs' own three-tier ladder applies: the departing
 * holder's named heir, then the longest-standing remaining member, then reversion to the faction the
 * fief is held from. A policy is expected to defer for any fief it does not govern, and deferring
 * must always be safe, so a policy that cannot reach its own state defers rather than guesses.
 *
 * <h2>A policy may reorder the ladder, never widen it, and never empty it</h2>
 *
 * <p>The answer is validated before it is used. {@link FiefSuccession#successor()} must be non-null
 * and must be in the {@code eligible} list handed in. Anything else is discarded and treated as a
 * deferral, and logged at WARNING naming the fief, because a governing policy naming an ineligible
 * player is a bug in the implementing plugin and not a normal outcome. An implementation therefore
 * cannot seat an outsider, cannot reinstate the holder who just left, and cannot leave a fief unheld.
 * Reverting to the parent faction stays Fiefs' own call and no policy can reach it in either
 * direction.
 *
 * <p>{@link #standingFor} carries one extra allowance and no extra power: it may answer a null
 * successor when, and only when, {@code eligible} is empty, which is how a rule says "this fief would
 * revert" in its own words. A null successor alongside a non-empty roll is a contradiction, so it is
 * discarded like any other invalid answer.
 *
 * <p>This is the same asymmetry Medieval Factions' {@code SuccessionPolicy} and
 * {@code ClaimOverrideProvider} set: a third-party plugin may redirect one of the host's decisions,
 * never invalidate the invariant underneath it.
 *
 * <h2>When there is exactly one eligible member, the only valid answer is that member</h2>
 *
 * <p>Any other answer is discarded, and the deferral lands on the same player, so the policy is
 * consulted on that path purely so that the sentence can name the form. It cannot change the
 * outcome. This is deliberate rather than an oversight: short-circuiting would be free, and it would
 * cost a fief of two ever learning that it follows its realm's form.
 *
 * <h2>Implementations must not block, must not save, and must not call back into Fiefs</h2>
 *
 * <p>Unlike Medieval Factions' policy this one is <b>not</b> called from inside another plugin's
 * transaction, so reading Medieval Factions from an implementation is safe: MF's api events re-fire
 * on the next server tick and {@code /fi leave} is on the main thread. That is stated rather than
 * left implicit because the MF contract says the opposite and an implementer will have read it.
 *
 * <p>Calling back into <em>Fiefs</em> is not safe. {@link #decide} runs after the departing holder
 * has been removed and before the successor is seated, so a re-entrant read sees a half-succeeded
 * fief: a fief with no holder, a member list that has already lost one name, and a nomination that
 * has not yet been cleared. {@code FiefsAPI.refreshSuccession} called from inside
 * {@link #standingFor} is a no-op rather than a stack overflow, but relying on that is relying on a
 * guard rather than on a contract.
 *
 * <h2>No clock</h2>
 *
 * <p>Neither method takes a {@code now}. There is no decision window at fief scale: nothing holds a
 * fief in the meantime, because a fief is never unheld and never pending. The decision that takes
 * time happens while the holder is still alive and seated, is continuously revisable, and is read
 * once at the instant of vacancy. A seam with no {@code now} cannot grow a timeout by accident.
 *
 * <p>If a future server decides it wants a caretaker after all, this seam extends without anything
 * here being unpicked: the eligible-list computation, the validate-or-defer asymmetry, the readouts
 * and the store all survive it. A state has to be added, not removed.
 *
 * <h2>Failure is contained</h2>
 *
 * <p>A policy that throws anything at all, including the {@link NoClassDefFoundError} a policy built
 * against a since-changed class produces, is caught, logged once at SEVERE naming the owning plugin
 * and what the failure costs, <b>dropped for the session</b>, and treated as a deferral. It is also
 * dropped when the plugin that registered it is disabled. A broken third-party plugin must not be
 * able to cost a player their fief, and it must not be able to do so quietly either: while a policy
 * is stood down, {@code /fi succession} says so in words and the first {@code /fi heir} after the
 * drop explains why the command has started working again.
 *
 * @see FiefsAPI#registerSuccessionPolicy(org.bukkit.plugin.Plugin, FiefSuccessionPolicy)
 */
public interface FiefSuccessionPolicy {

    /**
     * Who should take this fief now that its holder has gone, or null to defer to Fiefs' own ladder.
     *
     * @param fief            the fief as it stands at the moment of vacancy: {@code departingHolder}
     *                        has already been removed from its member list, and it has no holder yet.
     * @param departingHolder the player who has left.
     * @param eligible        every member of the fief who may lawfully inherit it, in join order, so
     *                        the first entry is the longest-standing. Never empty and never contains
     *                        {@code departingHolder}. This is the <b>only</b> list a policy is given,
     *                        and Fiefs re-validates the answer against it rather than against the
     *                        fief's own member list, which is a cache of Medieval Factions'
     *                        membership and can drift.
     * @return an answer whose successor is one of {@code eligible}, or null to defer. Any other
     *         answer is discarded as though null had been returned.
     */
    FiefSuccession decide(FI_Fief fief, UUID departingHolder, List<UUID> eligible);

    /**
     * Who would take this fief if its holder departed today, and whether its holder may name an heir
     * at all, or null to defer to Fiefs' own ladder.
     *
     * <p>Read continuously and printed to players, so it must answer from memory and must be a pure
     * function of state the implementer already holds. It is called on {@code /fi info},
     * {@code /fi succession}, {@code /fi heir}, and after any change that can move the answer.
     *
     * @param fief     the fief as it stands, holder included.
     * @param eligible every member who could lawfully inherit it if the holder departed now, in join
     *                 order, with the current holder excluded. <b>May be empty</b>, in which case the
     *                 fief would revert and the only valid successor is null.
     * @return an answer whose successor is one of {@code eligible}, or is null when {@code eligible}
     *         is empty, or null to defer. Any other answer is discarded as though null had been
     *         returned.
     */
    FiefSuccession standingFor(FI_Fief fief, List<UUID> eligible);
}
