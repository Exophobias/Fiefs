package dansplugins.fiefs.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * Tells a player why fief protection refused an action (#209). Before this, the action simply
 * did not happen and nothing said why.
 *
 * The message is sent at most once per {@link #THROTTLE_MILLIS} per player: a held mouse button
 * or a pressure plate refuses an event every tick, and each one would otherwise be a chat line.
 * Players are held weakly, so a player who leaves is forgotten.
 */
public class RefusalNotifier {
    static final long THROTTLE_MILLIS = 2000;

    private final LongSupplier clock;
    private final Map<Player, Long> lastNotified = new WeakHashMap<>();

    public RefusalNotifier() {
        // Monotonic: a wall clock corrected backwards would otherwise suppress every message until it
        // caught up again.
        this(() -> System.nanoTime() / 1_000_000L);
    }

    RefusalNotifier(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Tells {@code player} that the land belongs to {@code fiefName}, unless they were told less
     * than {@link #THROTTLE_MILLIS} ago. Returns true when a message was sent.
     */
    public boolean notifyRefused(Player player, String fiefName) {
        long now = clock.getAsLong();
        Long last = lastNotified.get(player);
        if (last != null && now - last < THROTTLE_MILLIS) {
            return false;
        }
        lastNotified.put(player, now);
        player.sendMessage(ChatColor.RED + "This land belongs to the fief " + fiefName + ". Only its members may do that here.");
        return true;
    }
}
