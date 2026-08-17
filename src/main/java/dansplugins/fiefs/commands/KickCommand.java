package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
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
public class KickCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;
    private final SuccessionService successionService;

    public KickCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData,
                       SuccessionService successionService) {
        super(new ArrayList<>(Arrays.asList("kick")), new ArrayList<>(Arrays.asList("fiefs.kick")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        commandSender.sendMessage(Component.text("Usage: /fiefs kick (playerName)", NamedTextColor.RED));
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
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

        if (!playersFief.isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be the owner of your fief to kick members.", NamedTextColor.RED));
            return false;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Component.text("You can't kick yourself.", NamedTextColor.RED));
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

        // Look the target's fief up by their UUID — the String overload matches on fief name,
        // which is never the player's name. #144
        Fief targetsFief = persistentData.getFief(targetUUID);
        // Fief names are mutable; Patriam's stable ids are the authoritative identity.
        if (targetsFief == null || !targetsFief.getId().equals(playersFief.getId())) {
            player.sendMessage(Component.text("That player is not in your fief.", NamedTextColor.RED));
            return false;
        }

        playersFief.removeMember(targetUUID);

        persistentData.markDirty();
        Player target = Bukkit.getServer().getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(Component.text("You have been kicked from " + playersFief.getName() + " by " + player.getName() + ".", NamedTextColor.AQUA));
        }
        player.sendMessage(Component.text("Kicked.", NamedTextColor.GREEN));
        // Removing a member can move who stands to inherit, most obviously when the person kicked was
        // the presumptive successor.
        successionService.refreshSuccession(playersFief);
        return true;
    }
}
