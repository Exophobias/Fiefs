package dansplugins.fiefs.services;

import dansplugins.fiefs.commands.abs.FiefsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class CommandGovernanceGuardTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private Player actor(List<String> messages) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ACTOR);
        when(player.hasPermission(anyString())).thenReturn(true);
        doAnswer(call -> {
            messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0)));
            return null;
        }).when(player).sendMessage(any(Component.class));
        return player;
    }

    private FiefsCommand command(String canonical, String... aliases) {
        FiefsCommand command = mock(FiefsCommand.class);
        List<String> names = new ArrayList<>(List.of(canonical)); names.addAll(List.of(aliases));
        when(command.getNames()).thenReturn(names);
        when(command.getPermissions()).thenReturn(List.of("fiefs." + canonical));
        when(command.execute(any())).thenReturn(true);
        when(command.execute(any(), any(String[].class))).thenReturn(true);
        return command;
    }

    @Test void allDispossessionVerbsAndTheirAliasesReachAuthorityBeforeEitherExecutionOverload() {
        for (String verb : List.of("revoke", "kick", "disband", "transfer", "grant")) {
            List<String> messages = new ArrayList<>(), checks = new ArrayList<>();
            Player actor = actor(messages);
            FiefsCommand target = command(verb, "alternate");
            CommandService dispatch = new CommandService((id, canonical) -> {
                assertEquals(ACTOR, id); checks.add(canonical); return "Rally freeze is active.";
            });
            dispatch.initialize(List.of(target), "Unknown.");
            assertTrue(dispatch.interpretAndExecuteCommand(actor, new String[]{verb.toUpperCase()}));
            assertTrue(dispatch.interpretAndExecuteCommand(actor, new String[]{"AlTeRnAtE", "target"}));
            assertEquals(List.of("fief." + verb, "fief." + verb), checks);
            assertEquals(List.of("Rally freeze is active.", "Rally freeze is active."), messages);
            verify(target, never()).execute(any()); verify(target, never()).execute(any(), any(String[].class));
        }
    }

    @Test void permittedUnarmedOrNonRallyPolicyLetsTheResolvedCommandExecute() {
        FiefsCommand target = command("transfer", "handover");
        CommandService dispatch = new CommandService((id, verb) -> null);
        dispatch.initialize(List.of(target), "Unknown.");
        Player actor = actor(new ArrayList<>());
        assertTrue(dispatch.interpretAndExecuteCommand(actor, new String[]{"handover", "target"}));
        verify(target).execute(eq(actor), aryEq(new String[]{"target"}));
    }

    @Test void unavailableOrIncompatibleAuthorityFailsClosedBeforeMutation() {
        for (boolean linkage : List.of(false, true)) {
            FiefsCommand target = command("grant");
            CommandService dispatch = new CommandService((id, verb) -> {
                if (linkage) throw new AbstractMethodError("old provider");
                throw new IllegalStateException("unavailable provider");
            });
            dispatch.initialize(List.of(target), "Unknown.");
            List<String> messages = new ArrayList<>();
            assertTrue(dispatch.interpretAndExecuteCommand(actor(messages), new String[]{"grant"}));
            assertTrue(messages.getFirst().contains("temporarily unavailable"));
            verify(target, never()).execute(any());
        }
    }

    @Test void ordinaryPermissionDenialDoesNotQueryGovernanceAndOtherVerbsRemainIndependent() {
        AtomicInteger checks = new AtomicInteger();
        CommandService dispatch = new CommandService((id, verb) -> { checks.incrementAndGet(); return "Unavailable"; });
        FiefsCommand revoke = command("revoke"), info = command("info");
        dispatch.initialize(List.of(revoke, info), "Unknown.");
        Player actor = actor(new ArrayList<>());
        when(actor.hasPermission("fiefs.revoke")).thenReturn(false);
        assertFalse(dispatch.interpretAndExecuteCommand(actor, new String[]{"revoke", "target"}));
        assertTrue(dispatch.interpretAndExecuteCommand(actor, new String[]{"info"}));
        assertEquals(0, checks.get()); verify(info).execute(actor);
        CommandSender console = mock(CommandSender.class); when(console.hasPermission(anyString())).thenReturn(true);
        assertTrue(dispatch.interpretAndExecuteCommand(console, new String[]{"revoke"}));
        assertEquals(0, checks.get()); verify(revoke).execute(console);
    }
}
