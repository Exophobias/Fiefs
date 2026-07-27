package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Chunk;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Succession for fief holders: heir, then longest-standing member, then reversion to the faction the
 * fief is held from. Plus the patronage that reversion implies, since a fief nobody can be given is a
 * dead end rather than a state.
 */
class FiefSuccessionTest {

    private static final String FACTION_ID = "faction-1";

    private ServerMock server;
    private Fiefs fiefs;
    private FakeMedievalFactionsApi api;
    private PlayerMock holder;
    private PlayerMock elder;
    private PlayerMock younger;
    private FactionId factionId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);

        holder = server.addPlayer("Holder");
        elder = server.addPlayer("Elder");
        younger = server.addPlayer("Younger");

        // The first member is the faction's recorded head, matching MF, where the founder is.
        factionId = api.createFaction(FACTION_ID, "Ashford",
                holder.getUniqueId(), elder.getUniqueId(), younger.getUniqueId());
        api.setPower(holder.getUniqueId(), 10.0);
        api.setPower(elder.getUniqueId(), 5.0);
        api.setPower(younger.getUniqueId(), 3.0);

        fiefs = MockBukkit.load(Fiefs.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Fief fiefNamed(String name) {
        return fiefs.getPersistentData().getFief(name);
    }

    /**
     * A fief held by Holder, joined by Elder and then Younger, so that join order is unambiguous and
     * the longest-standing member is Elder.
     */
    private Fief aFiefWithBothMembers() {
        holder.performCommand("fi create \"Ashford Mill\"");
        holder.performCommand("fi invite Elder");
        elder.performCommand("fi join \"Ashford Mill\"");
        holder.performCommand("fi invite Younger");
        younger.performCommand("fi join \"Ashford Mill\"");
        return fiefNamed("Ashford Mill");
    }

    // --- the succession order ---

    @Test
    void aNamedHeirInheritsAheadOfTheLongestStandingMember() {
        aFiefWithBothMembers();
        assertTrue(holder.performCommand("fi heir Younger"));

        holder.performCommand("fi leave");

        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "the named heir takes precedence over seniority");
    }

    @Test
    void theLongestStandingMemberInheritsWhenNoHeirIsNamed() {
        aFiefWithBothMembers();

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "Elder joined first, so Elder inherits");
    }

    /**
     * THE HARD CONSTRAINT. A fief is held FROM a faction, so somebody who has left that faction cannot
     * inherit it however recently they were named heir. The nomination is not an error; it is simply
     * overtaken, exactly as a stale vassal designation is in Medieval Factions.
     */
    @Test
    void anHeirWhoHasLeftTheParentFactionCannotInherit() {
        Fief fief = aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        // Younger walks out of the faction, and Medieval Factions is the authority on that. The fief's
        // own member list is deliberately left stale here: that is the state a restart leaves behind
        // when the departure event was never delivered, and it is exactly what the check must survive.
        api.removeFactionMember(FACTION_ID, younger.getUniqueId());
        assertTrue(fief.isMember(younger.getUniqueId()), "precondition: the fief list is stale");

        holder.performCommand("fi leave");

        assertEquals(elder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "an heir outside the parent faction must be passed over, not inherit");
    }

    /** The same bar applies to the seniority rule, not just to the heir. */
    @Test
    void aMemberWhoHasLeftTheParentFactionIsPassedOverForSeniority() {
        Fief fief = aFiefWithBothMembers();
        api.removeFactionMember(FACTION_ID, elder.getUniqueId());
        assertTrue(fief.isMember(elder.getUniqueId()), "precondition: the fief list is stale");

        holder.performCommand("fi leave");

        assertEquals(younger.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID(),
                "the senior member is outside the faction, so the next eligible one inherits");
    }

    @Test
    void aFiefWithNobodyLeftRevertsToItsFaction() {
        holder.performCommand("fi create \"Ashford Mill\"");

        holder.performCommand("fi leave");

        Fief fief = fiefNamed("Ashford Mill");
        assertNotNull(fief, "reversion must not destroy the fief");
        assertTrue(fief.isVacant(), "with nobody to inherit, the faction holds it");
        assertEquals(FACTION_ID, fief.getFactionId(), "it is still held from the same faction");
    }

    /**
     * Upstream disbanded a fief the moment its holder ran /fi leave, which destroyed its land and its
     * name because one player walked away. Reversion replaces that, and the land has to survive it.
     */
    @Test
    void aRevertedFiefKeepsItsLand() {
        holder.performCommand("fi create \"Ashford Mill\"");
        Chunk chunk = holder.getLocation().getChunk();
        api.setFactionClaim(chunk, factionId);
        holder.performCommand("fi claim");
        assertEquals(1, fiefs.getPersistentData().getNumChunks());

        holder.performCommand("fi leave");

        assertEquals(1, fiefs.getPersistentData().getNumChunks(),
                "a holder leaving must not unclaim the fief's land");
    }

    /** Leaving the FACTION is a departure too, and the fief cannot follow somebody out of it. */
    @Test
    void leavingTheFactionPassesTheFiefOn() {
        aFiefWithBothMembers();

        api.removeFactionMember(FACTION_ID, holder.getUniqueId());
        server.getPluginManager().callEvent(new FactionMemberLeftEvent(factionId, holder.getUniqueId()));

        Fief fief = fiefNamed("Ashford Mill");
        assertEquals(elder.getUniqueId(), fief.getOwnerUUID());
        assertFalse(fief.isMember(holder.getUniqueId()), "the departed holder is no longer of the fief");
    }

    @Test
    void aNonHolderLeavingTheFactionStillJustLosesTheirMembership() {
        aFiefWithBothMembers();

        api.removeFactionMember(FACTION_ID, elder.getUniqueId());
        server.getPluginManager().callEvent(new FactionMemberLeftEvent(factionId, elder.getUniqueId()));

        Fief fief = fiefNamed("Ashford Mill");
        assertEquals(holder.getUniqueId(), fief.getOwnerUUID(), "the holder is untouched");
        assertFalse(fief.isMember(elder.getUniqueId()));
    }

    // --- the heir nomination ---

    @Test
    void succeedingClearsTheHeirNomination() {
        aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        holder.performCommand("fi leave");

        assertNull(fiefNamed("Ashford Mill").getHeirUUID(),
                "the nomination belongs to the holder who made it, not to the seat");
    }

    @Test
    void transferringClearsTheHeirNomination() {
        aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        holder.performCommand("fi transfer Elder");

        Fief fief = fiefNamed("Ashford Mill");
        assertEquals(elder.getUniqueId(), fief.getOwnerUUID());
        assertNull(fief.getHeirUUID());
    }

    @Test
    void kickingTheHeirWithdrawsTheNomination() {
        aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        holder.performCommand("fi kick Younger");

        assertNull(fiefNamed("Ashford Mill").getHeirUUID(),
                "somebody who is no longer of the fief must not still be its heir");
    }

    @Test
    void onlyTheHolderMayNameAnHeir() {
        aFiefWithBothMembers();

        assertFalse(elder.performCommand("fi heir Younger"));
        assertNull(fiefNamed("Ashford Mill").getHeirUUID());
    }

    @Test
    void anHeirMustBeAMemberOfTheFief() {
        holder.performCommand("fi create \"Ashford Mill\"");

        // Elder is in the faction but not in the fief.
        assertFalse(holder.performCommand("fi heir Elder"));
        assertNull(fiefNamed("Ashford Mill").getHeirUUID());
    }

    @Test
    void heirClearWithdrawsTheNomination() {
        aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        assertTrue(holder.performCommand("fi heir clear"));

        assertNull(fiefNamed("Ashford Mill").getHeirUUID());
    }

    // --- patronage: the way out of a reversion ---

    @Test
    void theHeadOfTheFactionMayGrantAVacantFief() {
        // Elder holds a fief of a faction whose head is Holder, then departs alone.
        elder.performCommand("fi create \"Elder Mill\"");
        elder.performCommand("fi leave");
        assertTrue(fiefNamed("Elder Mill").isVacant(), "precondition: it reverted");

        assertTrue(holder.performCommand("fi grant \"Elder Mill\" Younger"));

        Fief fief = fiefNamed("Elder Mill");
        assertEquals(younger.getUniqueId(), fief.getOwnerUUID());
        assertTrue(fief.isMember(younger.getUniqueId()), "the new holder is of the fief");
    }

    @Test
    void onlyTheHeadOfTheFactionMayGrantAFief() {
        elder.performCommand("fi create \"Elder Mill\"");
        elder.performCommand("fi leave");

        assertFalse(younger.performCommand("fi grant \"Elder Mill\" Younger"),
                "Younger is an ordinary member, not the head of Ashford");
        assertTrue(fiefNamed("Elder Mill").isVacant());
    }

    @Test
    void aFiefMayNotBeGrantedToSomebodyOutsideTheFaction() {
        elder.performCommand("fi create \"Elder Mill\"");
        elder.performCommand("fi leave");
        PlayerMock outsider = server.addPlayer("Outsider");
        api.createFaction("faction-2", "Blackmoor", outsider.getUniqueId());

        assertFalse(holder.performCommand("fi grant \"Elder Mill\" Outsider"));
        assertTrue(fiefNamed("Elder Mill").isVacant());
    }

    @Test
    void grantingRemovesTheFiefFromItsPreviousHolder() {
        aFiefWithBothMembers();

        assertTrue(holder.performCommand("fi grant \"Ashford Mill\" Elder"));

        Fief fief = fiefNamed("Ashford Mill");
        assertEquals(elder.getUniqueId(), fief.getOwnerUUID());
        assertTrue(fief.isMember(holder.getUniqueId()),
                "the former holder keeps their membership; only the fief has moved");
    }

    @Test
    void revokingLeavesTheFiefStandingWithItsMembersAndLand() {
        aFiefWithBothMembers();
        Chunk chunk = holder.getLocation().getChunk();
        api.setFactionClaim(chunk, factionId);
        holder.performCommand("fi claim");

        // Holder is both the fief's holder and the faction's head here, which is the ordinary case for
        // the founder of a faction taking back a fief they granted.
        assertTrue(holder.performCommand("fi revoke \"Ashford Mill\""));

        Fief fief = fiefNamed("Ashford Mill");
        assertNotNull(fief, "revoking is not disbanding");
        assertTrue(fief.isVacant());
        assertEquals(3, fief.getNumMembers(), "members are untouched");
        assertEquals(1, fiefs.getPersistentData().getNumChunksClaimedByFief(fief), "land is untouched");
    }

    @Test
    void onlyTheHeadOfTheFactionMayRevokeAFief() {
        aFiefWithBothMembers();

        assertFalse(elder.performCommand("fi revoke \"Ashford Mill\""));
        assertEquals(holder.getUniqueId(), fiefNamed("Ashford Mill").getOwnerUUID());
    }

    /**
     * A vacant fief must not be quietly editable by whoever happens to be standing in it. Every
     * holder-only command routes through the same null-safe check, so one of them stands for the set.
     */
    @Test
    void aVacantFiefHasNoHolderPrivileges() {
        elder.performCommand("fi create \"Elder Mill\"");
        elder.performCommand("fi invite Younger");
        younger.performCommand("fi join \"Elder Mill\"");
        elder.performCommand("fi leave");
        // Younger inherits, then is revoked, leaving the fief vacant with Younger still a member.
        holder.performCommand("fi revoke \"Elder Mill\"");
        assertTrue(fiefNamed("Elder Mill").isVacant(), "precondition");

        assertFalse(younger.performCommand("fi rename \"Younger Mill\""));
        assertNotNull(fiefNamed("Elder Mill"), "a member of a vacant fief cannot act as its holder");
    }

    // --- power ---

    /**
     * D30's explicit instruction: add NOTHING to power, and verify it is not already double counted.
     *
     * <p>Fief members are already faction members, so Medieval Factions has already counted their
     * power once at the faction level. If a fief contributed a second time, subdividing a faction
     * would inflate it for free. {@code getCumulativePowerLevel} is a plain read of MF's own numbers,
     * used only to size the fief's own demesne, and succession must not disturb them either.
     */
    @Test
    void fiefPowerIsAReadoutAndNeverASecondPool() {
        Fief fief = aFiefWithBothMembers();

        assertEquals(18, fief.getCumulativePowerLevel(),
                "10 + 5 + 3, i.e. the members' MF power summed exactly once");

        holder.performCommand("fi leave");

        assertEquals(10.0, api.getPower(holder.getUniqueId()), "MF power is MF's, and Fiefs never writes it");
        assertEquals(5.0, api.getPower(elder.getUniqueId()));
        assertEquals(3.0, api.getPower(younger.getUniqueId()));
        assertEquals(8, fiefNamed("Ashford Mill").getCumulativePowerLevel(),
                "the departed holder's power leaves with them rather than lingering");
    }

    // --- persistence ---

    /**
     * A vacant fief and a named heir both have to survive a restart. The owner field was non-null for
     * the whole of the plugin's life before this, and {@code load} called {@code UUID.fromString} on it
     * unconditionally, so a vacant fief written to disk would have thrown out of {@code onEnable} and
     * stopped the plugin loading at all.
     */
    @Test
    void aVacantFiefAndAnHeirSurviveASaveAndLoad() {
        Fief original = aFiefWithBothMembers();
        holder.performCommand("fi heir Younger");

        Map<String, String> saved = original.save();
        Fief reloaded = new Fief(saved, integrator(), new Logger(fiefs));
        assertEquals(holder.getUniqueId(), reloaded.getOwnerUUID());
        assertEquals(younger.getUniqueId(), reloaded.getHeirUUID());

        holder.performCommand("fi grant \"Ashford Mill\" Elder");
        holder.performCommand("fi revoke \"Ashford Mill\"");
        Fief reloadedVacant = new Fief(fiefNamed("Ashford Mill").save(), integrator(), new Logger(fiefs));
        assertTrue(reloadedVacant.isVacant(), "a vacant fief must reload vacant, not throw");
        assertNull(reloadedVacant.getHeirUUID());
        assertEquals(3, reloadedVacant.getNumMembers());
    }

    /** A save file written before either field existed must still load. */
    @Test
    void aSaveFilePredatingTheseFieldsStillLoads() {
        Fief original = aFiefWithBothMembers();
        Map<String, String> saved = original.save();
        saved.remove("heirUUID");

        Fief reloaded = new Fief(saved, integrator(), new Logger(fiefs));

        assertNull(reloaded.getHeirUUID(), "an absent key reads as no nomination, not as a crash");
        assertEquals(holder.getUniqueId(), reloaded.getOwnerUUID());
    }

    private MedievalFactionsIntegrator integrator() {
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(new Logger(fiefs));
        assertTrue(integrator.resolve(), "the fake API must be resolvable");
        return integrator;
    }
}
