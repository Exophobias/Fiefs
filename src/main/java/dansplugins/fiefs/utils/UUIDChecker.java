package dansplugins.fiefs.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Resolves between player names and UUIDs.
 *
 * @author Daniel McCoy Stephenson
 */
public class UUIDChecker {

    /**
     * Resolves a player name to a UUID, or null if the server has never seen that player.
     *
     * <p>Tries online players first, then Bukkit's already-loaded offline cache. It deliberately does
     * <b>not</b> fall back to {@code Bukkit.getOfflinePlayer(String)}: on a server in online mode that
     * can block the calling thread on a Mojang API lookup, and this runs from
     * {@code /fi invite|kick|transfer} on the main thread.
     *
     * <p>The previous implementation iterated all of {@code Bukkit.getOfflinePlayers()}, which builds
     * an array of every player who has ever joined the server — fine on a test box, linear in the
     * whole playerbase on a real one.
     */
    public UUID findUUIDBasedOnPlayerName(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        return cached != null ? cached.getUniqueId() : null;
    }

    /**
     * Resolves a UUID to a player name, falling back to the UUID's string form when the name is not
     * known. The UUID-taking {@code getOfflinePlayer} does not make a blocking web request, unlike the
     * name-taking overload.
     */
    public String findPlayerNameBasedOnUUID(UUID playerUUID) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return playerUUID.toString();
    }
}
