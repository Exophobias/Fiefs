package dansplugins.fiefs.commands;

import dansplugins.fiefs.Fiefs;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class DefaultCommand extends FiefsCommand {
    private final Fiefs fiefs;

    public DefaultCommand(Fiefs fiefs) {
        super(new ArrayList<>(Arrays.asList("default")), new ArrayList<>(Arrays.asList("fiefs.default")));
        this.fiefs = fiefs;
    }

    @Override
    public boolean execute(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Fiefs " + fiefs.getVersion());
        sender.sendMessage(ChatColor.AQUA + "Developer: Daniel McCoy Stephenson");
        sender.sendMessage(ChatColor.AQUA + "Requested by: Laughingspade");
        sender.sendMessage(ChatColor.AQUA + "Wiki: https://github.com/dmccoystephenson/Fiefs/wiki");
        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }
}