package dansplugins.fiefs.listeners;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionDisbandedEvent;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import dansplugins.fiefs.FakeMedievalFactionsApi;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import dansplugins.fiefs.testsupport.FakeBukkitServer;
import dansplugins.fiefs.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upstream #179's disband and departure coverage, using stable events and Patriam succession.
 * The stable departure event covers kicks and voluntary leaves once each. Unclaim durability,
 * unloaded worlds and reconciliation are covered by {@link FactionUnclaimDeferralTest}.
 */
class FactionEventListenerTest {
    private static final String FACTION = "faction-1";
    private static final String OTHER_FACTION = "faction-2";
    private static final Logger QUIET_LOGGER = new Logger(null) {
        @Override public void log(String message) { }
    };
    private final PersistentData data = new PersistentData(null);
    private final FakeMedievalFactionsApi factions = new FakeMedievalFactionsApi();
    private FactionEventListener listener;

    @BeforeEach
    void installServer() {
        FakeBukkitServer.install();
        var plugin = FakeBukkitServer.registerPlugin("Fiefs");
        Bukkit.getServicesManager().register(MedievalFactionsApi.class, factions,
                FakeBukkitServer.registerPlugin("MedievalFactions"), ServicePriority.Normal);
        MedievalFactionsIntegrator integrator = new MedievalFactionsIntegrator(QUIET_LOGGER);
        assertTrue(integrator.resolve());
        SuccessionService succession = new SuccessionService(integrator, data, plugin);
        listener = new FactionEventListener(data, succession, null, null, factions,
                new FactionEventListener.CoverageGate() {
                    @Override public boolean suspend() { return true; }
                    @Override public void restore(boolean wasReady) { }
                }, failure -> fail("Unexpected claim failure", failure));
    }

    @AfterEach void removeServer() { FakeBukkitServer.uninstall(); }

    private Fief fief(String name, String faction, UUID owner) {
        factions.createFaction(faction, faction, owner);
        Fief fief = new Fief(null, name, owner, faction, QUIET_LOGGER);
        data.addFief(fief);
        return fief;
    }

    private ClaimedChunk claim(int x, int z, Fief fief) {
        ClaimedChunk chunk = new ClaimedChunk(BukkitTestDoubles.chunk("world", x, z),
                fief.getFactionId(), fief.getName());
        data.addChunk(chunk);
        return chunk;
    }

    private void disband(String faction) {
        listener.handle(new FactionDisbandedEvent(new FactionId(faction)));
    }

    private void depart(UUID player) {
        factions.removeFactionMember(FACTION, player);
        listener.handle(new FactionMemberLeftEvent(new FactionId(FACTION), player));
    }

    @Test void disbandRemovesEveryFiefOfThatFaction() {
        fief("Testopia", FACTION, UUID.randomUUID());
        fief("Secondia", FACTION, UUID.randomUUID());
        disband(FACTION);
        assertTrue(data.getFiefs().isEmpty());
    }

    @Test void disbandLeavesTheFiefsOfEveryOtherFaction() {
        fief("Testopia", FACTION, UUID.randomUUID());
        Fief survivor = fief("Elsewhere", OTHER_FACTION, UUID.randomUUID());
        disband(FACTION);
        assertEquals(1, data.getFiefs().size());
        assertSame(survivor, data.getFiefs().getFirst());
    }

    @Test void disbandAlsoReleasesAllLandThoseFiefsHeld() {
        Fief fief = fief("Testopia", FACTION, UUID.randomUUID());
        claim(1, 2, fief);
        claim(3, 4, fief);
        disband(FACTION);
        assertEquals(0, data.getNumChunks());
    }

    @Test void disbandLeavesLandHeldByAnotherFactionsFiefs() {
        Fief removed = fief("Testopia", FACTION, UUID.randomUUID());
        Fief survivor = fief("Elsewhere", OTHER_FACTION, UUID.randomUUID());
        claim(1, 2, removed);
        ClaimedChunk retained = claim(3, 4, survivor);
        disband(FACTION);
        assertEquals(1, data.getNumChunks());
        assertSame(retained, data.getClaimedChunks().getFirst());
    }

    @Test void disbandForAFactionWithNoFiefsChangesNothing() {
        Fief survivor = fief("Elsewhere", OTHER_FACTION, UUID.randomUUID());
        ClaimedChunk retained = claim(1, 2, survivor);
        disband(FACTION);
        assertSame(survivor, data.getFiefs().getFirst());
        assertSame(retained, data.getClaimedChunks().getFirst());
    }

    @Test void departureDropsOnlyThatMemberAndPreservesTheFiefAndLand() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Fief fief = fief("Testopia", FACTION, owner);
        fief.addMember(member);
        ClaimedChunk land = claim(1, 2, fief);
        depart(member);
        assertFalse(fief.isMember(member));
        assertTrue(fief.isMember(owner));
        assertSame(fief, data.getFief("Testopia"));
        assertSame(land, data.getClaimedChunks().getFirst());
    }

    @Test void departureByAPlayerInNoFiefChangesNothing() {
        UUID owner = UUID.randomUUID();
        Fief fief = fief("Testopia", FACTION, owner);
        depart(UUID.randomUUID());
        assertEquals(1, fief.getNumMembers());
        assertTrue(fief.isMember(owner));
    }

    @Test void aDepartingLastHolderRevertsTheFiefRatherThanKeepingAnInvalidOwner() {
        UUID owner = UUID.randomUUID();
        Fief fief = fief("Testopia", FACTION, owner);
        ClaimedChunk land = claim(1, 2, fief);
        depart(owner);
        assertSame(fief, data.getFief("Testopia"));
        assertNull(fief.getOwnerUUID());
        assertEquals(0, fief.getNumMembers());
        assertSame(land, data.getClaimedChunks().getFirst());
    }
}
