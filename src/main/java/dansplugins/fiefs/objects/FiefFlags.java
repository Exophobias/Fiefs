package dansplugins.fiefs.objects;

import dansplugins.fiefs.utils.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;

/*
    In order to add a new fief flag to this class, the following methods need to be altered:
    - initializeFlagNames()
    - initializeFlagValues()
    - loadMissingFlagsIfNecessary()
*/

/**
 * @author Daniel McCoy Stephenson
 */
public class FiefFlags {
    private final Logger logger;
    private final ArrayList<String> flagNames = new ArrayList<>();
    private HashMap<String, Integer> integerValues = new HashMap<>();
    private HashMap<String, Boolean> booleanValues = new HashMap<>();
    private HashMap<String, Double> doubleValues = new HashMap<>();
    private HashMap<String, String> stringValues = new HashMap<>();

    public FiefFlags(Logger logger) {
        this.logger = logger;
        initializeFlagNames();
    }

    private void initializeFlagNames() { // this is called internally
        flagNames.add("claimedLandProtected");
    }

    public void initializeFlagValues() {
        // this is called externally in Fief.java when a fief is created in-game
        booleanValues.put("claimedLandProtected", true);
    }

    public void loadMissingFlagsIfNecessary() {
        // this is called externally in Fief.java when a fief is loaded from save files
        if (!booleanValues.containsKey("claimedLandProtected")) {
            booleanValues.put("claimedLandProtected", true);
        }
    }

    public void sendFlagList(Player player) {
        player.sendMessage(Component.text("" + getFlagsSeparatedByCommas(), NamedTextColor.AQUA));
    }


    public void setFlag(String flag, String value, Player player) {
        
        if (isFlag(flag)) {
            if (integerValues.containsKey(flag)) {
                integerValues.replace(flag, Integer.parseInt(value));
                player.sendMessage(Component.text("Integer set.", NamedTextColor.GREEN));
            }
            else if (booleanValues.containsKey(flag)) {
                booleanValues.replace(flag, Boolean.parseBoolean(value));
                player.sendMessage(Component.text("Boolean set.", NamedTextColor.GREEN));
            }
            else if (doubleValues.containsKey(flag)) {
                doubleValues.replace(flag, Double.parseDouble(value));
                player.sendMessage(Component.text("Double set.", NamedTextColor.GREEN));
            }
            else if (stringValues.containsKey(flag)) {
                stringValues.replace(flag, value);
                player.sendMessage(Component.text("String set.", NamedTextColor.GREEN));
            }
        }
        else {
            player.sendMessage(Component.text("That fief flag wasn't found.", NamedTextColor.RED));
        }
    }

    public Object getFlag(String flag) {
        if (!isFlag(flag)) {
            logger.log(String.format("[DEBUG] Flag '%s' was not found!", flag));
            return false;
        }

        if (integerValues.containsKey(flag)) {
            logger.log(String.format("Flag '%s' was found! Value: '%s'", flag, integerValues.get(flag)));
            return integerValues.get(flag);
        }
        else if (booleanValues.containsKey(flag)) {
            logger.log(String.format("[DEBUG] Flag '%s' was found! Value: '%s'", flag, booleanValues.get(flag)));
            return booleanValues.get(flag);
        }
        else if (doubleValues.containsKey(flag)) {
            logger.log(String.format("[DEBUG] Flag '%s' was found! Value: '%s'", flag, doubleValues.get(flag)));
            return doubleValues.get(flag);
        }
        else if (stringValues.containsKey(flag)) {
            logger.log(String.format("[DEBUG] Flag '%s' was found! Value: '%s'", flag, stringValues.get(flag)));
            return stringValues.get(flag);
        }
        return null;
    }

    public HashMap<String, Integer> getIntegerValues() {
        return integerValues;
    }

    public void setIntegerValues(HashMap<String, Integer> values) {
        integerValues = values;
    }

    public HashMap<String, Boolean> getBooleanValues() {
        return booleanValues;
    }

    public void setBooleanValues(HashMap<String, Boolean> values) {
        booleanValues = values;
    }

    public HashMap<String, Double> getDoubleValues() {
        return doubleValues;
    }

    public void setDoubleValues(HashMap<String, Double> values) {
        doubleValues = values;
    }

    public HashMap<String, String> getStringValues() {
        return stringValues;
    }

    public void setStringValues(HashMap<String, String> values) {
        stringValues = values;
    }

    private boolean isFlag(String flag) {
        // this method will likely need to be used to sanitize user input
        return flagNames.contains(flag);
    }

    public int getNumFlags() {
        return booleanValues.size();
    }

    private String getFlagsSeparatedByCommas() {
        String toReturn = "";
        for (String flagName : flagNames) {

            if (!toReturn.equals("")) {
                toReturn += ", ";
            }
            if (integerValues.containsKey(flagName)) {
                toReturn += String.format("%s: %s", flagName, integerValues.get(flagName));
            }
            else if (booleanValues.containsKey(flagName)) {
                toReturn += String.format("%s: %s", flagName, booleanValues.get(flagName));
            }
            else if (doubleValues.containsKey(flagName)) {
                toReturn += String.format("%s: %s", flagName, doubleValues.get(flagName));
            }
            else if (stringValues.containsKey(flagName)) {
                toReturn += String.format("%s: %s", flagName, stringValues.get(flagName));
            }
        }
        return toReturn;
    }
}