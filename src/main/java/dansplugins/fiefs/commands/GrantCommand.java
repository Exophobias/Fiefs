package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.ArgumentParser;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /fi grant "Fief Name" (playerName)} - the head of a faction puts one of its fiefs into a
 * player's hands.
 *
 * <p>This is patronage, and it is the other half of reversion. A fief is held FROM a faction, so the
 * faction that granted it can grant it again: to fill a fief that has reverted with nobody to
 * inherit, or to take one out of the hands of a holder who still has it and give it to somebody else.
 * Without it, a reverted fief would sit vacant permanently, which is the dead end reverting exists to
 * avoid.
 *
 * <p>Restricted to the faction's recorded head - Medieval Factions' {@code primaryOwnerId}, the
 * identity answer, not the capability one. Granting land is an act of the person at the top, not of
 * anyone who happens to hold a powerful role, and there is exactly one of them.
 */
public class GrantCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public GrantCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("grant")), new ArrayList<>(Arrays.asList("fiefs.grant")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /fi grant \"fiefName\" (playerName)", NamedTextColor.RED));
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            return execute(sender);
        }

        FactionView faction = medievalFactionsIntegrator.getFactionForPlayer(player);
        if (faction == null) {
            return false;
        }

        if (!player.getUniqueId().equals(faction.getPrimaryOwnerId())) {
            player.sendMessage(Component.text("Only the head of " + faction.getName()
                    + " may grant its fiefs.", NamedTextColor.RED));
            return false;
        }

        // The LAST argument is the player name and everything before it is the fief name. Player
        // names never contain spaces, so this reads "Ashford Mill" and Ashford Mill alike without the
        // quotes having to be there.
        String targetName = args[args.length - 1];
        String fiefName = new ArgumentParser().getFiefNameFromArguments(Arrays.copyOf(args, args.length - 1));

        Fief fief = persistentData.getFief(fiefName);
        if (fief == null) {
            player.sendMessage(Component.text("That fief wasn't found.", NamedTextColor.RED));
            return false;
        }

        if (!fief.getFactionId().equals(faction.getId().getValue())) {
            player.sendMessage(Component.text("That fief isn't in your faction.", NamedTextColor.RED));
            return false;
        }

        UUID targetUUID = new UUIDChecker().findUUIDBasedOnPlayerName(targetName);
        if (targetUUID == null) {
            player.sendMessage(Component.text("That player wasn't found.", NamedTextColor.RED));
            return false;
        }

        // A fief is held from the faction, so its holder must be one of the faction's people.
        if (!faction.getMemberIds().contains(targetUUID)) {
            player.sendMessage(Component.text("'" + targetName + "' is not in your faction.", NamedTextColor.RED));
            return false;
        }

        Fief targetsExistingFief = persistentData.getFief(targetUUID);
        if (targetsExistingFief != null && !targetsExistingFief.isSameFief(fief)) {
            player.sendMessage(Component.text("'" + targetName + "' is already in "
                    + targetsExistingFief.getName() + ".", NamedTextColor.RED));
            return false;
        }

        if (fief.isOwner(targetUUID)) {
            player.sendMessage(Component.text("'" + targetName + "' already holds that fief.", NamedTextColor.RED));
            return false;
        }

        UUID previousHolder = fief.getOwnerUUID();

        fief.addMember(targetUUID);
        fief.setOwnerUUID(targetUUID);
        // The outgoing holder's nomination dies with their tenure; the new holder names their own.
        fief.setHeirUUID(null);
        persistentData.markDirty();

        player.sendMessage(Component.text("Granted " + fief.getName() + " to " + targetName + ".",
                NamedTextColor.GREEN));
        notify(targetUUID, Component.text(faction.getName() + " has granted you " + fief.getName() + ".",
                NamedTextColor.AQUA));
        if (previousHolder != null) {
            // The former holder keeps their membership; only the fief itself has moved.
            notify(previousHolder, Component.text(fief.getName() + " has been granted to " + targetName
                    + " by " + faction.getName() + ".", NamedTextColor.AQUA));
        }
        return true;
    }

    private void notify(UUID playerId, Component message) {
        Player recipient = Bukkit.getPlayer(playerId);
        if (recipient != null) {
            recipient.sendMessage(message);
        }
    }
}
