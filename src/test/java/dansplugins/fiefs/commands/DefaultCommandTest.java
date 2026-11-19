package dansplugins.fiefs.commands;

import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.services.CommandService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the retained metadata command. Bare {@code /fi} now routes to filtered help;
 * direct metadata invocation still has to enforce {@code fiefs.default} itself.
 */
class DefaultCommandTest {

    private static final String NODE = "fiefs.default";

    private final DefaultCommand defaultCommand = new DefaultCommand("v1.2.3");
    private final List<String> messages = new ArrayList<>();
    private final CommandSender allowed =
            BukkitTestDoubles.senderWithPermissions(Collections.singleton(NODE), messages);
    private final CommandSender denied =
            BukkitTestDoubles.senderWithPermissions(Collections.emptySet(), messages);

    private boolean anyMessageContains(String text) {
        for (String message : messages) {
            if (message.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void execute_withThePermission_sendsTheVersionAndInfoLines() {
        boolean result = defaultCommand.execute(allowed);

        assertTrue(result);
        assertEquals(4, messages.size());
        assertTrue(anyMessageContains("Fiefs v1.2.3"));
        assertTrue(anyMessageContains("Wiki: https://github.com/Dans-Plugins/Fiefs/wiki"));
    }

    @Test
    void execute_withoutThePermission_sendsNoVersionOrInfoLines() {
        boolean result = defaultCommand.execute(denied);

        assertFalse(result);
        assertFalse(anyMessageContains("v1.2.3"));
        assertFalse(anyMessageContains("Developer"));
        assertFalse(anyMessageContains("Wiki"));
    }

    @Test
    void execute_withArguments_appliesTheSamePermissionCheck() {
        boolean result = defaultCommand.execute(denied, new String[]{"ignored"});

        assertFalse(result);
        assertFalse(anyMessageContains("v1.2.3"));
    }

    /** A denied metadata invocation must read exactly as a denied {@code /fi <subcommand>} does. */
    @Test
    void execute_withoutThePermission_answersAsTheCommandServiceDoes() {
        CommandService commandService = new CommandService();
        commandService.initialize(
                new ArrayList<FiefsCommand>(Arrays.asList(new SubcommandRequiringTheSameNode())),
                "not found");

        boolean serviceResult = commandService.interpretAndExecuteCommand(denied, new String[]{"sub"});
        List<String> serviceRefusal = new ArrayList<>(messages);
        messages.clear();

        boolean result = defaultCommand.execute(denied);

        assertFalse(serviceRefusal.isEmpty(), "the command service should explain its refusal");
        assertEquals(serviceRefusal, messages);
        assertEquals(serviceResult, result);
    }

    private static final class SubcommandRequiringTheSameNode extends FiefsCommand {

        private SubcommandRequiringTheSameNode() {
            super(new ArrayList<>(Arrays.asList("sub")), new ArrayList<>(Arrays.asList(NODE)));
        }

        @Override
        public boolean execute(CommandSender sender) {
            throw new AssertionError("the command service should have refused before running this");
        }

        @Override
        public boolean execute(CommandSender sender, String[] args) {
            throw new AssertionError("the command service should have refused before running this");
        }
    }
}
