package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.dansplugins.factionsystem.api.event.FactionDisbandedEvent;
import com.dansplugins.factionsystem.api.event.FactionMemberLeftEvent;
import com.dansplugins.factionsystem.api.event.FactionUnclaimedChunkEvent;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player-driven paths, which a console sender cannot reach: every gameplay command early-returns
 * for a non-Player, so an RCON smoke test proves nothing about them.
 */
class FiefCommandTest {

    private ServerMock server;
    private Fiefs fiefs;
    private FakeMedievalFactionsApi api;
    private PlayerMock owner;
    private FactionId factionId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);

        owner = server.addPlayer("Owner");
        factionId = api.createFaction("faction-1", "Ashford", owner.getUniqueId());
        api.setPower(owner.getUniqueId(), 10.0);

        fiefs = MockBukkit.load(Fiefs.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Fief fiefNamed(String name) {
        return fiefs.getPersistentData().getFief(name);
    }

    // --- creation ---

    @Test
    void createMakesAFiefOwnedByTheCaller() {
        assertTrue(owner.performCommand("fi create \"Ashford Mill\""));

        Fief fief = fiefNamed("Ashford Mill");
        assertNotNull(fief, "the fief should exist");
        assertEquals(owner.getUniqueId(), fief.getOwnerUUID());
        assertTrue(fief.isMember(owner.getUniqueId()));
    }

    /**
     * Quoted names must keep their spaces. Ponder's parser joined argv with the EMPTY string, so this
     * used to produce a fief called "AshfordMill".
     */
    @Test
    void createKeepsSpacesInsideQuotes() {
        owner.performCommand("fi create \"Ashford Mill\"");
        assertNotNull(fiefNamed("Ashford Mill"));
        assertNull(fiefNamed("AshfordMill"));
    }

    @Test
    void subcommandsAreCaseInsensitive() {
        assertTrue(owner.performCommand("fi CREATE \"Ashford Mill\""));
        assertNotNull(fiefNamed("Ashford Mill"));
    }

    // --- claiming ---

    @Test
    void claimRequiresTheFactionToOwnTheLand() {
        owner.performCommand("fi create \"Ashford Mill\"");
        // The chunk is NOT claimed by the faction in MF.
        owner.performCommand("fi claim");
        assertEquals(0, fiefs.getPersistentData().getNumChunks());
    }

    @Test
    void claimSucceedsOnFactionLand() {
        owner.performCommand("fi create \"Ashford Mill\"");
        api.setFactionClaim(owner.getLocation().getChunk(), factionId);

        owner.performCommand("fi claim");

        assertEquals(1, fiefs.getPersistentData().getNumChunks());
    }

    /** limitLand=false must lift the demesne cap that power otherwise imposes. */
    @Test
    void limitLandFalseDisablesTheDemesneCap() {
        api.setPower(owner.getUniqueId(), 0.0);   // cap of 0 chunks
        owner.performCommand("fi create \"Ashford Mill\"");
        World world = owner.getWorld();
        api.setFactionClaim(world.getChunkAt(0, 0), factionId);

        owner.teleport(world.getChunkAt(0, 0).getBlock(8, 64, 8).getLocation());
        owner.performCommand("fi claim");
        assertEquals(0, fiefs.getPersistentData().getNumChunks(), "cap should block at power 0");

        fiefs.getConfig().set("limitLand", false);
        owner.performCommand("fi claim");
        assertEquals(1, fiefs.getPersistentData().getNumChunks(), "limitLand=false should permit it");
    }

    // --- rename re-points claims (upstream orphaned them) ---

    @Test
    void renameCarriesTheFiefsClaimsWithIt() {
        owner.performCommand("fi create \"Ashford Mill\"");
        Chunk chunk = owner.getLocation().getChunk();
        api.setFactionClaim(chunk, factionId);
        owner.performCommand("fi claim");
        assertEquals(1, fiefs.getPersistentData().getNumChunks());

        owner.performCommand("fi rename \"Ashford Keep\"");

        Fief renamed = fiefNamed("Ashford Keep");
        assertNotNull(renamed);
        assertEquals(1, fiefs.getPersistentData().getNumChunksClaimedByFief(renamed),
                "claims must follow the rename, not be orphaned under the old name");
        for (ClaimedChunk claimed : fiefs.getPersistentData().getClaimedChunks()) {
            assertEquals("Ashford Keep", claimed.getFief());
        }
    }

    // --- membership ---

    @Test
    void kickRemovesTheTargetFromTheFief() {
        PlayerMock member = server.addPlayer("Member");
        api.createFaction("faction-1", "Ashford", owner.getUniqueId(), member.getUniqueId());

        owner.performCommand("fi create \"Ashford Mill\"");
        owner.performCommand("fi invite Member");
        member.performCommand("fi join \"Ashford Mill\"");
        assertTrue(fiefNamed("Ashford Mill").isMember(member.getUniqueId()));

        owner.performCommand("fi kick Member");

        assertFalse(fiefNamed("Ashford Mill").isMember(member.getUniqueId()),
                "/fi kick resolved the target's fief by player name upstream, so it never worked");
    }

    // --- API events from Medieval Factions ---

    @Test
    void factionDisbandRemovesItsFiefs() {
        owner.performCommand("fi create \"Ashford Mill\"");
        assertNotNull(fiefNamed("Ashford Mill"));

        server.getPluginManager().callEvent(new FactionDisbandedEvent(factionId));

        assertNull(fiefNamed("Ashford Mill"), "a disbanded faction's fiefs must go with it");
    }

    @Test
    void leavingTheFactionRemovesTheMemberFromTheirFief() {
        PlayerMock member = server.addPlayer("Member");
        api.createFaction("faction-1", "Ashford", owner.getUniqueId(), member.getUniqueId());
        owner.performCommand("fi create \"Ashford Mill\"");
        owner.performCommand("fi invite Member");
        member.performCommand("fi join \"Ashford Mill\"");

        server.getPluginManager().callEvent(new FactionMemberLeftEvent(factionId, member.getUniqueId()));

        assertFalse(fiefNamed("Ashford Mill").isMember(member.getUniqueId()));
    }

    @Test
    void factionUnclaimDropsTheMatchingFiefClaim() {
        owner.performCommand("fi create \"Ashford Mill\"");
        Chunk chunk = owner.getLocation().getChunk();
        api.setFactionClaim(chunk, factionId);
        owner.performCommand("fi claim");
        assertEquals(1, fiefs.getPersistentData().getNumChunks());

        server.getPluginManager().callEvent(new FactionUnclaimedChunkEvent(
                factionId, chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));

        assertEquals(0, fiefs.getPersistentData().getNumChunks(),
                "a fief claim must not outlive the faction claim beneath it");
    }
}
