package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionId;
import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.SuccessionService;
import dansplugins.fiefs.utils.ArgumentParser;
import dansplugins.fiefs.utils.UUIDChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * {@code /fi succession} - what decides who inherits this fief, and who that is today.
 *
 * <p><b>This command is the anti-silence surface of the whole succession feature</b>, which is why it
 * lives in Fiefs and not in the plugin that supplies the rule. The failure being designed against is
 * a government layer that is installed, believed to be working, and quietly deciding nothing: on that
 * server "the old rule ran" and "the new rule happened to agree" are indistinguishable, and every
 * fief on it inherits by a rule its realm does not use. A readout that needed the government layer in
 * order to report on the government layer would be worthless against exactly that, so this answers
 * on a server with no such layer at all, and says so in words a player reads rather than in a log
 * line an operator might.
 *
 * <p>Three states, three visibly different pages: no layer installed, a layer answering, and a layer
 * that failed and has been stood down. A player can tell which server they are on without asking.
 *
 * <p>Not public. A realm's government form is server news that wars are planned around; a
 * three-person fief's succession is not. The audience is the fief's own members and the parent
 * faction's recorded head, who is the lord of the fief and holds the escape hatch in
 * {@code /fi grant} and {@code /fi revoke}.
 */
public class SuccessionCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;
    private final SuccessionService successionService;

    public SuccessionCommand(MedievalFactionsIntegrator medievalFactionsIntegrator,
                             PersistentData persistentData, SuccessionService successionService) {
        super(new ArrayList<>(Arrays.asList("succession")), new ArrayList<>(Arrays.asList("fiefs.succession")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
        this.successionService = successionService;
    }

    /** Bare {@code /fi succession} reports on the caller's own fief. */
    @Override
    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }
        Player player = (Player) sender;

        Fief fief = persistentData.getFief(player);
        if (fief == null) {
            player.sendMessage(Component.text("You must be in a fief to use this command, or name one: "
                    + "/fi succession (fiefName)", NamedTextColor.RED));
            return false;
        }
        return report(player, fief);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return false;
        }
        Player player = (Player) sender;

        Fief fief = persistentData.getFief(new ArgumentParser().getFiefNameFromArguments(args));
        if (fief == null) {
            player.sendMessage(Component.text("That fief wasn't found.", NamedTextColor.RED));
            return false;
        }
        if (!mayRead(player, fief)) {
            player.sendMessage(Component.text("Only that fief's members and the head of the faction it "
                    + "is held from may read its succession.", NamedTextColor.RED));
            return false;
        }
        return report(player, fief);
    }

    /**
     * The head is included with the members because they are the lord of the fief: they granted it,
     * they can revoke it, and they are the only person who can regrant it once it reverts.
     */
    private boolean mayRead(Player player, Fief fief) {
        if (fief.isMember(player.getUniqueId())) {
            return true;
        }
        FactionView faction = medievalFactionsIntegrator.getAPI().getFaction(new FactionId(fief.getFactionId()));
        return faction != null && player.getUniqueId().equals(faction.getPrimaryOwnerId());
    }

    private boolean report(Player player, Fief fief) {
        SuccessionService.StandingAnswer answer = successionService.standingAnswerFor(fief);

        player.sendMessage(Component.text("=== Succession of " + fief.getName() + " ===", NamedTextColor.AQUA));

        if (successionService.isPolicyStoodDown()) {
            // Said before the rule, because it changes what the rule below means. A player who reads
            // the ladder first and this second has already concluded their realm's form is being
            // honoured.
            player.sendMessage(Component.text("Fiefs' government layer failed and has been stood down "
                    + "until the server restarts.", NamedTextColor.RED));
            player.sendMessage(Component.text("The ordinary ladder decides: its holder's named heir, "
                    + "then its longest-standing member.", NamedTextColor.AQUA));
        }

        player.sendMessage(Component.text("Rule: " + answer.rule() + ".", NamedTextColor.AQUA));

        if (answer.presumptive() == null) {
            player.sendMessage(Component.text("Stands to revert to "
                    + persistentData.getFactionNameOfFief(fief) + ": nobody in it could inherit it.",
                    NamedTextColor.AQUA));
        } else {
            String name = new UUIDChecker().findPlayerNameBasedOnUUID(answer.presumptive());
            if (answer.fromPolicy()) {
                // A sentence of the rule's own, so it goes on its own line rather than being welded
                // to one of Fiefs'. Fiefs never composes a sentence that describes a government form.
                player.sendMessage(Component.text("Stands to pass to: " + name + ".", NamedTextColor.AQUA));
                if (answer.explanation() != null) {
                    player.sendMessage(Component.text("  " + SuccessionService.sentence(answer.explanation()),
                            NamedTextColor.GRAY));
                }
            } else {
                player.sendMessage(Component.text("Stands to pass to: " + name + ", "
                        + answer.explanation() + ".", NamedTextColor.AQUA));
            }
        }

        if (!answer.fromPolicy() && !successionService.isPolicyStoodDown()) {
            player.sendMessage(Component.text("No government layer is installed, so a fief does not "
                    + "follow its realm's form.", NamedTextColor.GRAY));
        }

        staleNominationNotice(player, fief, answer);
        return true;
    }

    /**
     * A nomination made while the realm allowed one, standing under a form that does not.
     *
     * <p>Never cleared, only ignored, because clearing is destructive across a temporary change of
     * form and ignoring is a pure function of the rule in force. Printed because a nomination that
     * exists, is visible on {@code /fi heir}, and decides nothing is exactly the kind of quiet
     * contradiction a player reads as a bug in the plugin.
     */
    private void staleNominationNotice(Player player, Fief fief, SuccessionService.StandingAnswer answer) {
        UUID heir = fief.getHeirUUID();
        if (heir == null || answer.holderMayNameHeir()) {
            return;
        }
        player.sendMessage(Component.text("  A nomination for "
                + new UUIDChecker().findPlayerNameBasedOnUUID(heir) + " stands from a time when this "
                + "fief's holder could name an heir. It decides nothing now.", NamedTextColor.GRAY));
    }
}
