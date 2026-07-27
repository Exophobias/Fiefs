package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dansplugins.fiefs.commands.abs.FiefsCommand;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class LeaveCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;
    private final SuccessionService successionService;

    public LeaveCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData,
                        SuccessionService successionService) {
        super(new ArrayList<>(Arrays.asList("leave")), new ArrayList<>(Arrays.asList("fiefs.leave")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }

        Player player = (Player) sender;

        FactionView faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        Fief fief = persistentData.getFief(player);
        if (fief == null) {
            player.sendMessage(Component.text("You must be in a fief to use this command.", NamedTextColor.RED));
            return false;
        }

        // A holder walking away is a succession, not a demolition. This used to disband the fief
        // outright, which destroyed its land, its members' home and its name because one player left,
        // and it made /fi leave a way for a holder to take a settlement down with them. The fief now
        // passes to the heir, then to the longest-standing member, and only reverts to the faction if
        // there is genuinely nobody left.
        if (fief.isOwner(player.getUniqueId())) {
            SuccessionService.Succession succession = successionService.succeedFrom(fief, player.getUniqueId());
            if (succession.reverted()) {
                player.sendMessage(Component.text("Left. " + fief.getName() + " has reverted to "
                        + persistentData.getFactionNameOfFief(fief) + ".", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Left. " + fief.getName() + " has passed to "
                        + new UUIDChecker().findPlayerNameBasedOnUUID(succession.newOwnerId()) + ".",
                        NamedTextColor.GREEN));
            }
            return true;
        }

        fief.removeMember(player.getUniqueId());

        persistentData.markDirty();
        player.sendMessage(Component.text("Left.", NamedTextColor.GREEN));

        // TODO: inform fief members that the player has left the fief

        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }
}