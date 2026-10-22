package dansplugins.fiefs.services;

import dansplugins.fiefs.commands.abs.FiefsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Dispatches {@code /fi &lt;subcommand&gt; [args...]} to the matching {@link FiefsCommand}.
 *
 * <p>Replaces {@code preponderous.ponder.minecraft.bukkit.services.CommandService}. Behaviour is
 * preserved except where noted:
 *
 * <ul>
 *   <li><b>Subcommand matching is now case-INSENSITIVE.</b> Ponder matched with
 *       {@code getNames().contains(args[0])}, i.e. {@code String.equals}, so {@code /fi Claim} used to
 *       answer "That command wasn't found." This is a deliberate behaviour change, not an accident.</li>
 *   <li><b>Lookup is a map, not a linear scan</b> over the command list for every invocation.</li>
 *   <li>Ponder also re-checked that the typed label was one of the plugin's declared commands. That is
 *       redundant: Bukkit only routes a label to this plugin's {@code onCommand} if it is declared in
 *       plugin.yml, and Fiefs declares no aliases.</li>
 * </ul>
 *
 * <p>Permission handling matches Ponder exactly: the sender needs <b>any one</b> of a subcommand's
 * declared permissions, and is told which ones would have worked if they have none.
 */
public class CommandService {
    private static final Set<String> PROTECTED_VERBS = Set.of("revoke", "kick", "disband", "transfer", "grant");
    private final BiFunction<UUID, String, String> governance;
    private final Map<String, FiefsCommand> commandsByName = new HashMap<>();
    private String notFoundMessage = "That command wasn't found.";

    public CommandService() {
        this((actor, verb) -> null);
    }

    public CommandService(BiFunction<UUID, String, String> governance) {
        this.governance = Objects.requireNonNull(governance, "governance");
    }

    public void initialize(List<FiefsCommand> commands, String notFoundMessage) {
        this.notFoundMessage = notFoundMessage;
        commandsByName.clear();
        for (FiefsCommand command : commands) {
            for (String name : command.getNames()) {
                commandsByName.put(name.toLowerCase(Locale.ROOT), command);
            }
        }
    }

    public boolean interpretAndExecuteCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return new dansplugins.fiefs.commands.HelpCommand().execute(sender);
        }

        FiefsCommand command = commandsByName.get(args[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            sender.sendMessage(Component.text(notFoundMessage, NamedTextColor.RED));
            new dansplugins.fiefs.commands.HelpCommand().execute(sender);
            return false;
        }

        if (!hasAnyPermission(sender, command)) {
            return false;
        }

        // Check the resolved command's canonical verb, so aliases and namespaced routes cannot
        // evade the same rally freeze that protects faction membership and offices.
        String canonical = command.getNames().getFirst().toLowerCase(Locale.ROOT);
        if (sender instanceof Player player && PROTECTED_VERBS.contains(canonical)) {
            String refusal;
            try {
                refusal = governance.apply(player.getUniqueId(), "fief." + canonical);
            } catch (RuntimeException | LinkageError unavailable) {
                refusal = "Faction governance is temporarily unavailable; ask staff to check PatriamMFAddon.";
            }
            if (refusal != null) {
                sender.sendMessage(Component.text(refusal, NamedTextColor.RED));
                return true;
            }
        }

        // The subcommand sees only its own arguments, never its own name.
        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        return remainingArgs.length == 0
                ? command.execute(sender)
                : command.execute(sender, remainingArgs);
    }

    public List<String> complete(CommandSender sender, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) return commandsByName.entrySet().stream()
            .filter(e -> e.getValue().getPermissions().stream().anyMatch(sender::hasPermission))
            .map(Map.Entry::getKey).filter(name -> name.startsWith(prefix)).sorted().toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("help") && sender.hasPermission("fiefs.help")
                && new dansplugins.fiefs.commands.HelpCommand().hasStaff(sender) && "staff".startsWith(prefix))
            return List.of("staff");
        return List.of();
    }

    private boolean hasAnyPermission(CommandSender sender, FiefsCommand command) {
        for (String permission : command.getPermissions()) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        sender.sendMessage(Component.text("In order to use this command, you need one of the following permissions: "
                + String.join(", ", command.getPermissions()), NamedTextColor.RED));
        return false;
    }
}
