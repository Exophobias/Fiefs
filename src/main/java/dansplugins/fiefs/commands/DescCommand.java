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
import dansplugins.fiefs.utils.ArgumentParser;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Daniel McCoy Stephenson
 */
public class DescCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public DescCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("desc")), new ArrayList<>(Arrays.asList("fiefs.desc")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        commandSender.sendMessage(Component.text("Usage: /fiefs desc \"new description\"", NamedTextColor.RED));
        return false;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
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

        ArgumentParser argumentParser = new ArgumentParser();
        ArrayList<String> singleQuoteArgs = new ArrayList<>(argumentParser.getArgumentsInsideDoubleQuotes(args));

        if (singleQuoteArgs.size() == 0) {
            player.sendMessage(Component.text("New description must be between double quotes.", NamedTextColor.RED));
            return false;
        }

        String newDescription = singleQuoteArgs.get(0);

        playersFief.setDescription(newDescription);

        persistentData.markDirty();
        return true;
    }

}
