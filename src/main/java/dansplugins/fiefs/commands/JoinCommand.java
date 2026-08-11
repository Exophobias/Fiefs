package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import dansplugins.fiefs.utils.ArgumentParser;
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
    private final SuccessionService successionService;

    public JoinCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData,
                       SuccessionService successionService) {
        super(new ArrayList<>(Arrays.asList("join")), new ArrayList<>(Arrays.asList("fiefs.join")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.successionService = successionService;
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

        String fiefName = new ArgumentParser().getFiefNameFromArguments(args);

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

        // A new member can move who stands to inherit, and this is the path a holder would use to
        // pack an elective fief with a bloc before departing. It is not gated - only a holder can
        // invite, so gating it would gate the fief's only route in - but the flip it causes is public
        // inside the fief the moment it happens.
        successionService.refreshSuccession(targetFief);

        // TODO: alert fief members that the player has joined the fief

        return true;
    }
}