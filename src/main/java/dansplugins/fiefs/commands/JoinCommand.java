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
public class JoinCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public JoinCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("join")), new ArrayList<>(Arrays.asList("fiefs.join")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        commandSender.sendMessage(ChatColor.RED + "Usage: /fiefs join (fiefName)");
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

        Fief fief = persistentData.getFief(player);
        if (fief != null) {
            player.sendMessage(ChatColor.RED + "You're already in a fief.");
            return false;
        }

        String fiefName = args[0];

        Fief targetFief = persistentData.getFief(fiefName);

        if (targetFief == null) {
            player.sendMessage(ChatColor.RED + "That fief wasn't found.");
            return false;
        }

        if (!targetFief.getFactionId().equals(faction.getId().getValue())) {
            player.sendMessage(ChatColor.RED + "That fief isn't in your faction.");
            return false;
        }

        if (!targetFief.isInvited(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are not invited to this fief.");
            return false;
        }

        targetFief.addMember(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Joined.");

        // TODO: alert fief members that the player has joined the fief

        return true;
    }
}