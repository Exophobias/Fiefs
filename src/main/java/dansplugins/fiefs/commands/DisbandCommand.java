package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.heraldry.HeraldryPresence;
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
public class DisbandCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public DisbandCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("disband")), new ArrayList<>(Arrays.asList("fiefs.disband")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player)) {
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

        if (!fief.isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be the owner of your fief to disband it.", NamedTextColor.RED));
            return false;
        }

        persistentData.removeFief(fief);
        HeraldryPresence.publicationChanged(
                fief.getId(), HeraldryPresence.PublicationChange.EXISTENCE);
        player.sendMessage(Component.text("Fief disbanded.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public boolean execute(CommandSender commandSender, String[] strings) {
        return execute(commandSender);
    }

}
