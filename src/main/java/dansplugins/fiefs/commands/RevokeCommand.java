package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.utils.ArgumentParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /fi revoke "Fief Name"} - the head of a faction takes a fief back into the faction's hands.
 *
 * <p>The fief itself is untouched: it keeps its name, its members, its land and its flags, and only
 * stops having a holder. That is the same state a fief reaches when its holder departs with nobody
 * eligible to inherit, so there is one vacant state and one way out of it, {@code /fi grant}.
 *
 * <p>Deliberately not a disband. Revoking is about who holds a fief, never about whether it exists,
 * and taking land off a player should not delete a settlement several other players live in.
 */
public class RevokeCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public RevokeCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("revoke")), new ArrayList<>(Arrays.asList("fiefs.revoke")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    @Override
    public boolean execute(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /fi revoke \"fiefName\"", NamedTextColor.RED));
        return false;
    }

    @Override
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

        if (!player.getUniqueId().equals(faction.getPrimaryOwnerId())) {
            player.sendMessage(Component.text("Only the head of " + faction.getName()
                    + " may revoke its fiefs.", NamedTextColor.RED));
            return false;
        }

        String fiefName = new ArgumentParser().getFiefNameFromArguments(args);
        Fief fief = persistentData.getFief(fiefName);
        if (fief == null) {
            player.sendMessage(Component.text("That fief wasn't found.", NamedTextColor.RED));
            return false;
        }

        if (!fief.getFactionId().equals(faction.getId().getValue())) {
            player.sendMessage(Component.text("That fief isn't in your faction.", NamedTextColor.RED));
            return false;
        }

        if (fief.isVacant()) {
            player.sendMessage(Component.text(fief.getName() + " is already held by "
                    + faction.getName() + ".", NamedTextColor.RED));
            return false;
        }

        UUID previousHolder = fief.getOwnerUUID();

        fief.setOwnerUUID(null);
        fief.setHeirUUID(null);
        persistentData.markDirty();

        player.sendMessage(Component.text("Revoked. " + fief.getName() + " is held by "
                + faction.getName() + " until you grant it.", NamedTextColor.GREEN));

        Component message = Component.text(faction.getName() + " has revoked " + fief.getName() + ".",
                NamedTextColor.AQUA);
        for (UUID memberId : fief.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(message);
            }
        }
        if (previousHolder != null && !fief.isMember(previousHolder)) {
            Player formerHolder = Bukkit.getPlayer(previousHolder);
            if (formerHolder != null) {
                formerHolder.sendMessage(message);
            }
        }
        return true;
    }
}
