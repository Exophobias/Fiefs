package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author Daniel McCoy Stephenson
 */
public class InviteCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public InviteCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("invite")), new ArrayList<>(Arrays.asList("fiefs.invite")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }

        Player player = (Player) sender;

        FactionView playersFaction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (playersFaction == null) {
            return false;
        }

        Fief playersFief = persistentData.getFief(player);
        if (playersFief == null) {
            player.sendMessage(Component.text("You must be in a fief to use this command.", NamedTextColor.RED));
            return false;
        }

        if (!playersFief.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be the owner of your fief to invite others.", NamedTextColor.RED));
            return false;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /fiefs invite (playerName)", NamedTextColor.RED));
            return false;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Component.text("You can't invite yourself.", NamedTextColor.RED));
            return false;
        }

        UUIDChecker uuidChecker = new UUIDChecker();
        UUID targetUUID = uuidChecker.findUUIDBasedOnPlayerName(targetName);

        if (targetUUID == null) {
            player.sendMessage(Component.text("That player wasn't found.", NamedTextColor.RED));
            return false;
        }

        FactionView targetsFaction = medievalFactionsIntegrator.getAPI().getFactionByPlayer(targetUUID);
        // Compared by id, not by name: faction names are mutable and not guaranteed unique.
        if (targetsFaction == null || !targetsFaction.getId().equals(playersFaction.getId())) {
            player.sendMessage(Component.text("'" + targetName + "' is not in your faction.", NamedTextColor.RED));
            return false;
        }

        Fief targetsFief = persistentData.getFief(targetUUID);
        if (targetsFief != null) {
            player.sendMessage(Component.text("That player is already in " + targetsFief.getName(), NamedTextColor.RED));
            return false;
        }

        playersFief.invitePlayer(targetUUID);

        persistentData.markDirty();
        Player target = Bukkit.getServer().getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Component.text("You have been invited to " + playersFief.getName() + ". Type /fiefs join (fiefName) to join.", NamedTextColor.AQUA));
        }
        player.sendMessage(Component.text("Invited.", NamedTextColor.GREEN));
        return true;
    }
}