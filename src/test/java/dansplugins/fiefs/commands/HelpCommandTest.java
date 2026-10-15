package dansplugins.fiefs.commands;

import dansplugins.fiefs.services.CommandService;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HelpCommandTest {
    private final List<String> messages = new ArrayList<>();
    private CommandSender sender(Set<String> permissions) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
        doAnswer(call -> { messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))); return null; })
            .when(sender).sendMessage(any(Component.class));
        return sender;
    }
    private boolean contains(String text) { return messages.stream().anyMatch(s -> s.contains(text)); }
    @Test void ordinaryHelpClampsAndHidesStaffNavigation() {
        var sender = sender(Set.of("fiefs.help", "fiefs.info")); var help = new HelpCommand();
        for (String page : List.of("0", "99", "banana")) {
            messages.clear(); assertTrue(help.execute(sender, new String[]{page}));
            assertEquals(4, messages.size()); assertTrue(contains("/fi info")); assertFalse(contains("config"));
            assertFalse(contains("for more")); assertFalse(contains("Staff"));
        }
        messages.clear(); assertFalse(help.execute(sender, new String[]{"staff"}));
        assertEquals(List.of("You do not have permission."), messages);
    }
    @Test void partialStaffHaveSeparatePageAndNoEmptyOrdinaryPage() {
        var help = new HelpCommand(); var both = sender(Set.of("fiefs.help", "fiefs.config"));
        help.execute(both); assertTrue(contains("Fiefs (1/2)")); assertFalse(contains("config <"));
        messages.clear(); help.execute(both, new String[]{"2"});
        assertTrue(contains("Fiefs Staff (2/2)")); assertTrue(contains("config <")); assertFalse(contains("/fi help ["));
        messages.clear(); help.execute(sender(Set.of("fiefs.config")));
        assertEquals(3, messages.size()); assertTrue(contains("Fiefs Staff")); assertFalse(contains("for more"));
    }
    @Test void dispatcherShowsHelpForRootAndUnknownAndDeniesExecutionIndependently() {
        var service = new CommandService(); var denied = mock(FiefsCommand.class);
        when(denied.getNames()).thenReturn(List.of("config")); when(denied.getPermissions()).thenReturn(List.of("fiefs.config"));
        service.initialize(List.of(new HelpCommand(), denied), "Unknown command.");
        var ordinary = sender(Set.of("fiefs.help"));
        assertTrue(service.interpretAndExecuteCommand(ordinary, new String[0])); assertTrue(contains("Fiefs"));
        messages.clear(); assertFalse(service.interpretAndExecuteCommand(ordinary, new String[]{"unknown"}));
        assertEquals("Unknown command.", messages.get(0)); assertTrue(contains("Fiefs"));
        assertFalse(service.interpretAndExecuteCommand(ordinary, new String[]{"config"})); verify(denied, never()).execute(any());
        assertEquals(List.of("help"), service.complete(ordinary, new String[]{""}));
        assertTrue(service.complete(ordinary, new String[]{"help", ""}).isEmpty());
        assertEquals(List.of("staff"), service.complete(sender(Set.of("fiefs.help", "fiefs.config")), new String[]{"help", ""}));
    }
    @Test void eightEntriesPerPageAreEnforcedForAllPermissions() {
        var sender = sender(Set.of()); when(sender.hasPermission(anyString())).thenReturn(true);
        var help = new HelpCommand(); help.execute(sender);
        assertEquals(10, messages.size()); assertTrue(contains("Fiefs (1/4)"));
        messages.clear(); help.execute(sender, new String[]{"99"});
        assertTrue(contains("Fiefs Staff (4/4)")); assertFalse(contains("for more"));
    }
}
