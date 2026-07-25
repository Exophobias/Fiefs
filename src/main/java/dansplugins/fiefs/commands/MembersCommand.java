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
public class MembersCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public MembersCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("members")), new ArrayList<>(Arrays.asList("fiefs.members")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = (Player) sender;

        MfFaction faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            player.sendMessage(ChatColor.RED + "You must be in a fief to use this command.");
            return false;
        }

        playersFief.sendMembersListToPlayer(player);
        return true;
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

        String fiefName = args[0];
        Fief fief = persistentData.getFief(fiefName);
        if (fief == null) {
            player.sendMessage(ChatColor.RED + "That fief wasn't found.");
            return false;
        }

        fief.sendMembersListToPlayer(player);
        return true;
    }
}