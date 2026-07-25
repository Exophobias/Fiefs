package dansplugins.fiefs.services;

import dansplugins.fiefs.commands.abs.FiefsCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final Map<String, FiefsCommand> commandsByName = new HashMap<>();
    private String notFoundMessage = "That command wasn't found.";

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
            return false;
        }

        FiefsCommand command = commandsByName.get(args[0].toLowerCase(Locale.ROOT));
        if (command == null) {
            sender.sendMessage(ChatColor.RED + notFoundMessage);
            return false;
        }

        if (!hasAnyPermission(sender, command)) {
            return false;
        }

        // The subcommand sees only its own arguments, never its own name.
        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        return remainingArgs.length == 0
                ? command.execute(sender)
                : command.execute(sender, remainingArgs);
    }

    private boolean hasAnyPermission(CommandSender sender, FiefsCommand command) {
        for (String permission : command.getPermissions()) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "In order to use this command, you need one of the following permissions: "
                + String.join(", ", command.getPermissions()));
        return false;
    }
}
