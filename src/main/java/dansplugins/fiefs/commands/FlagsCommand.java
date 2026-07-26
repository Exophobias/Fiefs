package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class FlagsCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public FlagsCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("flags")), new ArrayList<>(Arrays.asList("fiefs.flags")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;

        FactionView faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            player.sendMessage(ChatColor.RED + "You must be in a fief to use this command.");
            return false;
        }

        if (!playersFief.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be the owner of your fief to change its flags.");
            return false;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Valid sub-commands: show, set");
            return false;
        }

        if (args[0].equalsIgnoreCase("show")) {
            playersFief.getFlags().sendFlagList(player);
        }
        else if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /fi flags set (flag) (value)");
                return false;
            }
            else {
                playersFief.getFlags().setFlag(args[1], args[2], player);
                persistentData.markDirty();
            }
        }
        else {
            player.sendMessage(ChatColor.RED + "Valid sub-commands: show, set");
        }
        return true;
    }

}
