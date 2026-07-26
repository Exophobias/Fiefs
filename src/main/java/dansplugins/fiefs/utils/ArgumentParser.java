package dansplugins.fiefs.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts double-quoted arguments, so that {@code /fi create "Ashford Mill"} yields one name rather
 * than two arguments.
 *
 * <p>Replaces {@code preponderous.ponder.misc.ArgumentParser}, and fixes a bug in it. Ponder joined
 * the argument array with the <b>empty string</b> before matching, so every space inside the quotes
 * was swallowed: {@code /fi create "Ashford Mill"} actually created a fief called
 * {@code AshfordMill}. Joining with a space is plainly what quoting is for, so that is what this does.
 * The visible consequence is that quoted names keep their spaces.
 */
public class ArgumentParser {
    private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"[^\"]*\"");

    /**
     * Resolves a fief name from a command's arguments, accepting both {@code "Ashford Mill"} and a
     * bare {@code Ashford Mill}.
     *
     * <p>{@code /fi create} and {@code /fi rename} take quoted names, so multi-word fiefs exist — but
     * {@code join}, {@code info} and {@code members} used only {@code args[0]}, which made every fief
     * with a space in its name impossible to join or inspect.
     *
     * @return the name, or null if there are no arguments.
     */
    public String getFiefNameFromArguments(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<String> quoted = getArgumentsInsideDoubleQuotes(args);
        return quoted.isEmpty() ? String.join(" ", args) : quoted.get(0);
    }

    /**
     * @return every double-quoted section of {@code args}, in order, without the surrounding quotes.
     *         Empty if there are none.
     */
    public List<String> getArgumentsInsideDoubleQuotes(String[] args) {
        List<String> results = new ArrayList<>();
        if (args == null || args.length == 0) {
            return results;
        }

        Matcher matcher = DOUBLE_QUOTED.matcher(String.join(" ", args));
        while (matcher.find()) {
            results.add(matcher.group().replace("\"", ""));
        }
        return results;
    }
}
