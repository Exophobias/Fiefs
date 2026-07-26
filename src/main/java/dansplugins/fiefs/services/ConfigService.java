package dansplugins.fiefs.services;

import dansplugins.fiefs.Fiefs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/*
    To add a new config option, the following methods must be altered:
    - saveMissingConfigDefaultsIfNotPresent()
    - setConfigOption()
    - sendConfigList()
 */

/**
 * @author Daniel McCoy Stephenson
 */
public class ConfigService {
    private final Fiefs fiefs;

    private boolean altered = false;

    public ConfigService(Fiefs fiefs) {
        this.fiefs = fiefs;
    }

    public void saveMissingConfigDefaultsIfNotPresent() {
        // set version
        if (!getConfig().isString("version")) {
            getConfig().addDefault("version", fiefs.getVersion());
        }
        else {
            getConfig().set("version", fiefs.getVersion());
        }

        // save config options
        if (!getConfig().isSet("debugMode")) {
            getConfig().set("debugMode", false);
        }
        if (!getConfig().isSet("limitLand")) {
            getConfig().set("limitLand", true);
        }
        if (!getConfig().isSet("enableTerritoryAlerts")) {
            getConfig().set("enableTerritoryAlerts", true);
        }
        getConfig().options().copyDefaults(true);
        fiefs.saveConfig();
    }

    public void setConfigOption(String option, String value, CommandSender sender) {

        if (getConfig().isSet(option)) {

            if (option.equalsIgnoreCase("version")) {
                sender.sendMessage(Component.text("Cannot set version.", NamedTextColor.RED));
                return;
            } else if (option.equalsIgnoreCase("a")) { // no integers yet
                getConfig().set(option, Integer.parseInt(value));
                sender.sendMessage(Component.text("Integer set.", NamedTextColor.GREEN));
            } else if (option.equalsIgnoreCase("debugMode")
                    || option.equalsIgnoreCase("limitLand")
                    || option.equalsIgnoreCase("enableTerritoryAlerts")) {
                getConfig().set(option, Boolean.parseBoolean(value));
                sender.sendMessage(Component.text("Boolean set.", NamedTextColor.GREEN));
            } else if (option.equalsIgnoreCase("c")) { // no doubles yet
                getConfig().set(option, Double.parseDouble(value));
                sender.sendMessage(Component.text("Double set.", NamedTextColor.GREEN));
            } else {
                getConfig().set(option, value);
                sender.sendMessage(Component.text("String set.", NamedTextColor.GREEN));
            }

            // save
            fiefs.saveConfig();
            altered = true;
        } else {
            sender.sendMessage(Component.text("That config option wasn't found.", NamedTextColor.RED));
        }
    }

    public void sendConfigList(CommandSender sender) {
        sender.sendMessage(Component.text("=== Config List ===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("version: " + getConfig().getString("version")
                + ", debugMode: " + getBoolean("debugMode")
                + ", limitLand: " + getBoolean("limitLand")
                + ", enableTerritoryAlerts: " + getBoolean("enableTerritoryAlerts"), NamedTextColor.AQUA));
    }

    public boolean hasBeenAltered() {
        return altered;
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