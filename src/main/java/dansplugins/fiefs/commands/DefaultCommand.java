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
    private final Fiefs fiefs;

    public DefaultCommand(Fiefs fiefs) {
        super(new ArrayList<>(Arrays.asList("default")), new ArrayList<>(Arrays.asList("fiefs.default")));
        this.fiefs = fiefs;
    }

    @Override
    public boolean execute(CommandSender sender) {
        sender.sendMessage(Component.text("Fiefs " + fiefs.getVersion(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Developer: Daniel McCoy Stephenson", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Requested by: Laughingspade", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Wiki: https://github.com/dmccoystephenson/Fiefs/wiki", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }
}