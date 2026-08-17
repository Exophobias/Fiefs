package dansplugins.fiefs.commands;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class WhoisCommand extends FiefsCommand {
    private final PersistentData persistentData;

    public WhoisCommand(PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("whois")), new ArrayList<>(Arrays.asList("fiefs.whois")));
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /fi whois (playerName)", NamedTextColor.RED));
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return execute(sender);
        }

        String targetName = args[0];

        UUIDChecker uuidChecker = new UUIDChecker();
        UUID targetUUID = uuidChecker.findUUIDBasedOnPlayerName(targetName);
        if (targetUUID == null) {
            sender.sendMessage(Component.text("That player wasn't found.", NamedTextColor.RED));
            return false;
        }

        Fief targetsFief = persistentData.getFief(targetUUID);
        if (targetsFief == null) {
            sender.sendMessage(Component.text(targetName + " is not a member of a fief.", NamedTextColor.AQUA));
            return true;
        }

        sender.sendMessage(Component.text(targetName + " is a member of " + targetsFief.getName() + ".", NamedTextColor.AQUA));
        return true;
    }
}
