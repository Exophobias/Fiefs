package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
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
 *
 * <h2>The one command a government layer takes away</h2>
 *
 * <p>Under a form where the holder's own nomination is not the rule, this refuses, and it refuses
 * with a sentence the rule wrote. <b>Fiefs never names a government type here.</b> It asks the
 * standing answer whether the holder may name an heir at all and prints whatever refusal comes back,
 * which is the only arrangement where the refusal cannot drift out of step with the rule that caused
 * it.
 *
 * <p>This is the change players complain about, and the refusal message is the only thing that makes
 * it land as a constitution lesson rather than as a bug.
 */
public class HeirCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;
    private final SuccessionService successionService;

    public HeirCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData,
                       SuccessionService successionService) {
        super(new ArrayList<>(Arrays.asList("heir")), new ArrayList<>(Arrays.asList("fiefs.heir")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    /** Bare {@code /fi heir} reports the standing nomination rather than erroring. */
    @Override
    public boolean execute(CommandSender sender) {
        Fief fief = resolveOwnedFief(sender);
        if (fief == null) {
            return false;
        }

        Player player = (Player) sender;
        // Refused here too, not only on the setting form. The bare command's own message ends "Use
        // /fi heir (playerName) to name one", which under a form that forbids naming one is the
        // plugin inviting a player to do something it is about to refuse.
        if (refusedByTheRealmsGovernment(player, fief)) {
            return false;
        }

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

        if (refusedByTheRealmsGovernment(player, fief)) {
            return false;
        }

        if (targetName.equalsIgnoreCase("clear")) {
            fief.setHeirUUID(null);
            persistentData.markDirty();
            player.sendMessage(Component.text("Heir cleared.", NamedTextColor.GREEN));
            // Clearing moves the answer as surely as naming one does, and the fief is entitled to
            // hear about it either way.
            successionService.refreshSuccession(fief);
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
        successionService.refreshSuccession(fief);
        return true;
    }

    /**
     * Whether the realm's government decides this fief's succession rather than its holder, having
     * already said so in the rule's own words.
     *
     * <p>Also the one place the widening-on-failure notice is printed. When a policy is stood down
     * after failing, this command starts working again, which is a permission that widens on failure.
     * It must widen, because the ladder now genuinely reads the nomination and refusing would be the
     * plugin lying about what decides. It must not widen quietly, so the first holder to reach it
     * after a drop is told why the command they were refused now works.
     */
    private boolean refusedByTheRealmsGovernment(Player player, Fief fief) {
        String refusal = successionService.heirRefusalFor(fief);
        if (refusal != null) {
            // The rule's own sentence, printed verbatim. Fiefs holds no government forms and must
            // never compose a sentence naming one: a message written here drifts out of step with the
            // rule the moment the rule changes, and the player is then told something that was true
            // last month.
            player.sendMessage(Component.text(refusal, NamedTextColor.RED));
            return true;
        }

        // Asked AFTER the refusal and not before it, which matters in exactly one case and it is the
        // case this exists for: when the policy throws while answering the line above, this very call
        // is the first one after the drop. Asking first would defer the notice to the next /fi heir
        // and leave this one silently succeeding where it would have been refused a moment earlier.
        if (successionService.claimNominationDecidesAgainNotice(player.getUniqueId())) {
            player.sendMessage(Component.text("Fiefs' government layer has failed and been stood down "
                    + "until the server restarts, so your nomination decides again. It was refused "
                    + "until now because your realm's government named who inherits this fief.",
                    NamedTextColor.YELLOW));
        }
        return false;
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
