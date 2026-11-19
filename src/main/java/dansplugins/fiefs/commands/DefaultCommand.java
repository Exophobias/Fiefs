package dansplugins.fiefs.commands;

import dansplugins.fiefs.Fiefs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class DefaultCommand extends FiefsCommand {
    private final String version;

    public DefaultCommand(Fiefs fiefs) {
        this(fiefs.getVersion());
    }

    public DefaultCommand(String version) {
        super(new ArrayList<>(Arrays.asList("default")), new ArrayList<>(Arrays.asList("fiefs.default")));
        this.version = version;
    }

    @Override
    public boolean execute(CommandSender sender) {
        if (!sender.hasPermission("fiefs.default")) {
            sender.sendMessage(Component.text("In order to use this command, you need one of the following permissions: "
                    + String.join(", ", getPermissions()), NamedTextColor.RED));
            return false;
        }
        sender.sendMessage(Component.text("Fiefs " + version, NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Developer: Daniel McCoy Stephenson", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Requested by: Laughingspade", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Wiki: https://github.com/Dans-Plugins/Fiefs/wiki", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }
}