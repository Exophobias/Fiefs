package dansplugins.fiefs.services;

import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.config.ConfigMigrator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * @author Daniel McCoy Stephenson
 */
public class ConfigService {
    private final Fiefs fiefs;

    public ConfigService(Fiefs fiefs) {
        this.fiefs = fiefs;
    }

    public void setConfigOption(String option, String value, CommandSender sender) {
        String canonical = switch (option.toLowerCase(java.util.Locale.ROOT)) {
            case "debugmode" -> "debugMode";
            case "limitland" -> "limitLand";
            case "enableterritoryalerts" -> "enableTerritoryAlerts";
            default -> null;
        };
        if (canonical == null) {
            sender.sendMessage(Component.text("That config option wasn't found.", NamedTextColor.RED));
            return;
        }
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            sender.sendMessage(Component.text("Value must be true or false.", NamedTextColor.RED));
            return;
        }

        if (fiefs.updateConfigBoolean(canonical, Boolean.parseBoolean(value))) {
            sender.sendMessage(Component.text("Boolean set.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    "Config update was blocked; the previous settings remain active.",
                    NamedTextColor.RED));
        }
    }

    public void sendConfigList(CommandSender sender) {
        sender.sendMessage(Component.text("=== Config List ===", NamedTextColor.AQUA));
        ConfigMigrator.Result result = fiefs.getConfigMigrationResult();
        ConfigMigrator.Result active = fiefs.getActiveConfigResult();
        String state = result == null ? "unavailable" : result.state().name().toLowerCase();
        sender.sendMessage(Component.text("plugin-version: " + fiefs.getPluginMeta().getVersion()
                + ", config-supported: " + ConfigMigrator.CURRENT_VERSION
                + ", config-source: " + (result == null || result.sourceVersion() < 0
                        ? "unverified" : result.sourceVersion())
                + ", config-installed: " + (result != null && result.compatible()
                        ? result.loadedVersion() : "unverified")
                + ", config-active: " + (active == null
                        ? "none" : active.loadedVersion())
                + ", config-state: " + state
                + ", debugMode: " + getBoolean("debugMode")
                + ", limitLand: " + getBoolean("limitLand")
                + ", enableTerritoryAlerts: " + getBoolean("enableTerritoryAlerts"), NamedTextColor.AQUA));
    }

    public boolean hasBeenAltered() {
        // Command-owned config changes are persisted atomically before their snapshot is published.
        return false;
    }

    public FileConfiguration getConfig() {
        return fiefs.getConfig();
    }

    public int getInt(String option) {
        return getConfig().getInt(option);
    }

    public boolean getBoolean(String option) {
        return getConfig().getBoolean(option);
    }

    public double getDouble(String option) {
        return getConfig().getDouble(option);
    }

    public String getString(String option) {
        return getConfig().getString(option);
    }
}
