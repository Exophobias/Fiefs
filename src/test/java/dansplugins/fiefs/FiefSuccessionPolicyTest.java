package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import dansplugins.fiefs.externalapi.FI_Fief;
import dansplugins.fiefs.externalapi.FiefSuccession;
import dansplugins.fiefs.externalapi.FiefSuccessionPolicy;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The succession policy seam: what another plugin may decide, what it may not, and what a player can
 * see about it.
 *
 * <p>{@link FiefSuccessionTest} is the regression bar for this whole feature and is deliberately
 * untouched: it runs with no policy registered and every assertion in it must still hold. This class
 * is everything that is new, and it is written around one question rather than around the code -
 * <b>would this test fail if the behaviour were removed?</b> Two habits follow from that and both are
 * deliberate:
 *
 * <ul>
 *   <li>A policy that misbehaves is asserted against a <b>seated holder</b>, never against the
 *       absence of an exception. "Nothing was thrown" is also what a fief nobody holds looks like.</li>
 *   <li>Call counts are asserted, not just outcomes. At one eligible member every rule produces the
 *       same player, so an outcome assertion there passes whether or not the policy was consulted at
 *       all - which is exactly the shape of defect this codebase keeps producing.</li>
 * </ul>
 */
class FiefSuccessionPolicyTest {

    private static final String FACTION_ID = "faction-1";

    private ShutdownAwareServerMock server;
    private Fiefs fiefs;
    private FakeMedievalFactionsApi api;
    private PlayerMock holder;
    private PlayerMock elder;
    private PlayerMock younger;
    private PlayerMock second;
    private PlayerMock third;
    private FactionId factionId;
    private Plugin addon;
    private RecordingPolicy policy;

