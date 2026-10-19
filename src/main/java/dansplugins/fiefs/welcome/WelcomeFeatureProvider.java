package dansplugins.fiefs.welcome;

import com.github.exophobias.patriamwelcome.api.HubFeature;
import com.github.exophobias.patriamwelcome.api.HubFeatureProvider;
import com.github.exophobias.patriamwelcome.api.HubFeatureView;
import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.commands.HelpCommand;
import dansplugins.fiefs.objects.Fief;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import java.util.ArrayList;
import java.util.List;

/** Current self holding projection owned by Fiefs. No foreign roster, presence or live chunk loads. */
public final class WelcomeFeatureProvider implements HubFeatureProvider {
    private final Fiefs owner;
    public WelcomeFeatureProvider(Fiefs owner) { this.owner = owner; }
    public static AutoCloseable register(Plugin plugin) {
        var provider = new WelcomeFeatureProvider((Fiefs) plugin);
        plugin.getServer().getServicesManager().register(HubFeatureProvider.class, provider, plugin, ServicePriority.Normal);
        return () -> plugin.getServer().getServicesManager().unregister(HubFeatureProvider.class, provider);
    }
    @Override public String key() { return "holdings"; }
    @Override public boolean availableTo(Player player) {
        return Bukkit.isPrimaryThread() && owner.featureReady() && player.isOnline()
                && owner.getServer().getPlayer(player.getUniqueId()) == player && !HelpCommand.permitted(player).isEmpty();
    }
    @Override public void open(Player player, HubFeatureView view) {
        if (!availableTo(player) || !view.active()) { view.unavailable("Holding pages are unavailable."); return; }
        var rows = new ArrayList<HubFeature.Row>();
        var sections = new ArrayList<HubFeature.Section>();
        var actions = new ArrayList<HubFeature.Action>();
        // Mirror /fi info: a realm is required before its fief information is readable.
        Fief self = player.hasPermission("fiefs.info") ? owner.currentFeatureFief(player) : null;
        if (self != null && self.isMember(player.getUniqueId())) {
            rows.add(new HubFeature.Row("Holding", self.getName()));
            rows.add(new HubFeature.Row("Your position", self.isOwner(player.getUniqueId()) ? "Holder" : "Member"));
            rows.add(new HubFeature.Row("Members", Integer.toString(self.getNumMembers())));
            rows.add(new HubFeature.Row("Claimed chunks", Integer.toString(owner.getPersistentData().getNumChunksClaimedByFief(self))));
            rows.add(new HubFeature.Row("Land allowance", Integer.toString(self.getCumulativePowerLevel())));
            rows.add(new HubFeature.Row("Seat", self.getCapitalWorld() == null ? "Not set" : self.getCapitalWorld() + " (chunk " + self.getCapitalX() + ", " + self.getCapitalZ() + ")"));
            if (!self.getDescription().isBlank()) sections.add(new HubFeature.Section("Description", List.of(self.getDescription())));
            actions.add(new HubFeature.Action("Holding details", "Inspect your current holding", "fi info", "fiefs.info"));
            if (player.hasPermission("fiefs.succession")) actions.add(new HubFeature.Action("Succession", "Inspect current inheritance rules", "fi succession", "fiefs.succession"));
        } else rows.add(new HubFeature.Row("Current holding", player.hasPermission("fiefs.info") ? "No current fief membership" : "Holding information is not permitted"));
        var help = HelpCommand.permitted(player).stream().map(entry -> new HubFeature.Help("fi " + entry.command(), entry.description(), entry.permission(), entry.staff())).toList();
        view.show(new HubFeature("Holdings", "Your current holding, land allowance and permitted Fiefs commands. Commands with placeholders require their shown arguments.", rows, sections, help, actions));
    }
}
