package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.heraldry.HeraldryPresence;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        commandSender.sendMessage(Component.text("Usage: /fiefs rename \"new name\"", NamedTextColor.RED));
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }

        Player player = (Player) sender;

        FactionView faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            player.sendMessage(Component.text("You must be in a fief to use this command.", NamedTextColor.RED));
            return false;
        }

        if (!playersFief.isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be the owner of your fief to rename it.", NamedTextColor.RED));
            return false;
        }

        ArgumentParser argumentParser = new ArgumentParser();
        ArrayList<String> singleQuoteArgs = new ArrayList<>(argumentParser.getArgumentsInsideDoubleQuotes(args));

        if (singleQuoteArgs.size() == 0) {
            player.sendMessage(Component.text("You must put the new name of your fief in between double quotes.", NamedTextColor.RED));
            return false;
        }

        String newName = singleQuoteArgs.get(0);

        if (persistentData.isNameTaken(newName)) {
            player.sendMessage(Component.text("That name is taken.", NamedTextColor.RED));
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
        HeraldryPresence.publicationChanged(
                playersFief.getId(), HeraldryPresence.PublicationChange.NAME);
        player.sendMessage(Component.text("Fief renamed.", NamedTextColor.GREEN));
        return true;
    }
}