    private final List<LogRecord> logs = new ArrayList<>();
    private Handler logHandler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new ShutdownAwareServerMock());
        api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);

        holder = server.addPlayer("Holder");
        elder = server.addPlayer("Elder");
        younger = server.addPlayer("Younger");
        second = server.addPlayer("Second");
        third = server.addPlayer("Third");

        // The first member is the faction's recorded head, matching MF, where the founder is.
        factionId = api.createFaction(FACTION_ID, "Ashford", holder.getUniqueId(), elder.getUniqueId(),
                younger.getUniqueId(), second.getUniqueId(), third.getUniqueId());

        fiefs = MockBukkit.load(Fiefs.class);

        // Attached to the plugin's OWN logger rather than to the server's. MockBukkit's server logger
        // is not the parent of a plugin logger here, so a handler on it sees nothing this plugin
        // prints - which is exactly the shape of a test that passes because it had nothing to look at.
        // Nothing is missed by attaching after the load: both boot lines are printed later than it,
        // one from a registration and the other from ServerLoadEvent.
        logHandler = new Handler() {
            @Override public void publish(LogRecord record) {
                logs.add(record);
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        fiefs.getLogger().addHandler(logHandler);

        addon = MockBukkit.createMockPlugin("PatriamMFAddon");
        policy = new RecordingPolicy();
    }

    @AfterEach
    void tearDown() {
        if (fiefs != null) {
            fiefs.getLogger().removeHandler(logHandler);
        }
        MockBukkit.unmock();
    }

    // ---- fixtures ---------------------------------------------------------

    private Fief fiefNamed(String name) {
        return fiefs.getPersistentData().getFief(name);
    }

    /** Held by Holder, joined by Elder then Younger, so join order is unambiguous. */
    private Fief aFiefWithBothMembers() {
        holder.performCommand("fi create \"Ashford Mill\"");
        holder.performCommand("fi invite Elder");
        elder.performCommand("fi join \"Ashford Mill\"");
        holder.performCommand("fi invite Younger");
        younger.performCommand("fi join \"Ashford Mill\"");
        return fiefNamed("Ashford Mill");
    }

    /** A second fief of the same faction, so two successions can happen in one test. */
    private Fief aSecondFief() {
        second.performCommand("fi create \"Blackmoor Mill\"");
        second.performCommand("fi invite Third");
        third.performCommand("fi join \"Blackmoor Mill\"");
        return fiefNamed("Blackmoor Mill");
    }

    /**
     * Registers through {@code getAPI()}, which is what a consumer does, and NOT through the service
     * directly. See {@link #aPolicyRegisteredThroughOneApiInstanceIsVisibleToEveryOther}.
     */
    private void register() {
        fiefs.getAPI().registerSuccessionPolicy(addon, policy);
    }

    private MedievalFactionsIntegrator integrator() {
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(new Logger(fiefs));
        assertTrue(integrator.resolve(), "the fake API must be resolvable");
        return integrator;
    }

    /**
     * Everything this player has been told since the last drain, with the colour codes taken off.
     *
     * <p>MockBukkit's {@code nextMessage()} serialises a Component to LEGACY text, so every line
     * arrives with a section sign and a colour character welded to its front. Stripping them is what
     * lets a test assert on a whole line rather than on a fragment of one, and asserting on the whole
     * line is what catches a message that is subtly wrong rather than merely absent.
     */
    private List<String> drain(PlayerMock player) {
        List<String> said = new ArrayList<>();
        String next;
        while ((next = player.nextMessage()) != null) {
            said.add(next.replaceAll("§.", ""));
        }
        return said;
    }

    private void drainAll() {
        for (PlayerMock player : Arrays.asList(holder, elder, younger, second, third)) {
            drain(player);
        }
    }

    private List<String> logsAt(Level level) {
        List<String> lines = new ArrayList<>();
        for (LogRecord record : logs) {
            if (record.getLevel().equals(level)) {
                lines.add(String.valueOf(record.getMessage()));
            }
        }
        return lines;
    }

    private boolean anyLogContains(String fragment) {
        for (LogRecord record : logs) {
            if (String.valueOf(record.getMessage()).contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyContains(List<String> lines, String fragment) {
        for (String line : lines) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static String lineStartingWith(List<String> lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return null;
    }

    private static FiefSuccession seats(UUID successor) {
        return new FiefSuccession(successor, "Council, as Ashford is governed",
                "Ashford is a Council, and its elders had chosen them from among themselves: 2 of 3 "
                        + "standing votes.", false,
                "Ashford is a Council, so the elders of Ashford Mill choose who inherits it, not you.");
    }

    private static FiefSuccession stands(UUID successor) {
        return new FiefSuccession(successor, "Council, as Ashford is governed",
                "Ashford is a Council, and its elders have chosen them from among themselves: 2 of 3 "
                        + "standing votes.", false,
                "Ashford is a Council, so the elders of Ashford Mill choose who inherits it, not you.");
    }

    // ---- what a policy may decide -----------------------------------------

    @Test
    void aPolicysChoiceIsSeatedAndItsOwnSentenceIsPrinted() {
        aFiefWithBothMembers();
        policy.decideAnswer = eligible -> seats(younger.getUniqueId());
        register();
        drainAll();

        holder.performCommand("fi leave");

        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "a policy may reorder the ladder, and here it passed over the senior member");
        assertTrue(anyContains(drain(elder), "Ashford is a Council, and its elders had chosen them"),
                "the deciding rule's own sentence is what the fief is told, never one Fiefs composed");
        assertTrue(anyContains(drain(holder), "Ashford is a Council, and its elders had chosen them"),
                "and the departing holder is told what took their fief, not merely who has it");
    }

    /**
     * The regression this whole guard exists for. A policy that names somebody who may not inherit is
     * a bug in the other plugin, and the fief must still end up correctly held rather than in the
     * hands of a stranger.
     */
    @Test
    void aSuccessorOutsideTheEligibleRollIsDiscardedAndTheLadderAnswers() {
        aFiefWithBothMembers();
        UUID stranger = UUID.randomUUID();
        policy.decideAnswer = eligible -> seats(stranger);
        register();

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "an outsider must not be seated; the ladder answers instead");
        assertTrue(anyLogContains("Ashford Mill"), "the warning has to name the fief it is about");
        assertTrue(anyContains(logsAt(Level.WARNING), "Ignoring it"),
                "a governing policy naming an ineligible player is not a normal outcome and must be said out loud");
    }

    /** A policy cannot reinstate the holder who just left, which would make leaving impossible. */
    @Test
    void namingTheDepartingHolderIsDiscardedAndTheLadderAnswers() {
        aFiefWithBothMembers();
        policy.decideAnswer = eligible -> seats(holder.getUniqueId());
        register();

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID());
        assertFalse(fiefNamed("Ashford Mill").isMember(holder.getUniqueId()),
                "the departing holder is out of the fief, and no policy can put them back in it");
    }

    @Test
    void deferringIsSilentAndTheLadderAnswers() {
        aFiefWithBothMembers();
        policy.decideAnswer = eligible -> null;
        register();

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID());
        assertEquals(1, policy.decideCalls);
        assertFalse(anyLogContains("Ignoring it"),
                "deferring is the normal answer and must not be reported as a fault");
    }

    /**
     * A missing sentence costs a sentence, never a seat. The opposite reading - treat a blank
     * explanation as a malformed answer and discard it - would take a fief away from the player a
     * working rule had just chosen, over a null string.
     */
    @Test
    void aBlankExplanationLosesNobodyTheirFief() {
        aFiefWithBothMembers();
        policy.decideAnswer = eligible -> new FiefSuccession(younger.getUniqueId(), "Council", "   ",
                false, "not you.");
        register();
        drainAll();

        holder.performCommand("fi leave");

        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "the seat stands even though the words did not");
        assertTrue(anyContains(drain(elder), "Its realm's government decided it."),
                "a generic sentence is printed rather than none at all");
        assertTrue(anyContains(logsAt(Level.WARNING), "no sentence explaining it"));
    }

    // ---- the size of the roll ---------------------------------------------

    /**
     * The most common fief succession on the server, and the one that never touches the government
     * layer. Asserted on the CALL COUNT as well as on the outcome: at zero eligible members every rule
     * produces the same reversion, so an outcome-only assertion would pass whether or not the policy
     * had been asked a question it could not answer.
     */
    @Test
    void nobodyEligibleRevertsWithoutConsultingThePolicyAtAll() {
        elder.performCommand("fi create \"Elder Mill\"");
        register();
        drainAll();

        elder.performCommand("fi leave");

        Fief fief = fiefNamed("Elder Mill");
        assertNotNull(fief, "reversion must not destroy the fief");
        assertTrue(fief.isVacant());
        assertEquals(0, policy.decideCalls,
                "a policy is never asked a question with no possible answer, and cannot turn a "
                        + "reversion into something else");

        List<String> head = drain(holder);
        assertTrue(anyContains(head, "has reverted to Ashford"), "today's message, unchanged");
        assertTrue(anyContains(head, "/fi grant"), "today's hint to the head, unchanged");
        assertFalse(anyContains(drain(elder), "  "),
                "a reversion quotes no rule, so it gains no second line");
    }

    /**
     * The deliberate asymmetry between the two seam methods, and the reason it exists: whether a
     * holder may name an heir is a property of the FORM, not of the roll, so a fief must not silently
     * regain {@code /fi heir} merely because everybody else left the faction.
     */
    @Test
    void anEmptyRollIsStillPutToTheStandingQuestionEvenThoughItIsNeverPutToTheSuccession() {
        elder.performCommand("fi create \"Elder Mill\"");
        register();
        drainAll();

        elder.performCommand("fi succession");

        assertEquals(1, policy.standingCalls);
        assertEquals(List.of(), policy.standingRolls.get(0), "and it is told the roll is empty");

        elder.performCommand("fi leave");
        assertEquals(0, policy.decideCalls);
        assertTrue(fiefNamed("Elder Mill").isVacant());
    }

    /**
     * At one eligible member the policy cannot change the answer, and it is consulted anyway so that
     * the sentence can name the form. Both halves are asserted, because the outcome alone is
     * satisfied by not asking at all.
     */
    @Test
    void oneEligibleMemberIsSeatedWhateverThePolicyAnswers() {
        holder.performCommand("fi create \"Ashford Mill\"");
        holder.performCommand("fi invite Elder");
        elder.performCommand("fi join \"Ashford Mill\"");
        policy.decideAnswer = eligible -> seats(younger.getUniqueId());
        register();

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "validation forces the only possible outcome");
        assertEquals(1, policy.decideCalls,
                "and it is asked all the same, so a fief of two learns it follows its realm's form");
    }

    // ---- the eligible roll ------------------------------------------------

    /**
     * THE HARD CONSTRAINT, now that a second plugin can answer. The roll is built here, live against
     * Medieval Factions, and it is the only list a policy ever sees - so a policy cannot reach the
     * stale member list this class was written to survive.
     */
    @Test
    void thePolicyNeverSeesSomebodyWhoHasLeftTheParentFaction() {
        Fief fief = aFiefWithBothMembers();
        api.removeFactionMember(FACTION_ID, younger.getUniqueId());
        assertTrue(fief.isMember(younger.getUniqueId()), "precondition: the fief list is stale");
        register();

        holder.performCommand("fi leave");

        assertEquals(List.of(elder.getUniqueId()), policy.decideRolls.get(0),
                "checked against Medieval Factions at the moment of succession, not against the cache");
        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID());
    }

    @Test
    void theEligibleRollIsInJoinOrderSoTheFirstEntryIsTheLongestStanding() {
        aFiefWithBothMembers();
        register();

        holder.performCommand("fi leave");

        assertEquals(List.of(elder.getUniqueId(), younger.getUniqueId()), policy.decideRolls.get(0));
    }

    // ---- failure containment ----------------------------------------------

    /**
     * The failure the {@code catch (Throwable)} exists for: an Error, not an Exception, out of a
     * half-enabled plugin. Asserted on <b>two seated holders</b>, because "no exception escaped" is
     * also what losing a fief looks like.
     */
    @Test
    void aPolicyThatThrowsCostsNobodyTheirFiefAndIsNeverAskedTwice() {
        aFiefWithBothMembers();
        aSecondFief();
        policy.throwOnDecide = true;
        register();
        drainAll();

        holder.performCommand("fi leave");
        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "the ladder answered in the same tick, so nobody lost a fief");

        second.performCommand("fi leave");
        assertEquals(third.getUniqueId(), fiefNamed("Blackmoor Mill").getOwnerUUID());

        assertEquals(1, policy.decideCalls,
                "a policy that threw is stood down for the session rather than asked on every departure");
        List<String> severe = logsAt(Level.SEVERE);
        assertEquals(1, severe.size(), "logged once, not once per fief");
        assertTrue(severe.get(0).contains("PatriamMFAddon"), "and it names the plugin that owns the fault");
        assertNull(fiefs.getAPI().getSuccessionPolicyOwner(),
                "a stood-down policy is not in force, and the self-check on the other side must see that");
    }

    @Test
    void aPolicyIsDroppedWhenThePluginThatRegisteredItIsDisabled() {
        register();
        assertEquals("PatriamMFAddon", fiefs.getAPI().getSuccessionPolicyOwner());

        server.getPluginManager().callEvent(new PluginDisableEvent(addon));

        assertNull(fiefs.getAPI().getSuccessionPolicyOwner());
        aFiefWithBothMembers();
        holder.performCommand("fi leave");
        assertEquals(0, policy.decideCalls + policy.standingCalls,
                "a policy left registered by a plugin that is no longer running must not be consulted");
        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID());
    }

    /**
     * The same disable, mid-session, is the one an operator has to be told about: the government
     * layer has stopped answering on a server that is still taking players, and nothing else on the
     * server will say so.
     */
    @Test
    void aMidSessionDisableOfTheOwningPluginIsAnAlarm() {
        register();

        // The server is up and taking players, which is what makes this worth a warning.
        server.getPluginManager().callEvent(new PluginDisableEvent(addon));

        List<String> warnings = logsAt(Level.WARNING);
        assertEquals(1, warnings.size(), "said once, and said loudly");
        assertTrue(warnings.get(0).contains("PatriamMFAddon"),
                "and it names the plugin whose layer has gone, not just the fact that one has");
        assertTrue(warnings.get(0).contains("no fief follows its realm's government form"),
                "and it says what that now costs, because the ladder answering is not visibly wrong");
    }

    /**
     * The same handler on a clean shutdown, which is the overwhelmingly common case and is not a
     * fault at all. Bukkit disables the plugin that registered the policy BEFORE it disables Fiefs,
     * so without this the last line of every single healthy run was a warning that no fief follows
     * its realm's government form - and an alarm that fires on every healthy run is one nobody reads
     * by the time a real one arrives.
     *
     * <p>The drop itself is still asserted here. Silence must come from the log level and not from
     * skipping the work, or a policy belonging to a plugin that has gone would stay registered.
     */
    @Test
    void aCleanShutdownDropsThePolicyAndSaysNothingAlarming() {
        register();
        server.stopping = true;

        server.getPluginManager().callEvent(new PluginDisableEvent(addon));

        assertNull(fiefs.getAPI().getSuccessionPolicyOwner(),
                "the drop is unconditional: only the alarm depends on which moment this is");
        assertTrue(logsAt(Level.WARNING).isEmpty(),
                "a healthy shutdown must not end on a warning");
        assertFalse(anyLogContains("no longer in force"),
                "at no level, either: a line about the layer going away is only news mid-session");
    }

    // ---- registration -----------------------------------------------------

    /**
     * The trap the seam was shaped around. {@code Fiefs.getAPI()} mints a NEW wrapper on every call,
     * so a policy held as a field on that wrapper would be registered on one object and invisible to
     * the one the ServicesManager published - and the symptom would be a government layer that
     * reports itself installed and decides nothing.
     */
    @Test
    void aPolicyRegisteredThroughOneApiInstanceIsVisibleToEveryOther() {
        FiefsAPI registeredThrough = fiefs.getAPI();
        FiefsAPI somebodyElses = fiefs.getAPI();
        FiefsAPI fromTheServicesManager = server.getServicesManager().load(FiefsAPI.class);
        assertNotNull(fromTheServicesManager);
        assertNotEquals(registeredThrough, somebodyElses, "precondition: these really are different objects");

        registeredThrough.registerSuccessionPolicy(addon, policy);

        assertEquals("PatriamMFAddon", somebodyElses.getSuccessionPolicyOwner());
        assertEquals("PatriamMFAddon", fromTheServicesManager.getSuccessionPolicyOwner());
    }

    @Test
    void theRegisteredOwnerIsNamedAndIsNullWhenNothingIsRegistered() {
        assertNull(fiefs.getAPI().getSuccessionPolicyOwner());
        register();
        assertEquals("PatriamMFAddon", fiefs.getAPI().getSuccessionPolicyOwner());
        fiefs.getAPI().unregisterSuccessionPolicy(policy);
        assertNull(fiefs.getAPI().getSuccessionPolicyOwner());
    }

    /**
     * A boot log with NEITHER line is itself the alarm, so both directions are pinned. The line is
     * printed from inside the registration, naming the plugin handed in, so it cannot be printed by a
     * plugin that believes it registered and did not.
     */
    @Test
    void bootSaysTheLadderIsInForceWhenNothingRegistered() {
        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        assertTrue(anyLogContains("No fief succession policy is registered"));
        assertFalse(anyLogContains("now decides fief succession"));
    }

    @Test
    void bootSaysTheGovernmentLayerIsInForceWhenSomethingRegistered() {
        register();
        server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));

        assertTrue(anyLogContains("PatriamMFAddon now decides fief succession"));
        assertFalse(anyLogContains("No fief succession policy is registered"),
                "exactly one of the two lines, or an operator cannot read the state off the log");
    }

    // ---- /fi heir ---------------------------------------------------------

    @Test
    void heirIsRefusedWithTheRulesOwnSentenceAndFiefsNamesNoGovernmentType() {
        aFiefWithBothMembers();
        policy.standingAnswer = eligible -> stands(elder.getUniqueId());
        register();
        drainAll();

        assertFalse(holder.performCommand("fi heir Younger"));

        assertNull(fiefNamed("Ashford Mill").getHeirUUID(), "the nomination must not be recorded either");
        assertTrue(drain(holder).contains(
                "Ashford is a Council, so the elders of Ashford Mill choose who inherits it, not you."),
                "printed verbatim, so it can never drift out of step with the rule that caused it");
    }

    @Test
    void aRefusalWithNoSentenceStillRefuses() {
        aFiefWithBothMembers();
        policy.standingAnswer = eligible ->
                new FiefSuccession(elder.getUniqueId(), "Council", "its elders have chosen them.", false, null);
        register();
        drainAll();

        assertFalse(holder.performCommand("fi heir Younger"));

        assertNull(fiefNamed("Ashford Mill").getHeirUUID(),
                "a missing string never changes behaviour");
        assertTrue(anyContains(drain(holder), "decides who inherits this fief, not you"));
        assertTrue(anyContains(logsAt(Level.WARNING), "supplies no sentence saying why"));
    }

    /**
     * A permission that widens on failure. It must widen, because the ladder genuinely reads the
     * nomination again and refusing would be the plugin lying about what decides - and it must not
     * widen quietly, which is what this pins.
     */
    @Test
    void heirWidensWhenAPolicyIsStoodDownAndSaysSoWhenItDoes() {
        aFiefWithBothMembers();
        policy.standingAnswer = eligible -> stands(elder.getUniqueId());
        register();
        assertFalse(holder.performCommand("fi heir Younger"), "precondition: refused while the rule stands");
        drainAll();

        policy.throwOnStanding = true;
        assertTrue(holder.performCommand("fi heir Younger"));

        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getHeirUUID(),
                "the nomination decides again, so it has to be recordable again");
        assertTrue(anyContains(drain(holder), "so your nomination decides again"),
                "widening silently would leave the holder believing the constitution had changed");
    }

    // ---- the readouts -----------------------------------------------------

    /**
     * The anti-silence surface. Two servers, one with a government layer and one without, print
     * visibly different text on a command players type constantly. Without that, "the old rule ran"
     * and "the new rule happened to agree" are indistinguishable.
     */
    @Test
    void infoAlwaysPrintsASuccessionLineAndTheTwoServersDoNotAgree() {
        aFiefWithBothMembers();
        drainAll();

        holder.performCommand("fi info \"Ashford Mill\"");
        String ladderLine = lineStartingWith(drain(holder), "Succession: ");
        assertEquals("Succession: Elder, its longest-standing member.", ladderLine);

        policy.standingAnswer = eligible -> stands(younger.getUniqueId());
        register();
        drainAll();

        holder.performCommand("fi info \"Ashford Mill\"");
        String policyLine = lineStartingWith(drain(holder), "Succession: ");
        assertNotNull(policyLine);
        assertNotEquals(ladderLine, policyLine);
        assertTrue(policyLine.contains("Younger"));
        assertTrue(policyLine.contains("Ashford is a Council"),
                "the sentence is the rule's, so a layer that stopped deciding is visible in the text");
    }

    @Test
    void infoNamesTheNominationThatStandsButDecidesNothing() {
        aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");
        // The realm changes form afterwards, so the nomination is residue from a time when it counted.
        policy.standingAnswer = eligible -> stands(elder.getUniqueId());
        register();
        drainAll();

        holder.performCommand("fi info \"Ashford Mill\"");

        List<String> said = drain(holder);
        assertTrue(anyContains(said, "A nomination for Younger stands"));
        assertTrue(anyContains(said, "It decides nothing now."));
        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getHeirUUID(),
                "ignored, never cleared: clearing is destructive across a temporary change of form");
    }

    @Test
    void infoSaysSoWhenNobodyCouldInheritAtAll() {
        holder.performCommand("fi create \"Ashford Mill\"");
        drainAll();

        holder.performCommand("fi info \"Ashford Mill\"");

        assertEquals("Succession: nobody, so it would revert to Ashford.",
                lineStartingWith(drain(holder), "Succession: "));
    }

    @Test
    void successionSaysInWordsThatNoGovernmentLayerIsInstalled() {
        aFiefWithBothMembers();
        drainAll();

        assertTrue(holder.performCommand("fi succession"));

        List<String> said = drain(holder);
        assertTrue(said.contains("=== Succession of Ashford Mill ==="));
        assertTrue(said.contains("Rule: its holder's named heir, then its longest-standing member, "
                + "then back to Ashford."));
        assertTrue(said.contains("Stands to pass to: Elder, its longest-standing member."));
        assertTrue(said.contains("No government layer is installed, so a fief does not follow its "
                + "realm's form."), "a readout that could not report its own absence would be worthless");
    }

    @Test
    void successionNamesTheRuleWhenAGovernmentLayerIsAnswering() {
        aFiefWithBothMembers();
        policy.standingAnswer = eligible -> stands(younger.getUniqueId());
        register();
        drainAll();

        assertTrue(holder.performCommand("fi succession"));

        List<String> said = drain(holder);
        assertTrue(said.contains("Rule: Council, as Ashford is governed."));
        assertTrue(said.contains("Stands to pass to: Younger."));
        assertFalse(anyContains(said, "No government layer is installed"));
    }

    @Test
    void successionSaysTheLayerFailedRatherThanSayingNothing() {
        aFiefWithBothMembers();
        policy.throwOnStanding = true;
        register();
        drainAll();

        assertTrue(holder.performCommand("fi succession"));

        List<String> said = drain(holder);
        assertTrue(anyContains(said, "Fiefs' government layer failed and has been stood down"));
        assertTrue(said.contains("Stands to pass to: Elder, its longest-standing member."),
                "and it still answers the question, from the ladder");
        assertFalse(anyContains(said, "No government layer is installed"),
                "a layer that failed is a different state from one that was never installed");
    }

    @Test
    void successionIsNotReadableByJustAnybody() {
        aFiefWithBothMembers();
        PlayerMock outsider = server.addPlayer("Outsider");
        api.createFaction("faction-2", "Blackmoor", outsider.getUniqueId());
        drainAll();

        assertFalse(outsider.performCommand("fi succession \"Ashford Mill\""));
        assertTrue(anyContains(drain(outsider), "may read its succession"));
    }

    // ---- the standing answer moving ---------------------------------------

    @Test
    void theFiefIsToldOnlyWhenTheStandingAnswerActuallyMoves() {
        holder.performCommand("fi create \"Ashford Mill\"");
        holder.performCommand("fi invite Elder");
        drainAll();

        elder.performCommand("fi join \"Ashford Mill\"");
        assertFalse(anyContains(drain(holder), "now stands to"),
                "the first answer for a fief seeds the cache silently, so a restart cannot produce a "
                        + "line about a change nobody made");

        holder.performCommand("fi invite Younger");
        younger.performCommand("fi join \"Ashford Mill\"");
        assertFalse(anyContains(drain(holder), "now stands to"),
                "Elder is still the longest-standing member, so nothing moved and nothing is said");

        holder.performCommand("fi heir Younger");
        assertTrue(drain(holder).contains("Ashford Mill now stands to pass to Younger."),
                "the answer moved, and this is the political moment the whole feature is for");

        holder.performCommand("fi heir clear");
        assertTrue(drain(holder).contains("Ashford Mill now stands to pass to Elder."));
    }

    @Test
    void aMemberLeavingTheFiefMovesTheStandingAnswerAndTheFiefHearsIt() {
        aFiefWithBothMembers();
        drainAll();

        elder.performCommand("fi leave");

        assertTrue(drain(holder).contains("Ashford Mill now stands to pass to Younger."));
    }

    /**
     * Nobody in the fief did anything and its own member list is still stale, but the person who
     * would have inherited has walked out of the realm and can no longer take it. This is the change
     * a fief has no other way of noticing.
     */
    @Test
    void aMemberLeavingTheFactionMovesTheStandingAnswerAndTheFiefHearsIt() {
        aFiefWithBothMembers();
        drainAll();

        api.removeFactionMember(FACTION_ID, elder.getUniqueId());
        server.getPluginManager().callEvent(new FactionMemberLeftEvent(factionId, elder.getUniqueId()));

        assertTrue(drain(holder).contains("Ashford Mill now stands to pass to Younger."));
    }

    @Test
    void aMemberJoiningCanMoveTheStandingAnswerAndTheFiefHearsIt() {
        holder.performCommand("fi create \"Ashford Mill\"");
        holder.performCommand("fi invite Elder");
        elder.performCommand("fi join \"Ashford Mill\"");
        drainAll();

        // Elder is the standing answer and is now seeded. Kicking them moves it to nobody.
        holder.performCommand("fi kick Elder");

        assertTrue(drain(holder).contains(
                "Ashford Mill now stands to revert to Ashford: nobody in it could inherit it."));
    }

    // ---- persistence ------------------------------------------------------

    /**
     * Pins the "zero new persisted state" claim by asserting the exact key set, so a later change
     * cannot quietly add a field and leave Fiefs carrying another plugin's state. A save file holding
     * ballots that only mean something when a second plugin is installed is a data dependency in the
     * wrong direction, and Fiefs must keep running correctly alone.
     */
    @Test
    void thisFeatureAddedNothingToWhatAFiefSaves() {
        Fief fief = aFiefWithBothMembers();

        assertEquals(new TreeSet<>(Set.of("id", "name", "description", "ownerUUID", "heirUUID",
                        "factionId", "members", "capitalWorld", "capitalX", "capitalZ", "heldSince",
                        "integerFlagValues", "booleanFlagValues", "doubleFlagValues", "stringFlagValues")),
                new TreeSet<>(fief.save().keySet()),
                "fiefs.json is unchanged by the succession seam: no migration, no new absent-key handling");
    }

    @Test
    void aFiefWrittenBeforeThisFeatureLoadsAndBehavesIdentically() {
        Fief original = aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        Map<String, String> saved = original.save();
        Fief reloaded = new Fief(saved, integrator(), new Logger(fiefs));

        assertEquals(holder.getUniqueId(), reloaded.getOwnerUUID());
        assertEquals(younger.getUniqueId(), reloaded.getHeirUUID());
        assertEquals(original.getMembers(), reloaded.getMembers());
        assertEquals(original.getId(), reloaded.getId(), "and it is still the same fief");
    }

    @Test
    void everyFiefIdIsPublishedSoAConsumerCanPruneItsOwnRows() {
        Fief ashford = aFiefWithBothMembers();
        Fief blackmoor = aSecondFief();

        assertEquals(Set.of(ashford.getId(), blackmoor.getId()), fiefs.getAPI().getFiefIds());
    }

    // ---- the fixture policy -----------------------------------------------

    /**
     * A policy that records what it was asked and answers whatever the test told it to.
     *
     * <p>It throws {@link NoClassDefFoundError} rather than a {@link RuntimeException}, deliberately.
     * That is the realistic failure - a plugin compiled against a class the installed jar no longer
     * has - and it is an {@link Error}, so a {@code catch (Exception)} would not hold it. A fixture
     * that threw a RuntimeException would pass against a guard too narrow to survive the real one.
     */
    private static final class RecordingPolicy implements FiefSuccessionPolicy {
        private final List<List<UUID>> decideRolls = new ArrayList<>();
        private final List<List<UUID>> standingRolls = new ArrayList<>();
        private int decideCalls;
        private int standingCalls;
        private boolean throwOnDecide;
        private boolean throwOnStanding;
        private Function<List<UUID>, FiefSuccession> decideAnswer = eligible -> null;
        private Function<List<UUID>, FiefSuccession> standingAnswer = eligible -> null;

        @Override
        public FiefSuccession decide(FI_Fief fief, UUID departingHolder, List<UUID> eligible) {
            decideCalls++;
            decideRolls.add(List.copyOf(eligible));
            if (throwOnDecide) {
                throw new NoClassDefFoundError("a government plugin that half-enabled");
            }
            return decideAnswer.apply(eligible);
        }

        @Override
        public FiefSuccession standingFor(FI_Fief fief, List<UUID> eligible) {
            standingCalls++;
            standingRolls.add(List.copyOf(eligible));
            if (throwOnStanding) {
                throw new NoClassDefFoundError("a government plugin that half-enabled");
            }
            return standingAnswer.apply(eligible);
        }
    }

    /**
     * A server that will say whether it is stopping, which MockBukkit's own {@link ServerMock} will
     * not: its {@code isStopping()} throws {@code UnimplementedOperationException}.
     *
     * <p>Overriding the server rather than handing a flag to the service is what keeps the two
     * shutdown tests on the path the real server takes - a real {@link PluginDisableEvent}, through
     * the real plugin manager, into the registered listener - which is the only path on which the
     * ordering that caused the defect exists at all.
     */
    private static final class ShutdownAwareServerMock extends ServerMock {
        private volatile boolean stopping;

        @Override
        public boolean isStopping() {
            return stopping;
        }
    }
}
