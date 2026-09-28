package dansplugins.fiefs.commands;

import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link HelpCommand}, the one command that needs neither the
 * Medieval Factions integrator nor persisted data. The pages it prints are a documentation
 * surface in their own right, so the assertions below pin the set of commands each page
 * advertises rather than only the return values.
 */
class HelpCommandTest {

    private final HelpCommand helpCommand = new HelpCommand();
    private final List<String> messages = new ArrayList<>();
    private final CommandSender sender = BukkitTestDoubles.messageCapturingConsole(messages);

    private static final String USAGE = "Usage: /fi help { 1 | 2 | 3 }";

    private boolean anyMessageContains(String text) {
        for (String message : messages) {
            if (message.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void execute_withNoArguments_sendsTheUsageMessage() {
        boolean result = helpCommand.execute(sender, new String[]{});

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(anyMessageContains(USAGE));
    }

    @Test
    void execute_withAnUnrecognizedPage_sendsTheUsageMessage() {
        boolean result = helpCommand.execute(sender, new String[]{"4"});

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(anyMessageContains(USAGE));
    }

    @Test
    void execute_withANonNumericPage_sendsTheUsageMessage() {
        boolean result = helpCommand.execute(sender, new String[]{"one"});

        assertFalse(result);
        assertEquals(1, messages.size());
        assertTrue(anyMessageContains(USAGE));
    }

    @Test
    void execute_withPageOne_listsThatPagesCommands() {
        boolean result = helpCommand.execute(sender, new String[]{"1"});

        assertTrue(result);
        assertTrue(anyMessageContains("Page 1/3"));
        for (String command : new String[]{"/fi help", "/fi list", "/fi join", "/fi info",
                "/fi members", "/fi leave", "/fi checkclaim", "/fi create", "/fi invite"}) {
            assertTrue(anyMessageContains(command + " -"), "page one should advertise " + command);
        }
    }

    @Test
    void execute_withPageTwo_listsThatPagesCommands() {
        boolean result = helpCommand.execute(sender, new String[]{"2"});

        assertTrue(result);
        assertTrue(anyMessageContains("Page 2/3"));
        for (String command : new String[]{"/fi disband", "/fi claim", "/fi unclaim", "/fi desc",
                "/fi rename", "/fi kick", "/fi transfer", "/fi flags", "/fi config", "/fi whois"}) {
            assertTrue(anyMessageContains(command + " -"), "page two should advertise " + command);
        }
    }

    @Test
    void execute_withExtraArgumentsAfterThePage_stillSendsThatPage() {
        boolean result = helpCommand.execute(sender, new String[]{"1", "ignored"});

        assertTrue(result);
        assertTrue(anyMessageContains("Page 1/3"));
    }

    @Test
    void execute_withPageThree_listsThePatriamSuccessionAndCapitalCommands() {
        assertTrue(helpCommand.execute(sender, new String[]{"3"}));
        assertTrue(anyMessageContains("Page 3/3"));
        for (String command : new String[]{"/fi heir", "/fi capital", "/fi succession",
                "/fi grant", "/fi revoke"}) {
            assertTrue(anyMessageContains(command + " -"), "page three should advertise " + command);
        }
    }

    @Test
    void execute_senderOnlyOverload_sendsNothingAndReportsFailure() {
        boolean result = helpCommand.execute(sender);

        assertFalse(result);
        assertTrue(messages.isEmpty());
    }
}
