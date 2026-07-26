package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
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
        commandSender.sendMessage(Component.text("Usage: /fiefs join (fiefName)", NamedTextColor.RED));
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

        Fief fief = persistentData.getFief(player);
        if (fief != null) {
            player.sendMessage(Component.text("You're already in a fief.", NamedTextColor.RED));
            return false;
        }

        String fiefName = args[0];

        Fief targetFief = persistentData.getFief(fiefName);

        if (targetFief == null) {
            player.sendMessage(Component.text("That fief wasn't found.", NamedTextColor.RED));
            return false;
        }

        if (!targetFief.getFactionId().equals(faction.getId().getValue())) {
            player.sendMessage(Component.text("That fief isn't in your faction.", NamedTextColor.RED));
            return false;
        }

        if (!targetFief.isInvited(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not invited to this fief.", NamedTextColor.RED));
            return false;
        }

        targetFief.addMember(player.getUniqueId());

        persistentData.markDirty();
        player.sendMessage(Component.text("Joined.", NamedTextColor.GREEN));

        // TODO: alert fief members that the player has joined the fief

        return true;
    }
}