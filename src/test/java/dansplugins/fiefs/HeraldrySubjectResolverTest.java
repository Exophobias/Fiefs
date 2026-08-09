package dansplugins.fiefs;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import com.github.exophobias.patriamheraldry.api.SubjectKey;
import com.github.exophobias.patriamheraldry.api.SubjectResolver;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fiefs' answers to PatriamHeraldry, as PatriamHeraldry will actually get them: off the
 * {@code ServicesManager}, which is the only route it uses.
 *
 * <p>The resolver is what makes {@code /arms set} work for a fief at all. Until one is registered every
 * identity command refuses, so "the service is there and answers" is the first thing worth pinning.
 */
class HeraldrySubjectResolverTest {

    private ServerMock server;
    private Fiefs fiefs;
    private FakeMedievalFactionsApi api;
    private PlayerMock holder;
    private FactionId factionId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        api = new FakeMedievalFactionsApi();
        server.getServicesManager().register(MedievalFactionsApi.class, api,
                MockBukkit.createMockPlugin("MedievalFactions"), ServicePriority.Normal);
        // The bridge is gated on this plugin being present, so it has to exist before Fiefs enables.
        MockBukkit.createMockPlugin("PatriamHeraldry");

        holder = server.addPlayer("Holder");
        factionId = api.createFaction("faction-1", "Ashford", holder.getUniqueId());
        api.setPower(holder.getUniqueId(), 10.0);

        fiefs = MockBukkit.load(Fiefs.class);
        holder.performCommand("fi create \"Ashford Mill\"");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** The resolver as PatriamHeraldry reads it: through the ServicesManager and nothing else. */
    private SubjectResolver resolver() {
        SubjectResolver resolver = server.getServicesManager().load(SubjectResolver.class);
        assertNotNull(resolver, "Fiefs must register a SubjectResolver, or /arms set refuses for a fief");
        return resolver;
    }

    private Fief ashford() {
        return fiefs.getPersistentData().getFief("Ashford Mill");
    }

    private SubjectKey keyOfAshford() {
        return SubjectKey.fief(ashford().getId().toString());
    }

    @Test
    @DisplayName("the resolver is published on the ServicesManager and answers for fiefs")
    void itIsRegisteredAndAnswersForFiefs() {
        assertEquals(SubjectKey.Type.FIEF, resolver().type());
    }

    @Test
    @DisplayName("subjectOf names the fief a player is in, keyed on the id and not the name")
    void subjectOfNamesThePlayersFief() {
        Optional<SubjectKey> subject = resolver().subjectOf(holder);

        assertTrue(subject.isPresent());
        assertEquals(SubjectKey.Type.FIEF, subject.get().type());
        assertEquals(ashford().getId().toString(), subject.get().id());
    }

    @Test
    @DisplayName("a player in no fief is empty rather than an error")
    void aPlayerInNoFiefIsEmpty() {
        // The command turns this into "you are not in a fief", so answering with anything else would
        // turn a normal state into a failure.
        PlayerMock stranger = server.addPlayer("Stranger");

        assertTrue(resolver().subjectOf(stranger).isEmpty());
    }

    @Test
    @DisplayName("the holder may change the arms, and a mere member may not")
    void onlyTheHolderAndTheFactionHeadMayAdminister() {
        PlayerMock member = server.addPlayer("Member");
        api.createFaction("faction-1", "Ashford", holder.getUniqueId(), member.getUniqueId());
        holder.performCommand("fi invite Member");
        member.performCommand("fi join \"Ashford Mill\"");
        assertTrue(ashford().isMember(member.getUniqueId()), "the member should be in the fief");

        assertTrue(resolver().mayAdminister(holder, keyOfAshford()));
        assertFalse(resolver().mayAdminister(member, keyOfAshford()),
                "belonging to a fief does not entitle a player to speak for it");
    }

