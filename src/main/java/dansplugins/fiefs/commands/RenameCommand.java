package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.utils.ArgumentParser;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class RenameCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public RenameCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("rename")), new ArrayList<>(Arrays.asList("fiefs.rename")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        commandSender.sendMessage(ChatColor.RED + "Usage: /fiefs rename \"new name\"");
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
            player.sendMessage(ChatColor.RED + "You must be the owner of your fief to rename it.");
            return false;
        }

        ArgumentParser argumentParser = new ArgumentParser();
        ArrayList<String> singleQuoteArgs = new ArrayList<>(argumentParser.getArgumentsInsideDoubleQuotes(args));

        if (singleQuoteArgs.size() == 0) {
            player.sendMessage(ChatColor.RED + "You must put the new name of your fief in between double quotes.");
            return false;
        }

        String newName = singleQuoteArgs.get(0);

        if (persistentData.isNameTaken(newName)) {
            player.sendMessage(ChatColor.RED + "That name is taken.");
            return false;
        }

        // Claims are keyed by the fief's display name, so they must be re-pointed in the same
        // breath. Without this the fief orphans all its land: its demesne count resets to zero (so it
        // can claim its whole allowance again on top of land it already holds), it can no longer
        // unclaim its own chunks, disbanding stops cleaning them up, and territory alerts announce
        // the old name.
        String oldName = playersFief.getName();
        for (ClaimedChunk claimedChunk : persistentData.getClaimedChunks()) {
            if (claimedChunk.getFief().equalsIgnoreCase(oldName)) {
                claimedChunk.setFief(newName);
            }
        }

        playersFief.setName(newName);

        persistentData.markDirty();
        player.sendMessage(ChatColor.GREEN + "Fief renamed.");
        return true;
    }
}
