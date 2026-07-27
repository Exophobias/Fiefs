package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /fi heir} - the holder of a fief names who inherits it when they depart.
 *
 * <p>A nomination and nothing more: the heir gains no authority over the fief while the holder still
 * holds it, and the nomination is dropped as soon as it is used, as soon as the fief changes hands by
 * any other route, and as soon as the nominee stops being of the fief.
 *
 * <p>The nominee must be a member of the fief, which is the same bar {@code /fi transfer} sets. That
 * keeps the "a player is in at most one fief" invariant true without a second check, and it means the
 * parent-faction constraint is satisfied at nomination time for free - every fief member is a faction
 * member. It is checked again at succession all the same, because membership can change in between.
 */
public class HeirCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public HeirCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("heir")), new ArrayList<>(Arrays.asList("fiefs.heir")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    /** Bare {@code /fi heir} reports the standing nomination rather than erroring. */
    @Override
    public boolean execute(CommandSender sender) {
        Fief fief = resolveOwnedFief(sender);
        if (fief == null) {
            return false;
        }

        Player player = (Player) sender;
        if (fief.getHeirUUID() == null) {
            player.sendMessage(Component.text("No heir is named for " + fief.getName()
                    + ". Use /fi heir (playerName) to name one.", NamedTextColor.AQUA));
            return true;
        }

        String heirName = new UUIDChecker().findPlayerNameBasedOnUUID(fief.getHeirUUID());
        player.sendMessage(Component.text("Heir to " + fief.getName() + ": " + heirName, NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Fief fief = resolveOwnedFief(sender);
        if (fief == null) {
            return false;
        }

        Player player = (Player) sender;
        String targetName = args[0];

        if (targetName.equalsIgnoreCase("clear")) {
            fief.setHeirUUID(null);
            persistentData.markDirty();
            player.sendMessage(Component.text("Heir cleared.", NamedTextColor.GREEN));
            return true;
        }

        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(Component.text("You can't name yourself as your own heir.", NamedTextColor.RED));
            return false;
        }

        UUID targetUUID = new UUIDChecker().findUUIDBasedOnPlayerName(targetName);
        if (targetUUID == null) {
            player.sendMessage(Component.text("That player wasn't found.", NamedTextColor.RED));
            return false;
        }

        if (!fief.isMember(targetUUID)) {
            player.sendMessage(Component.text("That player is not in your fief.", NamedTextColor.RED));
            return false;
        }

        fief.setHeirUUID(targetUUID);
        persistentData.markDirty();
        player.sendMessage(Component.text(targetName + " will inherit " + fief.getName()
                + " if you depart.", NamedTextColor.GREEN));
        return true;
    }

    /**
     * @return the caller's fief if they are a player, in a faction, in a fief and hold it; null
     *         otherwise, having already explained why.
     */
    private Fief resolveOwnedFief(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return null;
        }

        Player player = (Player) sender;

        FactionView faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return null;
        }

        Fief fief = persistentData.getFief(player);
        if (fief == null) {
            player.sendMessage(Component.text("You must be in a fief to use this command.", NamedTextColor.RED));
            return null;
        }

        if (!fief.isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("You must be the holder of your fief to name an heir.", NamedTextColor.RED));
            return null;
        }

        return fief;
    }
}
