package dansplugins.fiefs.commands;

import dansplugins.fiefs.services.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.utils.ArgumentParser;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class ConfigCommand extends FiefsCommand {
    private final ConfigService configService;

    public ConfigCommand(ConfigService configService) {
        super(new ArrayList<>(Arrays.asList("config")), new ArrayList<>(Arrays.asList("fiefs.config")));
        this.configService = configService;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Sub-commands: show, set", NamedTextColor.RED));
            return false;
        }

        if (args[0].equalsIgnoreCase("show")) {
            configService.sendConfigList(sender);
            return true;
        }
        else if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /fi config set (option) (value)", NamedTextColor.RED));
                return false;
            }
            String option = args[1];

            String value = "";
            if (option.equalsIgnoreCase("denyUsageMessage") || option.equalsIgnoreCase("denyCreationMessage")) {
                ArgumentParser argumentParser = new ArgumentParser();
                ArrayList<String> singleQuoteArgs = new ArrayList<>(argumentParser.getArgumentsInsideDoubleQuotes(args));
                if (singleQuoteArgs.size() == 0) {
                    sender.sendMessage(Component.text("New message must be in between double quotes.", NamedTextColor.RED));
                    return false;
                }
                value = singleQuoteArgs.get(0);
            }
            else {
                value = args[2];
            }

            configService.setConfigOption(option, value, sender);
            return true;
        }
        else {
            sender.sendMessage(Component.text("Sub-commands: show, set", NamedTextColor.RED));
            return false;
        }
    }
}