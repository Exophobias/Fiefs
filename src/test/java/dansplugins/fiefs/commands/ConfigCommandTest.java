package dansplugins.fiefs.commands;

import dansplugins.fiefs.services.ConfigService;
import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link ConfigCommand}: how it parses {@code show} and
 * {@code set (option) (value)} and what it hands on to {@link ConfigService}.
 *
 * <p>{@link ConfigService} needs a live {@code Fiefs} plugin to read and save the config, which
 * cannot be constructed outside a server, so the command is given a service that records the
 * calls it receives instead. What the service then does with an option — the type coercion, the
 * refusal to set {@code version}, the "wasn't found" reply — is its own behaviour and is not
 * exercised here.
 */
class ConfigCommandTest {

    private final RecordingConfigService configService = new RecordingConfigService();
    private final ConfigCommand configCommand = new ConfigCommand(configService);
    private final List<String> messages = new ArrayList<>();
    private final CommandSender sender = BukkitTestDoubles.messageCapturingConsole(messages);

    private String lastMessage() {
        return messages.get(messages.size() - 1);
    }

    @Test
    void execute_senderOnlyOverload_failsWithoutSendingAnything() {
        boolean result = configCommand.execute(sender);

        assertFalse(result);
        assertTrue(messages.isEmpty());
        assertTrue(configService.calls.isEmpty());
    }

    @Test
    void execute_withNoArguments_listsTheSubCommands() {
        boolean result = configCommand.execute(sender, new String[]{});

        assertFalse(result);
        assertTrue(lastMessage().contains("Sub-commands: show, set"));
        assertTrue(configService.calls.isEmpty());
    }

    @Test
    void execute_withAnUnknownSubCommand_listsTheSubCommands() {
        boolean result = configCommand.execute(sender, new String[]{"reset"});

        assertFalse(result);
        assertTrue(lastMessage().contains("Sub-commands: show, set"));
        assertTrue(configService.calls.isEmpty());
    }

    @Test
    void execute_show_sendsTheConfigListToTheSender() {
        boolean result = configCommand.execute(sender, new String[]{"show"});

        assertTrue(result);
        assertEquals(1, configService.calls.size());
        assertEquals("sendConfigList", configService.calls.get(0).method);
        assertSame(sender, configService.calls.get(0).sender);
    }

    @Test
    void execute_matchesTheSubCommandCaseInsensitively() {
        boolean result = configCommand.execute(sender, new String[]{"SHOW"});

        assertTrue(result);
        assertEquals("sendConfigList", configService.calls.get(0).method);
    }

    @Test
    void execute_show_ignoresArgumentsAfterIt() {
        boolean result = configCommand.execute(sender, new String[]{"show", "debugMode"});

        assertTrue(result);
        assertEquals(1, configService.calls.size());
        assertEquals("sendConfigList", configService.calls.get(0).method);
    }

    @Test
    void execute_setWithOnlyAnOption_sendsTheUsageMessage() {
        boolean result = configCommand.execute(sender, new String[]{"set", "debugMode"});

        assertFalse(result);
        assertTrue(lastMessage().contains("Usage: /fi config set (option) (value)"));
        assertTrue(configService.calls.isEmpty());
    }

    @Test
    void execute_setWithNothingElse_sendsTheUsageMessage() {
        boolean result = configCommand.execute(sender, new String[]{"set"});

        assertFalse(result);
        assertTrue(lastMessage().contains("Usage: /fi config set (option) (value)"));
        assertTrue(configService.calls.isEmpty());
    }

    @Test
    void execute_set_passesTheOptionAndValueThroughAsTyped() {
        boolean result = configCommand.execute(sender, new String[]{"set", "debugMode", "true"});

        assertTrue(result);
        assertEquals(1, configService.calls.size());
        RecordedCall call = configService.calls.get(0);
        assertEquals("setConfigOption", call.method);
        assertEquals("debugMode", call.option);
        assertEquals("true", call.value);
        assertSame(sender, call.sender);
    }

    @Test
    void execute_set_acceptsTheDocumentedUsageReportingOptOut() {
        // CONFIG.md and the CHANGELOG both give this exact command as the per-plugin opt-out
        boolean result = configCommand.execute(sender, new String[]{"set", "usage-reporting.enabled", "false"});

        assertTrue(result);
        RecordedCall call = configService.calls.get(0);
        assertEquals("usage-reporting.enabled", call.option);
        assertEquals("false", call.value);
    }

    @Test
    void execute_set_leavesTheOptionsCaseForTheServiceToResolve() {
        boolean result = configCommand.execute(sender, new String[]{"set", "DEBUGMODE", "true"});

        assertTrue(result);
        assertEquals("DEBUGMODE", configService.calls.get(0).option);
    }

    @Test
    void execute_set_takesOnlyTheFirstWordOfTheValue() {
        boolean result = configCommand.execute(sender, new String[]{"set", "someOption", "two", "words"});

        assertTrue(result);
        assertEquals("two", configService.calls.get(0).value);
    }

    @Test
    void execute_set_keepsTheQuotesAroundAQuotedValue() {
        // Every option receives the third argument verbatim, quote characters included
        boolean result = configCommand.execute(sender, new String[]{"set", "someOption", "\"quoted\""});

        assertTrue(result);
        assertEquals("\"quoted\"", configService.calls.get(0).value);
    }

    @Test
    void execute_set_treatsTheFormerMessageOptionNamesLikeAnyOtherOption() {
        // denyUsageMessage and denyCreationMessage used to go through a double-quote parser, but
        // neither is a config option, so they now reach the service the same way as everything else
        boolean result = configCommand.execute(sender,
                new String[]{"set", "denyUsageMessage", "\"You", "may", "not.\""});

        assertTrue(result);
        RecordedCall call = configService.calls.get(0);
        assertEquals("denyUsageMessage", call.option);
        assertEquals("\"You", call.value);
    }

    @Test
    void execute_set_doesNotAskForDoubleQuotesForTheFormerMessageOptionNames() {
        boolean result = configCommand.execute(sender, new String[]{"set", "denyCreationMessage", "unquoted"});

        assertTrue(result);
        assertTrue(messages.isEmpty());
        assertEquals("unquoted", configService.calls.get(0).value);
    }

    /** One call {@link ConfigCommand} made on the service, with the arguments it passed. */
    private static final class RecordedCall {
        final String method;
        final String option;
        final String value;
        final CommandSender sender;

        RecordedCall(String method, String option, String value, CommandSender sender) {
            this.method = method;
            this.option = option;
            this.value = value;
            this.sender = sender;
        }
    }

    /**
     * A {@link ConfigService} with no plugin behind it, recording the two calls the command makes
     * instead of carrying them out. Any other method would reach for the missing plugin and throw.
     */
    private static final class RecordingConfigService extends ConfigService {
        final List<RecordedCall> calls = new ArrayList<>();

        RecordingConfigService() {
            super(null);
        }

        @Override
        public void setConfigOption(String option, String value, CommandSender sender) {
            calls.add(new RecordedCall("setConfigOption", option, value, sender));
        }

        @Override
        public void sendConfigList(CommandSender sender) {
            calls.add(new RecordedCall("sendConfigList", null, null, sender));
        }
    }
}
