package dansplugins.fiefs.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class HelpCommand extends FiefsCommand {
    private final int maxPage = 2;

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
            default:
                sendUsageMessage(sender);
                return false;
        }
        return true;
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /fi help { 1 | 2 }");
    }

    private void sendPageOne(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Fiefs Commands Page 1/" + maxPage + "===");
        sender.sendMessage(ChatColor.AQUA + "/fi help - View a list of helpful commands.");
        sender.sendMessage(ChatColor.AQUA + "/fi list - List the fiefs in your faction.");
        sender.sendMessage(ChatColor.AQUA + "/fi join - Join a fief you've been invited to.");
        sender.sendMessage(ChatColor.AQUA + "/fi info - View your fief's or another fief's information.");
        sender.sendMessage(ChatColor.AQUA + "/fi members - View your fief's or another fief's members.");
        sender.sendMessage(ChatColor.AQUA + "/fi leave - Leave your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi checkclaim - Check which fief owns a chunk.");
        sender.sendMessage(ChatColor.AQUA + "/fi create - Create a fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi invite - Invite players to your fief.");
    }

    private void sendPageTwo(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Fiefs Commands Page 2/" + maxPage + "===");
        sender.sendMessage(ChatColor.AQUA + "/fi disband - Disband your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi claim - Claim a chunk of land for your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi unclaim - Unclaim a chunk of land for your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi desc - Alter the description of your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi rename - Rename your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi kick - Kick a player from your fief.");
        sender.sendMessage(ChatColor.AQUA + "/fi transfer - Transfer your fief to another player.");
        sender.sendMessage(ChatColor.AQUA + "/fi flags - View and alter your fief's configuration.");
        sender.sendMessage(ChatColor.AQUA + "/fi config - View and alter this plugin's config options.");
    }

}
