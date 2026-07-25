package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.Logger;
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
public class CreateCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;
    private final Logger logger;

    public CreateCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData, Logger logger) {
        super(new ArrayList<>(Arrays.asList("create")), new ArrayList<>(Arrays.asList("fiefs.create")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.logger = logger;
    }

    @Override
    public boolean execute(CommandSender commandSender) {
        commandSender.sendMessage(ChatColor.RED + "Usage: /fiefs create \"name\"");
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

        if (persistentData.getFief(player) != null) {
            player.sendMessage(ChatColor.RED + "You can't create a fief if you're already in a fief.");
            return false;
        }

        ArgumentParser argumentParser = new ArgumentParser();
        ArrayList<String> singleQuoteArgs = new ArrayList<>(argumentParser.getArgumentsInsideDoubleQuotes(args));

        if (singleQuoteArgs.size() == 0) {
            player.sendMessage(ChatColor.RED + "You must put the name of the fief you want to create in between double quotes.");
            return false;
        }

        String name = singleQuoteArgs.get(0);

        if (persistentData.isNameTaken(name)) {
            player.sendMessage(ChatColor.RED + "That name is taken.");
            return false;
        }

        Fief fief = new Fief(medievalFactionsIntegrator, name, player.getUniqueId(), faction.getId().getValue(), logger);
        persistentData.addFief(fief);
        player.sendMessage(ChatColor.GREEN + "Fief created.");
        return true;
    }
}