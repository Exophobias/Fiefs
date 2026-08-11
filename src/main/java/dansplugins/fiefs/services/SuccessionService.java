package dansplugins.fiefs.services;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FI_Fief;
import dansplugins.fiefs.externalapi.FiefSuccession;
import dansplugins.fiefs.externalapi.FiefSuccessionPolicy;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decides, and applies, who takes a fief when its holder departs.
 *
 * <p>The order is fixed:
 *
 * <ol>
 *   <li>whoever a registered {@link FiefSuccessionPolicy} names, if it names one and it is still
 *       good;</li>
 *   <li>otherwise the heir the departing holder named, if they named one and it is still good;</li>
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
 * <p>That constraint survived the policy seam by construction rather than by a second check. The
 * eligible list is built here, once, live against Medieval Factions; it is the only list a policy
 * ever sees; and the answer is re-validated against it and never against {@code fief.getMembers()}.
 * A policy therefore cannot reach the stale list this class was written to survive, and there is
 * exactly one gate rather than one per rule.
 *
 * <h2>The policy seam, and why nothing here has a clock</h2>
 *
 * <p>A server that models government wants a fief inherited the way the realm it is held from is
 * inherited. Fiefs holds no forms and must not learn any, so the decision is delegated and the
 * sentence explaining it is written by whoever decided. See {@link FiefSuccessionPolicy} for the
 * contract; the two properties worth repeating here are that deferring is always safe, and that
 * nothing in this feature waits for anything.
 *
 * <p>There is no interregnum, no regent, no decision window and no sweep. {@code Fief.ownerUUID} is
 * nullable and {@code isVacant()} is load-bearing in five places, but nothing races to fill it and no
 * design here leaves it empty on purpose: a fief passes at the instant of vacancy under every form.
 * That is what keeps {@code FiefsRising}, {@code TitleService} and PatriamHeraldry outside the blast
 * radius, because occupancy and authority never diverge. If a caretaker is ever wanted, it is added
 * to this seam rather than unpicked from it.
 *
 * <h2>State</h2>
 *
 * <p>None of it is saved. {@code fiefs.json} is unchanged by this feature, {@code Fief} gained no
 * field, and {@code StorageService} was untouched, so there is no migration and no absent-key
 * handling. A save file carrying ballots that only mean something when a second plugin is installed
 * would be a data dependency in the wrong direction, and Fiefs must stay a plugin that runs correctly
 * alone and loses nothing but the form.
 *
 * <p>Nothing here touches power. Fief members are already faction members, so Medieval Factions has
 * already counted their power once at the faction level; adding a fief contribution on top would let
 * a faction inflate itself for free by subdividing. {@link Fief#getCumulativePowerLevel()} is a
 * read-only sum used to size a fief's own demesne and is never fed back to MF.
 */
public class SuccessionService {

    /** The boot line for a server with no government layer. Half of the pair; see the other below. */
    private static final String NO_POLICY_BOOT_LINE =
            "No fief succession policy is registered. A fief passes to its holder's named heir, then "
                    + "to its longest-standing member, then back to the faction that granted it. It "
                    + "does not follow its realm's government form.";

    /**
     * Printed when a policy returned a seat but no words for it. Replacing the sentence rather than
     * discarding the seat is deliberate: losing a player their fief over a null string is the wrong
     * direction.
     */
    private static final String GENERIC_EXPLANATION = "Its realm's government decided it.";

    /**
     * Printed when a policy refuses {@code /fi heir} but supplies no sentence. Deliberately vague
     * about the form, because Fiefs does not know what one is and must not invent the name of one.
     */
    private static final String GENERIC_HEIR_REFUSAL =
            "Your realm's government decides who inherits this fief, not you.";

    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    /**
     * Held rather than its logger, so the logger is resolved lazily.
     *
     * <p>This service is constructed in a field initializer of the plugin, and a field initializer
     * that reaches for anything Bukkit sets up later is the exact shape of the null dereference the
     * ordering comment in {@code Fiefs} already records.
     */
    private final Plugin plugin;

    /**
     * The one registered policy, or null.
     *
     * <p>At most one, and registering a second replaces the first. Not a list, for the reason
     * {@code PatriamGovernmentApi.registerFaithAuthority} already gives: two answers to "who inherits
     * this fief" is two rules for one seat, and one state with two producers is a defect this
     * codebase has already paid for once.
     *
     * <p>Volatile rather than synchronised. It is written a handful of times at startup and read on
     * commands and departures, so a lock would buy nothing that a safe publish does not.
     */
    private volatile FiefSuccessionPolicy policy;

    /** The plugin that registered {@link #policy}, so a wrong or stale registrant is nameable. */
    private volatile Plugin policyOwner;

    /**
     * A policy that threw, kept so it cannot re-register itself and quietly resume.
     *
     * <p>Mirrors Medieval Factions' {@code SuccessionPolicyRegistry.poisoned}. A different policy
     * object may still register: dropping is a judgement about one broken implementation, not about
     * the seam.
     */
    private volatile FiefSuccessionPolicy droppedPolicy;

    /**
     * Whether a policy was stood down after failing, rather than merely never registered.
     *
     * <p>Kept apart from "no policy at all" because the two are different things to tell a player.
     * One server never had a government layer; the other has one that broke, and on that one
     * {@code /fi heir} has just started working again for a reason the holder is owed.
     */
    private volatile boolean policyStoodDown;

    /**
     * The last announced standing answer per fief, so a change that changes nothing says nothing.
     *
     * <p>In memory only and never saved. {@link Optional} rather than a nullable value because
     * "stands to revert to the faction" is a real answer that must be distinguishable from "not
     * computed yet", and {@link ConcurrentHashMap} forbids null values. A cache miss seeds silently
     * and announces nothing, so a restart cannot produce a spurious line.
     */
    private final Map<UUID, Optional<UUID>> standingAnswers = new ConcurrentHashMap<>();

    /**
     * Guards against a policy that calls back in through {@code FiefsAPI.refreshSuccession} from
     * inside {@code standingFor}.
     *
     * <p>Per thread rather than global: the failure being prevented is an unbounded recursion on one
     * stack, and a global flag would additionally make two unrelated threads silently skip each
     * other's refresh.
     */
    private final ThreadLocal<Boolean> refreshing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** So exactly one of the two boot lines is printed, and a log with neither is itself an alarm. */
    private final AtomicBoolean bootStateReported = new AtomicBoolean();

    /** Once per session each, because a policy with a bad string has it on every single answer. */
    private final AtomicBoolean blankExplanationLogged = new AtomicBoolean();
    private final AtomicBoolean blankRefusalLogged = new AtomicBoolean();

    /**
     * Holders already told that their nomination decides again after a policy was stood down.
     *
     * <p>A permission that widens on failure must widen loudly, but only once per holder: repeating
     * it on every {@code /fi heir} would train them to skip it.
     */
    private final Set<UUID> toldNominationDecidesAgain = ConcurrentHashMap.newKeySet();

    public SuccessionService(MedievalFactionsIntegrator medievalFactionsIntegrator,
                             PersistentData persistentData, Plugin plugin) {
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.plugin = plugin;
    }

    /** Which of the four rules decided the outcome. */
    public enum Outcome {
        /** A registered {@link FiefSuccessionPolicy} named the successor. */
        CHOSEN_BY_POLICY,
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
     * @param outcome     which rule decided it.
     * @param newOwnerId  the new holder, or null when the fief reverted to the faction.
     * @param explanation the deciding rule's own sentence, in the past tense, printed under the
     *                    announcement. <b>Null on a reversion</b>, which keeps today's message exactly
     *                    as it was: no policy was consulted, so there is no rule to quote and
     *                    inventing one would be Fiefs speaking for a layer it does not have.
     */
    public record Succession(Outcome outcome, UUID newOwnerId, String explanation) {
        public boolean reverted() {
            return outcome == Outcome.REVERTED_TO_FACTION;
        }
    }

    /**
     * Who a fief would pass to today, and why, computed without changing anything.
     *
     * <p>This is what makes the government layer readable before it fires, and it is the same object
     * behind {@code /fi info}, {@code /fi succession}, {@code /fi heir}'s refusal and the
     * standing-change announcement, so those four can never disagree.
     *
     * @param presumptive       who would inherit, or null if the fief would revert to its faction.
     * @param rule              the label for a readout header. Never null.
     * @param explanation       the deciding rule's own sentence, present tense, or null when nobody
     *                          would inherit.
     * @param holderMayNameHeir whether {@code /fi heir} is the rule here. True whenever the ladder is
     *                          answering, because the ladder genuinely reads the nomination.
     * @param heirRefusal       what to print when it is not. Non-null exactly when
     *                          {@code holderMayNameHeir} is false.
     * @param fromPolicy        whether a policy answered this, which the readouts use to decide
     *                          whether the sentence is a clause of theirs or a sentence of somebody
     *                          else's. Also the honest answer to "is a government layer running".
     */
    public record StandingAnswer(UUID presumptive, String rule, String explanation,
                                 boolean holderMayNameHeir, String heirRefusal, boolean fromPolicy) {
    }

    // ---- registration -----------------------------------------------------

    /**
     * Publishes another plugin's rule for who inherits a fief. At most one, last one wins.
     *
     * <p>The confirmation line is printed <b>from here</b>, naming the {@link Plugin} handed in, so
     * it cannot be printed by a plugin that believes it registered and did not, and a wrong or stale
     * registrant is visible rather than merely "something registered".
     *
     * @param owner  the plugin the policy belongs to. Named in every failure message, and the answer
     *               to {@link #getSuccessionPolicyOwner()}, which exists so a registering plugin can
     *               check that the registration it believes it made is the one in force.
     * @param policy the rule. Read {@link FiefSuccessionPolicy} first; in particular, deferring is
     *               the normal answer and it must always be safe.
     */
    public void registerSuccessionPolicy(Plugin owner, FiefSuccessionPolicy policy) {
        if (owner == null || policy == null) {
            throw new IllegalArgumentException("A fief succession policy and its owning plugin are both required.");
        }
        if (policy == droppedPolicy) {
            logger().warning("The fief succession policy from " + owner.getName() + " was stood down "
                    + "earlier this session after it threw, so it has not been re-registered. Restart "
                    + "the server once the fault is fixed.");
            return;
        }
        this.policy = policy;
        this.policyOwner = owner;
        this.policyStoodDown = false;
        // The standing answers were computed under whatever rule was in force before, so they are
        // not evidence of anything now. Cleared rather than recomputed: a miss seeds silently, and
        // recomputing here would announce a line for every fief on the server at boot.
        standingAnswers.clear();
        bootStateReported.set(true);
        logger().info(owner.getName() + " now decides fief succession: a fief follows its realm's "
                + "government form.");
    }

    /** Withdraws a policy. Silent if it was not the one in force. */
    public void unregisterSuccessionPolicy(FiefSuccessionPolicy policy) {
        if (policy == null || this.policy != policy) {
            return;
        }
        String owner = policyOwner == null ? "an unnamed plugin" : policyOwner.getName();
        this.policy = null;
        this.policyOwner = null;
        standingAnswers.clear();
        logger().info(owner + " has withdrawn its fief succession policy. A fief passes to its "
                + "holder's named heir, then to its longest-standing member, then back to the faction "
                + "that granted it.");
    }

    /**
     * The name of the plugin whose policy is in force, or null when the ladder is deciding.
     *
     * <p>Null covers all three ways the ladder can be deciding - none was ever registered, one was
     * withdrawn, one was stood down after failing - because a caller checking that its own
     * registration is in force wants the same answer for every one of them, and any of them means
     * fiefs are not following their realms.
     */
    public String getSuccessionPolicyOwner() {
        Plugin owner = policyOwner;
        return owner == null ? null : owner.getName();
    }

    /** Whether a policy was stood down after failing, as opposed to never registered. */
    public boolean isPolicyStoodDown() {
        return policyStoodDown;
    }

    /**
     * Prints which ladder is in force, unless a registration has already said so.
     *
     * <p>Called from {@code ServerLoadEvent}, which is the only moment at which "nobody registered"
     * is a fact rather than a race: Fiefs enables before the plugins that depend on it, so asking
     * during {@code onEnable} would answer "none" on a perfectly healthy server.
     *
     * <p>An event and not a timer. {@code Scheduler} keeps doing exactly one thing.
     */
    public void announceLadderInForceIfSilent() {
        if (!bootStateReported.compareAndSet(false, true)) {
            return;
        }
        logger().info(NO_POLICY_BOOT_LINE);
    }

    /**
     * Drops the policy when the plugin that registered it stops functioning.
     *
     * <p>Closes the case {@code PatriamGovernmentApi} already warns about for its own extension
     * points: an authority left registered by a plugin that is no longer running. Not treated as a
     * poisoning - the plugin may enable again and register afresh - but it does stand the layer down,
     * because until it does, every fief is resolving by the ladder while the server believes forms
     * are honoured.
     *
     * <p><b>The drop is unconditional and the alarm is not.</b> A plugin can only find this seam
     * during its own enable by enabling after Fiefs, so a clean shutdown, which disables in the
     * reverse of that order, always disables it while Fiefs is still up and still listening. Warning
     * there would end every single healthy run with a line saying no fief follows its realm's
     * government form, and an alarm that fires on every healthy run is one an operator learns to
     * read past, which is how the one that matters gets missed. Mid-session the same line is the
     * whole point: the layer really has stopped answering on a server that is still taking players,
     * and nothing else will say so.
     */
    public void dropPolicyOwnedBy(Plugin disabled) {
        Plugin owner = policyOwner;
        if (disabled == null || owner == null || !owner.getName().equals(disabled.getName())) {
            return;
        }
        this.policy = null;
        this.policyOwner = null;
        this.policyStoodDown = true;
        this.toldNominationDecidesAgain.clear();
        standingAnswers.clear();
        if (serverIsStopping()) {
            return;
        }
        logger().warning(disabled.getName() + " has been disabled while the server is running, so its "
                + "fief succession policy is no longer in force. Every fief on this server now passes "
                + "to its holder's named heir, then to its longest-standing member, then back to the "
                + "faction that granted it, and no fief follows its realm's government form.");
    }

    /**
     * Whether this disable is the server going down, rather than something going wrong.
     *
     * <p>Paper sets the flag behind {@link org.bukkit.Server#isStopping()} at the top of
     * {@code MinecraftServer.stopServer}, before that method calls {@code disablePlugins}. So it
     * already reads true for every {@code PluginDisableEvent} of a shutdown, and false for a
     * mid-session disable, a {@code /reload}, or a plugin that disables itself after a fault. Paper's
     * own plugin manager asks the same question the same way, to decide whether a disabling plugin's
     * chunk tickets are still worth releasing.
     *
     * <p>Two cheaper-looking signals were rejected, because both answer the wrong question. Our own
     * enabled flag answers yes at every one of these moments, shutdown included: the event is
     * dispatched <em>before</em> the disabling plugin's {@code onDisable}, and Fiefs is disabled after
     * its dependents in any case, so a check on it would call every shutdown a mid-session disable and
     * leave the alarm exactly where it was. A flag set from our own {@code onDisable} is later still,
     * for the same reason: by the time Fiefs is told, every plugin that registered anything with it
     * has already gone.
     *
     * <p>Paper-only, which costs nothing here. This plugin compiles against paper-api and Patriam
     * runs Paper, for the reason the pom already records.
     */
    private boolean serverIsStopping() {
        return plugin.getServer().isStopping();
    }

    // ---- succession -------------------------------------------------------

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

        Succession succession = choose(fief, departingHolder);

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
        //
        // This line stays exactly as it is, and an implementer must not "fix" it. It breaks any
        // design with a pending window, because the fall-through would find the nomination already
        // deleted; this design has no pending window, so it is correct.
        fief.setHeirUUID(null);
        persistentData.markDirty();

        // The fief now stands somewhere completely different, and the cached answer describes the
        // holder who has just gone. Forgotten rather than recomputed, so the next refresh seeds
        // silently instead of announcing a change nobody made.
        standingAnswers.remove(fief.getId());

        announce(fief, departingHolder, succession);
        return succession;
    }

    /**
     * Runs the rules in order against the fief as it stands, without changing anything.
     *
     * <p>Zero eligible members short-circuits before the policy is asked, and that is structural
     * rather than an optimisation: a policy is never asked a question with no possible answer, and a
     * broken or hostile policy cannot turn a reversion into something else. Reverting is Fiefs' own
     * call and {@code /fi grant} is the route back.
     */
    private Succession choose(Fief fief, UUID departingHolder) {
        FactionView faction = parentFactionOf(fief);
        List<UUID> eligible = eligible(fief, faction, departingHolder);

        if (eligible.isEmpty()) {
            return new Succession(Outcome.REVERTED_TO_FACTION, null, null);
        }

        FiefSuccessionPolicy current = policy;
        FiefSuccession answer = current == null ? null
                : guarded(current, "deciding who inherits " + fief.getName(),
                        p -> p.decide(new FI_Fief(fief), departingHolder, eligible));

        if (answer != null && answer.successor() != null && eligible.contains(answer.successor())) {
            return new Succession(Outcome.CHOSEN_BY_POLICY, answer.successor(),
                    explanationOrGeneric(answer));
        }
        if (answer != null) {
            // Not an error worth failing a departure over, but not a normal outcome either: a
            // governing policy naming somebody who may not inherit is a bug in the addon, and it is
            // invisible unless it is said out loud, because the ladder answers correctly underneath.
            logger().warning("The fief succession policy from " + ownerName() + " named "
                    + answer.successor() + " to inherit " + fief.getName() + ", who is not eligible "
                    + "to. Ignoring it; Fiefs' own succession order applies.");
        }

        UUID heir = fief.getHeirUUID();
        if (heir != null && eligible.contains(heir)) {
            return new Succession(Outcome.HEIR, heir,
                    nameOf(departingHolder) + " had named them heir.");
        }
        // eligible preserves join order, so the first entry is the longest-standing member.
        return new Succession(Outcome.LONGEST_STANDING_MEMBER, eligible.get(0),
                "No heir was named, so it passed to its longest-standing member.");
    }

    // ---- the standing answer ----------------------------------------------

    /**
     * Who this fief would pass to if its holder departed right now, and by what rule.
     *
     * <p>Reads Medieval Factions once per member of the fief, so it is explicitly forbidden on any
     * per-tick or per-chat path. Every caller is a command or a rare event.
     *
     * <p>The current holder is excluded from the roll, since the question is what happens when they
     * go. A vacant fief excludes nobody, which reads as "whoever would take it next", and is the
     * honest answer for a fief its faction is holding pending a regrant.
     */
    public StandingAnswer standingAnswerFor(Fief fief) {
        FactionView faction = parentFactionOf(fief);
        List<UUID> eligible = eligible(fief, faction, fief.getOwnerUUID());

        FiefSuccessionPolicy current = policy;
        FiefSuccession answer = current == null ? null
                : guarded(current, "reading the standing succession of " + fief.getName(),
                        p -> p.standingFor(new FI_Fief(fief), eligible));

        if (answer != null && standingAnswerIsUsable(answer, eligible)) {
            return new StandingAnswer(answer.successor(), ruleOrGeneric(answer),
                    answer.successor() == null ? null : explanationOrGeneric(answer),
                    answer.holderMayNameHeir(), answer.heirRefusal(), true);
        }
        if (answer != null) {
            logger().warning("The fief succession policy from " + ownerName() + " said "
                    + fief.getName() + " stands to pass to " + answer.successor() + ", who could not "
                    + "inherit it. Ignoring it; Fiefs' own succession order applies.");
        }
        return ladderStanding(fief, eligible);
    }

    /**
     * A standing answer may say "nobody", where a succession answer may not, and only on an empty
     * roll.
     *
     * <p>That is the whole of the extra allowance, and it buys the one thing Fiefs cannot say for
     * itself: a rule explaining, in its own words, that a fief would revert. A null successor
     * alongside a roll with people on it is a contradiction rather than an opinion, so it is
     * discarded exactly like a successor nobody could seat.
     */
    private static boolean standingAnswerIsUsable(FiefSuccession answer, List<UUID> eligible) {
        return answer.successor() == null
                ? eligible.isEmpty()
                : eligible.contains(answer.successor());
    }

    /** Fiefs' own answer: the nomination, then seniority, then reversion. */
    private StandingAnswer ladderStanding(Fief fief, List<UUID> eligible) {
        String rule = "its holder's named heir, then its longest-standing member, then back to "
                + persistentData.getFactionNameOfFief(fief);
        if (eligible.isEmpty()) {
            return new StandingAnswer(null, rule, null, true, null, false);
        }
        UUID heir = fief.getHeirUUID();
        if (heir != null && eligible.contains(heir)) {
            return new StandingAnswer(heir, rule, "named by its holder", true, null, false);
        }
        return new StandingAnswer(eligible.get(0), rule, "its longest-standing member", true, null, false);
    }

    /**
     * Recomputes the standing answer and announces it to the fief, but only if it actually moved.
     *
     * <p>This is the political moment of the whole feature, and it is what converts a list of
     * preferences into something a fief can see and respond to <em>before</em> it matters. It is also
     * the mitigation for a holder inviting a bloc to swing an elective fief: the flip is public
     * inside the fief the moment it happens.
     *
     * <p>Called after {@code /fi join}, {@code /fi kick}, {@code /fi heir} in both directions, a
     * non-holder's {@code /fi leave}, a non-holder's departure from the faction, and from
     * {@code FiefsAPI.refreshSuccession} after a vote or an investiture. Never on a movement,
     * interaction or chat path: see {@link #standingAnswerFor}.
     */
    public void refreshSuccession(Fief fief) {
        if (fief == null) {
            return;
        }
        if (Boolean.TRUE.equals(refreshing.get())) {
            // A policy that reaches back in through the api from inside standingFor. A no-op is the
            // answer rather than a throw: the caller is another plugin, and the recursion it would
            // otherwise cause ends in a StackOverflowError out of a command a player typed.
            return;
        }
        refreshing.set(Boolean.TRUE);
        try {
            StandingAnswer answer = standingAnswerFor(fief);
            Optional<UUID> now = Optional.ofNullable(answer.presumptive());
            Optional<UUID> before = standingAnswers.put(fief.getId(), now);
            if (before == null || before.equals(now)) {
                // Seeded, or nothing moved. Either way there is no event to report, and a message
                // with no event behind it is worse than silence.
                return;
            }
            announceStandingChange(fief, answer);
        } finally {
            refreshing.remove();
        }
    }

    /** The id-keyed form, for {@code FiefsAPI}. Unknown ids are a no-op. */
    public void refreshSuccession(UUID fiefId) {
        refreshSuccession(persistentData.getFiefById(fiefId));
    }

    // ---- /fi heir ---------------------------------------------------------

    /**
     * Why this fief's holder may not name an heir, or null if they may.
     *
     * <p>The sentence is the rule's own and Fiefs never composes it, which is what lets
     * {@code /fi heir} refuse correctly without Fiefs ever naming a government type.
     */
    public String heirRefusalFor(Fief fief) {
        StandingAnswer answer = standingAnswerFor(fief);
        if (answer.holderMayNameHeir()) {
            return null;
        }
        String refusal = answer.heirRefusal();
        if (refusal == null || refusal.isBlank()) {
            if (blankRefusalLogged.compareAndSet(false, true)) {
                logger().warning("The fief succession policy from " + ownerName() + " refuses /fi heir "
                        + "but supplies no sentence saying why, so a generic one is being printed. A "
                        + "missing string never changes behaviour; the command still refuses.");
            }
            return GENERIC_HEIR_REFUSAL;
        }
        return refusal;
    }

    /**
     * Whether this holder is owed the notice that their nomination decides again, and marks them told.
     *
     * <p>{@code /fi heir} widens when a policy is stood down, which is a permission widening on
     * failure. It must widen, because the ladder now genuinely reads the nomination and refusing
     * would be the plugin lying about what decides. It must not widen quietly.
     */
    public boolean claimNominationDecidesAgainNotice(UUID holderId) {
        return policyStoodDown && holderId != null && toldNominationDecidesAgain.add(holderId);
    }

    // ---- the eligible roll ------------------------------------------------

    /**
     * Everybody who may lawfully inherit this fief, in join order, excluding one player.
     *
     * <p>The one gate. Every rule anywhere - a policy's electorate, its candidate set, its heir rung,
     * its investiture nominee - is a subset of this list, because this list is the only one that
     * crosses the seam.
     *
     * @param excluding the departing holder at a succession, the sitting holder for a standing
     *                  answer, or null to exclude nobody.
     */
    private List<UUID> eligible(Fief fief, FactionView faction, UUID excluding) {
        List<UUID> eligible = new ArrayList<>();
        // getMembers() preserves join order, so the first eligible entry is the longest-standing
        // member. Ineligible members are skipped rather than blocking: somebody who has left the
        // faction cannot inherit, but their presence in a stale list must not disinherit everyone
        // behind them.
        for (UUID member : fief.getMembers()) {
            if (member.equals(excluding)) {
                continue;
            }
            if (isEligible(faction, member)) {
                eligible.add(member);
            }
        }
        // Copied rather than wrapped, because this crosses a plugin boundary and a policy holding on
        // to a live view of an internal list is a second reader of state it does not own.
        return List.copyOf(eligible);
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

    // ---- calling a policy safely ------------------------------------------

    /** One call into a policy, for {@link #guarded}. Allowed to throw anything, including an Error. */
    private interface PolicyCall {
        FiefSuccession apply(FiefSuccessionPolicy policy);
    }

    /**
     * Asks a policy, and contains anything it throws.
     *
     * <p>{@code catch (Throwable)} and not {@code catch (Exception)}, mirroring
     * {@code GovernmentService.bound} in PatriamMFAddon. The realistic failure is a
     * {@link NoClassDefFoundError} out of a half-enabled plugin, which is an Error, and one thrown
     * from here would walk out through {@code /fi leave} or a {@code FactionMemberLeftEvent} handler
     * and cost a player their fief.
     */
    private FiefSuccession guarded(FiefSuccessionPolicy current, String what, PolicyCall call) {
        try {
            return call.apply(current);
        } catch (Throwable t) {
            String owner = ownerName();
            this.policy = null;
            this.policyOwner = null;
            this.droppedPolicy = current;
            this.policyStoodDown = true;
            this.toldNominationDecidesAgain.clear();
            standingAnswers.clear();
            logger().log(Level.SEVERE, "The fief succession policy from " + owner + " threw while "
                    + what + ", and has been stood down until the server restarts. Every fief on this "
                    + "server now passes to its holder's named heir, then to its longest-standing "
                    + "member, then back to the faction that granted it, and no fief follows its "
                    + "realm's government form.", t);
            return null;
        }
    }

    private String explanationOrGeneric(FiefSuccession answer) {
        String explanation = answer.explanation();
        if (explanation != null && !explanation.isBlank()) {
            return explanation;
        }
        if (blankExplanationLogged.compareAndSet(false, true)) {
            logger().warning("The fief succession policy from " + ownerName() + " named a successor "
                    + "but supplied no sentence explaining it, so a generic one is being printed. The "
                    + "seat is not affected: losing a player their fief over a null string is the "
                    + "wrong direction.");
        }
        return GENERIC_EXPLANATION;
    }

    private String ruleOrGeneric(FiefSuccession answer) {
        String rule = answer.rule();
        return rule == null || rule.isBlank() ? "its realm's government" : rule;
    }

    private String ownerName() {
        String owner = getSuccessionPolicyOwner();
        return owner == null ? "an unnamed plugin" : owner;
    }

    private Logger logger() {
        return plugin.getLogger();
    }

    // ---- what players are told --------------------------------------------

    /**
     * Tells the fief's remaining members what happened, and on a reversion tells the faction's head,
     * who is the only person who can then regrant it. Offline players are simply skipped; this is
     * news, not state.
     *
     * <p>One line became two: the second is the deciding rule's own sentence. The count of messages
     * is otherwise identical to what it was, and there is no case where this produces a message the
     * old code did not, or silence where the old code spoke.
     *
     * <p>Kept to the fief and, on a reversion, to the faction head. Not broadcast. A realm changing
     * hands is server news; a fief changing hands on a server with forty fiefs is not, and a
     * server-wide line per fief succession would train people to ignore the ones that matter.
     */
    private void announce(Fief fief, UUID departingHolder, Succession succession) {
        String departedName = nameOf(departingHolder);

        Component message;
        if (succession.reverted()) {
            message = Component.text(departedName + " no longer holds " + fief.getName()
                    + ", and it has reverted to " + persistentData.getFactionNameOfFief(fief) + ".",
                    NamedTextColor.AQUA);
        } else {
            String successorName = nameOf(succession.newOwnerId());
            message = Component.text(successorName + " has succeeded " + departedName + " as holder of "
                    + fief.getName() + ".", NamedTextColor.AQUA);
        }

        Component why = succession.explanation() == null ? null
                : Component.text("  " + succession.explanation(), NamedTextColor.GRAY);

        for (UUID memberId : fief.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(message);
                if (why != null) {
                    member.sendMessage(why);
                }
            }
        }

        if (succession.reverted()) {
            notifyFactionHead(fief, message);
        }
    }

    /** Told to the fief only. The rest of the realm has no business in a three-person ballot box. */
    private void announceStandingChange(Fief fief, StandingAnswer answer) {
        Component message;
        if (answer.presumptive() == null) {
            message = Component.text(fief.getName() + " now stands to revert to "
                    + persistentData.getFactionNameOfFief(fief) + ": nobody in it could inherit it.",
                    NamedTextColor.AQUA);
        } else {
            message = Component.text(fief.getName() + " now stands to pass to "
                    + nameOf(answer.presumptive()) + ".", NamedTextColor.AQUA);
        }

        Component why = answer.explanation() == null ? null
                : Component.text("  " + sentence(answer.explanation()), NamedTextColor.GRAY);

        for (UUID memberId : fief.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(message);
                if (why != null) {
                    member.sendMessage(why);
                }
            }
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

    /**
     * Turns a clause into a sentence.
     *
     * <p>The ladder's own explanations are clauses ("its longest-standing member") because they are
     * printed inline after a name on {@code /fi info}; a policy's are already sentences. One helper
     * rather than two sets of strings, so the two states cannot drift apart in punctuation alone.
     */
    public static String sentence(String clause) {
        if (clause == null || clause.isEmpty()) {
            return clause;
        }
        String capitalised = Character.toUpperCase(clause.charAt(0)) + clause.substring(1);
        return capitalised.endsWith(".") ? capitalised : capitalised + ".";
    }

    private String nameOf(UUID playerId) {
        return new UUIDChecker().findPlayerNameBasedOnUUID(playerId);
    }
}