    @Test
    @DisplayName("the head of the fief's faction may change its arms, holding it or not")
    void theFactionHeadMayAdminister() {
        // A fief is held FROM a faction, so the faction's recorded head can act for any fief of theirs.
        // That is the authority /fi grant and /fi revoke already use.
        PlayerMock liege = server.addPlayer("Liege");
        api.createFaction("faction-1", "Ashford", liege.getUniqueId(), holder.getUniqueId());
        api.setPrimaryOwner("faction-1", liege.getUniqueId());

        assertTrue(resolver().mayAdminister(liege, keyOfAshford()));
    }

    @Test
    @DisplayName("a subject that is not a fief of this server is refused rather than throwing")
    void anUnknownSubjectIsRefused() {
        SubjectKey noSuchFief = SubjectKey.fief(UUID.randomUUID().toString());

        assertFalse(resolver().mayAdminister(holder, noSuchFief));
        assertTrue(resolver().displayName(noSuchFief).isEmpty());
        assertFalse(resolver().exists(noSuchFief));
        assertTrue(resolver().head(noSuchFief).isEmpty());
    }

    @Test
    @DisplayName("an id that is not a uuid is refused rather than thrown at the player")
    void aMalformedIdIsRefused() {
        // SubjectKey itself accepts this: it only rejects a blank id and one containing a colon. So the
        // UUID.fromString is ours to survive, and it is reached from a command a player just ran.
        SubjectKey mangled = SubjectKey.fief("Ashford Mill");

        assertFalse(resolver().mayAdminister(holder, mangled));
        assertTrue(resolver().displayName(mangled).isEmpty());
        assertFalse(resolver().exists(mangled));
    }

    @Test
    @DisplayName("a subject of another type is refused, since one resolver answers for one type")
    void anotherTypesSubjectIsRefused() {
        SubjectKey faction = SubjectKey.faction(factionId.getValue());

        assertFalse(resolver().exists(faction));
        assertTrue(resolver().displayName(faction).isEmpty());
        assertFalse(resolver().mayAdminister(holder, faction));
    }

    @Test
    @DisplayName("displayName, names and exists answer for a fief that is there")
    void theDisplaySideAnswers() {
        assertEquals(Optional.of("Ashford Mill"), resolver().displayName(keyOfAshford()));
        assertTrue(resolver().names().contains("Ashford Mill"));
        assertTrue(resolver().exists(keyOfAshford()));
    }

    @Test
    @DisplayName("byName matches case-insensitively and is the only place a name becomes a subject")
    void byNameMatchesCaseInsensitively() {
        assertEquals(Optional.of(keyOfAshford()), resolver().byName("ashford mill"));
        assertEquals(Optional.of(keyOfAshford()), resolver().byName("ASHFORD MILL"));
        assertTrue(resolver().byName("Bramley").isEmpty());
        assertTrue(resolver().byName("").isEmpty(), "a blank name must not become SubjectKey's throw");
    }

    @Test
    @DisplayName("head names the fief's holder, and is empty for a fief nobody holds")
    void headNamesTheHolder() {
        assertEquals(holder.getUniqueId(), resolver().head(keyOfAshford()).orElseThrow().getUniqueId());

        // A fief that has reverted to its faction is held by nobody, which is a real state. The
        // faction's head is deliberately not substituted: they did not submit the design.
        ashford().setOwnerUUID(null);

        assertTrue(resolver().head(keyOfAshford()).isEmpty());
    }

    @Test
    @DisplayName("renaming the fief leaves the subject key alone, so its arms survive")
    void renamingDoesNotMoveTheSubject() {
        // Step 10's acceptance criterion. The armorial is keyed on the subject id, so this is what "set
        // fief arms, rename the fief, the arms survive" reduces to.
        SubjectKey before = resolver().subjectOf(holder).orElseThrow();

        assertTrue(holder.performCommand("fi rename \"Ashford Keep\""));

        assertEquals(before, resolver().subjectOf(holder).orElseThrow(),
                "the subject must not move when the name does");
        assertEquals(Optional.of("Ashford Keep"), resolver().displayName(before),
                "the display name follows the rename, which is all it is for");
        assertEquals(Optional.of(before), resolver().byName("Ashford Keep"));
        assertTrue(resolver().byName("Ashford Mill").isEmpty(), "the old name answers to nothing");
    }
}
