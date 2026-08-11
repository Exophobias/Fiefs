package dansplugins.fiefs.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class HelpCommand extends FiefsCommand {
    private final int maxPage = 3;

    public HelpCommand() {
        super(new ArrayList<>(Arrays.asList("help")), new ArrayList<>(Arrays.asList("fiefs.help")));
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsageMessage(sender);
            return false;
        }

        String page = args[0];

        switch(page) {
            case "1":
                sendPageOne(sender);
                break;
            case "2":
                sendPageTwo(sender);
                break;
            case "3":
                sendPageThree(sender);
                break;
            default:
                sendUsageMessage(sender);
                return false;
        }
        return true;
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /fi help { 1 | 2 | 3 }", NamedTextColor.RED));
    }

    private void sendPageOne(CommandSender sender) {
        sender.sendMessage(Component.text("=== Fiefs Commands Page 1/" + maxPage + "===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi help - View a list of helpful commands.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi list - List the fiefs in your faction.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi join - Join a fief you've been invited to.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi info - View your fief's or another fief's information.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi members - View your fief's or another fief's members.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi leave - Leave your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi checkclaim - Check which fief owns a chunk.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi create - Create a fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi invite - Invite players to your fief.", NamedTextColor.AQUA));
    }

    private void sendPageTwo(CommandSender sender) {
        sender.sendMessage(Component.text("=== Fiefs Commands Page 2/" + maxPage + "===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi disband - Disband your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi claim - Claim a chunk of land for your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi unclaim - Unclaim a chunk of land for your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi desc - Alter the description of your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi rename - Rename your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi kick - Kick a player from your fief.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi transfer - Transfer your fief to another player.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi flags - View and alter your fief's configuration.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi config - View and alter this plugin's config options.", NamedTextColor.AQUA));
    }

    private void sendPageThree(CommandSender sender) {
        sender.sendMessage(Component.text("=== Fiefs Commands Page 3/" + maxPage + "===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi heir - Name who inherits your fief if you depart.", NamedTextColor.AQUA));
        // Permission-filtered, unlike the lines around it, because this one is new and there is no
        // reason to list a command the reader cannot run. The rest of this page predates the
        // convention and is left alone rather than changed under cover of an unrelated feature.
        if (sender.hasPermission("fiefs.succession")) {
            sender.sendMessage(Component.text("/fi succession - See what decides who inherits a fief, and who that is.", NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text("/fi grant - Grant one of your faction's fiefs to a member. Head of the faction only.", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fi revoke - Take one of your faction's fiefs back. Head of the faction only.", NamedTextColor.AQUA));
    }

}
