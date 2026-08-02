package dansplugins.fiefs.commands;

import com.dansplugins.factionsystem.api.FactionView;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.objects.ClaimedChunk;
import dansplugins.fiefs.objects.Fief;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * {@code /fi capital} - the holder names where their fief's seat stands.
 *
 * <p>A realm has a seat; a fief had none, and anything that has to name "the place a side must hold"
 * needs one. It is set to the chunk the holder is standing in, which is the only sensible way to
 * choose a place in a game about places -- a coordinate typed into a command names somewhere the
 * player may never have been.
 *
 * <p><b>The chunk must already belong to this fief.</b> A capital on land the fief does not hold
 * would be a seat defended by somebody else's ground, and taking it would mean taking a chunk that
 * has nothing to do with the fief.
 *
 * <p>Moving it is free and always allowed, including while a rising is running, and that is not a
 * hole. A rising freezes both capitals onto its own record at the moment the war starts, so a
 * capital moved afterwards changes nothing about a war already under way. Keeping the rule on that
 * side means this command needs to know nothing about rebellions.
 */
public class CapitalCommand extends FiefsCommand {
    private final MedievalFactionsIntegrator medievalFactionsIntegrator;
    private final PersistentData persistentData;

    public CapitalCommand(MedievalFactionsIntegrator medievalFactionsIntegrator, PersistentData persistentData) {
        super(new ArrayList<>(Arrays.asList("capital", "seat")), new ArrayList<>(Arrays.asList("fiefs.capital")));
        this.medievalFactionsIntegrator = medievalFactionsIntegrator;
        this.persistentData = persistentData;
    }

    /** Bare {@code /fi capital} reports where it stands, rather than erroring. */
    @Override
    public boolean execute(CommandSender sender) {
        Fief fief = resolveOwnedFief(sender);
        if (fief == null) {
            return false;
        }
        Player player = (Player) sender;
        if (!fief.hasCapital()) {
            player.sendMessage(Component.text(fief.getName() + " has no capital. Stand where you "
                    + "want it and use /fi capital set.", NamedTextColor.AQUA));
            return true;
        }
        player.sendMessage(Component.text("The capital of " + fief.getName() + " stands in "
                + fief.getCapitalWorld() + " at chunk " + fief.getCapitalX() + ", "
                + fief.getCapitalZ() + ".", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Fief fief = resolveOwnedFief(sender);
        if (fief == null) {
            return false;
        }
        Player player = (Player) sender;

        if (!args[0].equalsIgnoreCase("set")) {
            player.sendMessage(Component.text("Usage: /fi capital [set]", NamedTextColor.RED));
            return false;
        }

        Chunk chunk = player.getLocation().getChunk();
        ClaimedChunk claimed = persistentData.getClaimedChunks().stream()
                .filter(c -> c.isAt(chunk))
                .findFirst()
                .orElse(null);
        if (claimed == null || !claimed.getFief().equalsIgnoreCase(fief.getName())) {
            player.sendMessage(Component.text("This land does not belong to " + fief.getName()
                    + ". A capital stands on the fief's own ground, so claim it first.",
                    NamedTextColor.RED));
            return false;
        }

        fief.setCapital(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        persistentData.markDirty();
        player.sendMessage(Component.text("The capital of " + fief.getName() + " now stands here. "
                + "This is the ground a rising is won and lost on, and it is public.",
                NamedTextColor.GREEN));
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
            player.sendMessage(Component.text("You must be the holder of your fief to name its "
                    + "capital.", NamedTextColor.RED));
            return null;
        }
        return fief;
    }
}
