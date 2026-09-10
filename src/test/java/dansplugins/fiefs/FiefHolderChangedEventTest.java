package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionDisbandedEvent;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import dansplugins.fiefs.externalapi.FiefHolderChangedEvent;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real Fiefs command/service paths on MockBukkit; observations are asserted outside event handlers. */
class FiefHolderChangedEventTest {
    private ServerMock server;
    private Fiefs plugin;
    private FakeMedievalFactionsApi api;
    private PlayerMock holder;
    private PlayerMock successor;
    private PlayerMock unrelated;
    private FactionId realm;
    private final List<Observed> events = new ArrayList<>();

    private record Observed(FiefHolderChangedEvent event, boolean mainThread, boolean dirty,
                            UUID liveOwner, UUID heir, List<UUID> members, boolean exists,
                            int claims) { }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);
        holder = server.addPlayer("Holder");
        successor = server.addPlayer("Successor");
        unrelated = server.addPlayer("Unrelated");
        realm = api.createFaction("realm", "Realm", holder.getUniqueId(), successor.getUniqueId(),
                unrelated.getUniqueId());
        api.setPower(holder.getUniqueId(), 10.0);
        api.setPower(successor.getUniqueId(), 5.0);
        plugin = MockBukkit.load(Fiefs.class);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void changed(FiefHolderChangedEvent event) {
                Fief live = plugin.getPersistentData().getFiefById(event.getFiefId());
                events.add(new Observed(event, Bukkit.isPrimaryThread(), plugin.getPersistentData().isDirty(),
                        live == null ? null : live.getOwnerUUID(), live == null ? null : live.getHeirUUID(),
                        live == null ? List.of() : List.copyOf(live.getMembers()), live != null,
                        plugin.getPersistentData().getNumChunks()));
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private Fief create() {
        assertTrue(holder.performCommand("fi create \"Mill\""));
        Fief fief = plugin.getPersistentData().getFief("Mill");
        assertNotNull(fief);
        return fief;
    }

    private Fief withSuccessor() {
        Fief fief = create();
        assertTrue(holder.performCommand("fi invite Successor"));
        assertTrue(successor.performCommand("fi join \"Mill\""));
        assertTrue(holder.performCommand("fi heir Successor"));
        events.clear();
        plugin.getPersistentData().clearDirty();
        return fief;
    }

    private Observed only(UUID fiefId, UUID before, UUID after) {
        assertEquals(1, events.size());
        Observed observed = events.getFirst();
        assertEquals(fiefId, observed.event().getFiefId());
        assertEquals(before, observed.event().getPreviousHolderId());
        assertEquals(after, observed.event().getCurrentHolderId());
        assertTrue(observed.mainThread());
        assertFalse(observed.event().isAsynchronous());
        assertTrue(observed.dirty());
        assertEquals(after, observed.liveOwner());
        assertNull(observed.heir(), "the old holder's nomination must already be cleared");
        return observed;
    }

    @Test
    void creationPublishesRegisteredHolderAndMembership() {
        Fief fief = create();
        Observed observed = only(fief.getId(), null, holder.getUniqueId());
        assertTrue(observed.exists());
        assertEquals(List.of(holder.getUniqueId()), observed.members());
    }

    @Test
    void failedCreationAndUnrelatedMemberChangesAreSilent() {
        Fief fief = create();
        events.clear();
        assertFalse(holder.performCommand("fi create \"Other\""));
        assertFalse(successor.performCommand("fi create \"Mill\""));
        assertTrue(holder.performCommand("fi invite Successor"));
        assertTrue(successor.performCommand("fi join \"Mill\""));
        assertTrue(holder.performCommand("fi heir Successor"));
        assertTrue(events.isEmpty());
        assertEquals(holder.getUniqueId(), fief.getOwnerUUID());
    }

    @Test
    void grantPublishesNewMembershipAndClearedNomination() {
        Fief fief = create();
        fief.setHeirUUID(unrelated.getUniqueId());
        events.clear();
        plugin.getPersistentData().clearDirty();
        assertTrue(holder.performCommand("fi grant \"Mill\" Successor"));
        Observed observed = only(fief.getId(), holder.getUniqueId(), successor.getUniqueId());
        assertTrue(observed.members().containsAll(List.of(holder.getUniqueId(), successor.getUniqueId())));
    }

    @Test
    void refusedGrantAndSameHolderGrantAreSilent() {
        Fief fief = create();
        events.clear();
        assertFalse(unrelated.performCommand("fi grant \"Mill\" Successor"));
        assertFalse(holder.performCommand("fi grant \"Mill\" Holder"));
        assertTrue(events.isEmpty());
        assertEquals(holder.getUniqueId(), fief.getOwnerUUID());
    }

    @Test
    void transferPublishesOnlyAfterTheOldNominationIsCleared() {
        Fief fief = withSuccessor();
        assertTrue(holder.performCommand("fi transfer Successor"));
        assertEquals(List.of(holder.getUniqueId(), successor.getUniqueId()),
                only(fief.getId(), holder.getUniqueId(), successor.getUniqueId()).members());
    }

    @Test
    void refusedTransfersAreSilent() {
        Fief fief = withSuccessor();
        assertFalse(successor.performCommand("fi transfer Holder"));
        assertFalse(holder.performCommand("fi transfer Unrelated"));
        assertFalse(holder.performCommand("fi transfer Holder"));
        assertTrue(events.isEmpty());
        assertEquals(holder.getUniqueId(), fief.getOwnerUUID());
    }

    @Test
    void revokePublishesVacancyWithoutRemovingTheFiefOrMembers() {
        Fief fief = withSuccessor();
        assertTrue(holder.performCommand("fi revoke \"Mill\""));
        Observed observed = only(fief.getId(), holder.getUniqueId(), null);
        assertTrue(observed.exists());
        assertEquals(List.of(holder.getUniqueId(), successor.getUniqueId()), observed.members());
        events.clear();
        assertFalse(holder.performCommand("fi revoke \"Mill\""));
        assertTrue(events.isEmpty());
    }

    @Test
    void refusedRevokeIsSilent() {
        Fief fief = withSuccessor();
        assertFalse(successor.performCommand("fi revoke \"Mill\""));
        assertTrue(events.isEmpty());
        assertEquals(holder.getUniqueId(), fief.getOwnerUUID());
    }

    @Test
    void leavingPassesTheFiefAfterRemovingTheDepartedHolder() {
        Fief fief = withSuccessor();
        assertTrue(holder.performCommand("fi leave"));
        assertEquals(List.of(successor.getUniqueId()),
                only(fief.getId(), holder.getUniqueId(), successor.getUniqueId()).members());
    }

    @Test
    void leavingWithoutAnEligibleHeirPublishesReversion() {
        Fief fief = create();
        events.clear();
        assertTrue(holder.performCommand("fi leave"));
        Observed observed = only(fief.getId(), holder.getUniqueId(), null);
        assertTrue(observed.exists());
        assertTrue(observed.members().isEmpty());
    }

    @Test
    void factionDepartureUsesTheSameCompletedSuccessionNotification() {
        Fief fief = withSuccessor();
        api.removeFactionMember("realm", holder.getUniqueId());
        server.getPluginManager().callEvent(new FactionMemberLeftEvent(realm, holder.getUniqueId()));
        assertEquals(List.of(successor.getUniqueId()),
                only(fief.getId(), holder.getUniqueId(), successor.getUniqueId()).members());
    }

    @Test
    void disbandPublishesOnlyAfterFiefAndClaimsDisappear() {
        Fief fief = create();
        api.setFactionClaim(holder.getLocation().getChunk(), realm);
        assertTrue(holder.performCommand("fi claim"));
        assertEquals(1, plugin.getPersistentData().getNumChunks());
        events.clear();
        assertTrue(holder.performCommand("fi disband"));
        Observed observed = only(fief.getId(), holder.getUniqueId(), null);
        assertFalse(observed.exists());
        assertEquals(0, observed.claims());
        events.clear();
        assertFalse(plugin.getPersistentData().removeFief(fief));
        assertTrue(events.isEmpty(), "repeated removal is not a second holding change");
    }

    @Test
    void parentFactionDisbandRemovesEveryHolderAndPreservesAnUnrelatedFief() {
        Fief first = create();
        assertTrue(successor.performCommand("fi create \"Pine\""));
        Fief second = plugin.getPersistentData().getFief("Pine");
        api.removeFactionMember("realm", unrelated.getUniqueId());
        api.createFaction("control", "Control", unrelated.getUniqueId());
        assertTrue(unrelated.performCommand("fi create \"Control\""));
        Fief control = plugin.getPersistentData().getFief("Control");
        events.clear();
        server.getPluginManager().callEvent(new FactionDisbandedEvent(realm));
        assertEquals(2, events.size());
        assertEquals(List.of(first.getId(), second.getId()), events.stream().map(o -> o.event().getFiefId()).toList());
        assertTrue(events.stream().noneMatch(Observed::exists));
        assertSame(control, plugin.getPersistentData().getFiefById(control.getId()));
        assertEquals(unrelated.getUniqueId(), control.getOwnerUUID());
    }

    @Test
    void directSetterDoesNotAnnounceAnIncompleteOperation() {
        Fief fief = withSuccessor();
        fief.setOwnerUUID(successor.getUniqueId());
        assertTrue(events.isEmpty());
        assertEquals(successor.getUniqueId(), fief.getHeirUUID(),
                "setter-only state is intentionally not the complete holder transition");
    }

    @Test
    void anOffThreadNotificationIsDeliveredOnTheMainTurn() {
        Fief fief = withSuccessor();
        fief.setOwnerUUID(successor.getUniqueId());
        fief.setHeirUUID(null);
        plugin.getPersistentData().markDirty();
        java.util.concurrent.CompletableFuture.runAsync(() ->
                plugin.getPersistentData().publishHolderChange(fief, holder.getUniqueId())).join();
        assertTrue(events.isEmpty(), "the owner notification must not touch consumers off-thread");
        server.getScheduler().performTicks(1);
        only(fief.getId(), holder.getUniqueId(), successor.getUniqueId());
    }

    @Test
    void aBrokenOptionalListenerCannotFailAnAppliedGrant() {
        Fief fief = withSuccessor();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void broken(FiefHolderChangedEvent ignored) {
                throw new IllegalStateException("deliberate listener refusal");
            }
        }, MockBukkit.createMockPlugin("BrokenCosmetic"));
        assertTrue(holder.performCommand("fi grant \"Mill\" Successor"));
        assertEquals(successor.getUniqueId(), fief.getOwnerUUID());
        assertNull(fief.getHeirUUID());
        assertTrue(plugin.getPersistentData().isDirty());
    }
}
