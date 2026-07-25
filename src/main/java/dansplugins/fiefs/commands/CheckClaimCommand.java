package dansplugins.fiefs.commands;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.ChunkService;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class CheckClaimCommand extends FiefsCommand {
    private final PersistentData persistentData;
    private final ChunkService chunkService;

    public CheckClaimCommand(PersistentData persistentData, ChunkService chunkService) {
        super(new ArrayList<>(Arrays.asList("checkclaim")), new ArrayList<>(Arrays.asList("fiefs.checkclaim")));
        this.persistentData = persistentData;
        this.chunkService = chunkService;
    }

    @Override
    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;

        Chunk chunk = player.getLocation().getChunk();
        ClaimedChunk claimedChunk = chunkService.getClaimedChunk(chunk);
        if (claimedChunk != null) {
            Fief owningFief = persistentData.getFief(claimedChunk.getFief());
            if (owningFief != null) {
                player.sendMessage(ChatColor.AQUA + "This land is claimed by " + owningFief.getName() + " and is located in " + persistentData.getFactionNameOfFief(owningFief));
            }
            else {
                player.sendMessage(ChatColor.AQUA + "This land is claimed by " + claimedChunk.getFief() + ".");
            }
        }
        else {
            player.sendMessage(ChatColor.GREEN + "This land is currently not claimed by a fief.");
        }
        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }
}