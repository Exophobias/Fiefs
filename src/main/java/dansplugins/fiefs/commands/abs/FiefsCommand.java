package dansplugins.fiefs.commands.abs;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Base class for every {@code /fi} subcommand.
 *
 * <p>Replaces {@code preponderous.ponder.minecraft.bukkit.abs.AbstractPluginCommand}. The constructor
 * signature is deliberately identical so that migrating the subcommands was a one-line import change
 * each. Ponder's five helper methods ({@code sendMessageIfNoArguments}, {@code getIntSafe},
 * {@code safeEquals}, {@code extractArgumentsInsideDoubleQuotes}, ...) were not carried over: nothing
 * in this plugin called any of them.
 *
 * @author Daniel McCoy Stephenson (original Ponder-based design)
 */
public abstract class FiefsCommand {
    private final List<String> names;
    private final List<String> permissions;

    protected FiefsCommand(List<String> names, List<String> permissions) {
        this.names = names;
        this.permissions = permissions;
    }

    /** Invoked when the subcommand was given no arguments of its own. */
    public abstract boolean execute(CommandSender sender);

    /** Invoked with the subcommand's own arguments, i.e. everything after {@code /fi &lt;name&gt;}. */
    public abstract boolean execute(CommandSender sender, String[] args);

    /** The labels this subcommand answers to. Matched case-insensitively by {@code CommandService}. */
    public List<String> getNames() {
        return Collections.unmodifiableList(names);
    }

    /** Permission nodes, ANY one of which admits the sender. */
    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }
}
