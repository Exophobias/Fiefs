package dansplugins.fiefs.listeners;

import dansplugins.fiefs.testsupport.BukkitTestDoubles;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalNotifierTest {
    private final AtomicLong now = new AtomicLong(10_000);
    private final RefusalNotifier notifier = new RefusalNotifier(now::get);

    @Test
    void firstRefusalTellsThePlayerWhichFiefOwnsTheLand() {
        List<String> messages = new ArrayList<>();
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        assertTrue(notifier.notifyRefused(player, "North"));

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("North"), messages.get(0));
    }

    @Test
    void refusalsInsideTheWindowAreNotRepeated() {
        List<String> messages = new ArrayList<>();
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        notifier.notifyRefused(player, "North");
        now.addAndGet(RefusalNotifier.THROTTLE_MILLIS - 1);
        assertFalse(notifier.notifyRefused(player, "North"));

        assertEquals(1, messages.size());
    }

    @Test
    void aRefusalAfterTheWindowIsReportedAgain() {
        List<String> messages = new ArrayList<>();
        Player player = BukkitTestDoubles.messageCapturingPlayer(messages);

        notifier.notifyRefused(player, "North");
        now.addAndGet(RefusalNotifier.THROTTLE_MILLIS);
        assertTrue(notifier.notifyRefused(player, "South"));

        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("South"), messages.get(1));
    }

    @Test
    void eachPlayerHasTheirOwnWindow() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        Player a = BukkitTestDoubles.messageCapturingPlayer(first);
        Player b = BukkitTestDoubles.messageCapturingPlayer(second);

        notifier.notifyRefused(a, "North");
        assertTrue(notifier.notifyRefused(b, "North"));

        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }
}
