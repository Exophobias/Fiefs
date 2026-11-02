package dansplugins.fiefs.utils;

import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.services.StorageService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import static org.mockito.Mockito.*;

class SchedulerTest {
    @Test void autosaveUsesFiveMinuteDirtyWrites() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Fiefs plugin = mock(Fiefs.class);
        StorageService storage = mock(StorageService.class);
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            new Scheduler(logger, plugin, storage).scheduleAutosave();
            var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).scheduleSyncRepeatingTask(eq(plugin), callback.capture(), eq(6000L), eq(6000L));
            verifyNoInteractions(storage);
            callback.getValue().run();
            verify(storage).saveIfDirty();
        }
    }
}
