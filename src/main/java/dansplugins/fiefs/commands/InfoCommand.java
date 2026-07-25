package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.faction.MfFaction;
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
public class InfoCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public InfoCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("info")), new ArrayList<>(Arrays.asList("fiefs.info")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {

        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;

        MfFaction faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        if (args.length > 0) {
            String fiefName = args[0];
            Fief fief = persistentData.getFief(fiefName);
            if (fief == null) {
                player.sendMessage(ChatColor.RED + "That fief wasn't found.");
                return false;
            }

            persistentData.sendFiefInfoToPlayer(player, fief);
            return true;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            player.sendMessage(ChatColor.RED + "You must be in a fief to use this command.");
            return false;
        }

        persistentData.sendFiefInfoToPlayer(player, playersFief);
        return true;
    }

}
