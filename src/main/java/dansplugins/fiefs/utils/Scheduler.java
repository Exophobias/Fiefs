package dansplugins.fiefs.utils;

import dansplugins.fiefs.Fiefs;
import dansplugins.fiefs.services.StorageService;
import org.bukkit.Bukkit;

/**
 * @author Daniel McCoy Stephenson
 */
public class Scheduler {
    private final Logger logger;
    private final Fiefs fiefs;
    private final StorageService storageService;

    public Scheduler(Logger logger, Fiefs fiefs, StorageService storageService) {
        this.logger = logger;
        this.fiefs = fiefs;
        this.storageService = storageService;
    }

    public void scheduleAutosave() {
        logger.log("Scheduling autosave.");
        // Every 5 minutes rather than hourly. The write is now conditional on something having
        // actually changed, so an idle server pays nothing for the shorter interval, while a busy one
        // narrows the crash window from an hour of lost fiefs to five minutes.
        int delay = 5 * 60;
        int secondsUntilRepeat = 5 * 60;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(fiefs, new Runnable() {
            @Override
            public void run() {
                storageService.saveIfDirty();
            }
        }, delay * 20, secondsUntilRepeat * 20);
    }
}