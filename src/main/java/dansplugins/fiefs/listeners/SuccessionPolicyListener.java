package dansplugins.fiefs.listeners;

import dansplugins.fiefs.services.SuccessionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * The two moments the succession seam has to notice about the rest of the server.
 *
 * <p>Both are events rather than a scheduled check, and that is a constraint rather than a
 * preference: this feature adds no timer, no sweep and no clock, {@code Scheduler} keeps doing
 * exactly one thing, and there is no decision window anywhere in it for a clock to bound.
 */
public class SuccessionPolicyListener implements Listener {
    private final SuccessionService successionService;

    public SuccessionPolicyListener(SuccessionService successionService) {
        this.successionService = successionService;
    }

    /**
     * Reports which succession ladder is actually in force, once the whole server is up.
     *
     * <p>It has to be here and not in {@code onEnable}. Fiefs is enabled <em>before</em> the plugins
     * that soft-depend on it, so a check during our own enable would find no policy registered on a
     * perfectly healthy server and print the alarm every boot - the same defect as a gate that passes
     * because it had nothing to look at, only inverted. By this event every plugin has enabled, so
     * "nobody registered" is a fact.
     *
     * <p>Nothing is printed if a registration already announced itself, so exactly one of the two
     * lines appears in a boot log, and <b>a boot log with neither is itself the alarm.</b>
     */
    @EventHandler
    public void handle(ServerLoadEvent event) {
        successionService.announceLadderInForceIfSilent();
    }

    /**
     * Drops a policy whose owning plugin has stopped functioning.
     *
     * <p>A policy left registered by a plugin that is no longer running is the failure this closes: it
     * would keep being consulted, keep throwing {@link NoClassDefFoundError} out of a departure, and
     * the fief succession that failed would look like a Fiefs bug.
     *
     * <p>This is the mirror of the problem above, arriving from the other end of the run. A plugin
     * that registered a policy is disabled <em>before</em> Fiefs on every clean shutdown, so this
     * handler fires on every healthy run and only some of those firings are worth a word. Dropping is
     * always right; saying so at WARNING is right only mid-session. Which of the two this is, and how
     * that is told apart, is in {@code SuccessionService.dropPolicyOwnedBy}.
     */
    @EventHandler
    public void handle(PluginDisableEvent event) {
        successionService.dropPolicyOwnedBy(event.getPlugin());
    }
}
