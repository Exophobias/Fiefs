package dansplugins.fiefs.integrators;

import com.dansplugins.factionsystem.api.FactionView;
import com.dansplugins.factionsystem.api.MedievalFactionsApi;
import dansplugins.fiefs.utils.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Fiefs' single point of contact with Medieval Factions.
 *
 * <p>Binds <b>only</b> to {@code com.dansplugins.factionsystem.api} — the stable, in-JVM API — and
 * never to MF's internal services or models. That is not a style preference. Fiefs was originally
 * written against MF4's internal API; MF5's Kotlin rewrite deleted it, and Fiefs stayed broken from
 * 2022 until 2026. Internal coupling fails at <em>runtime</em> with NoSuchMethodError, not at build
 * time, so it is exactly the kind of breakage that reaches players first.
 *
 * @author Daniel McCoy Stephenson
 */
public class MedievalFactionsIntegrator {
    private final Logger logger;

    private MedievalFactionsApi api = null;

    public MedievalFactionsIntegrator(Logger logger) {
        this.logger = logger;
    }

    /**
     * Resolves the Medieval Factions API. <b>Must be called from {@code onEnable}, never from a field
     * initializer or constructor.</b>
     *
     * <p>MF registers its API with Bukkit's ServicesManager inside its own {@code onEnable}, and Bukkit
     * constructs every plugin before enabling any of them. Resolving at construction time therefore
     * always yields null, and Fiefs would refuse to enable on every single boot. The hard
     * {@code depend: [MedievalFactions]} in plugin.yml guarantees MF is enabled before this runs.
     *
     * @return whether the API was found, i.e. whether Fiefs can enable.
     */
    public boolean resolve() {
        api = MedievalFactionsApi.get();
        if (api == null) {
            logger.log("[DEBUG] The Medieval Factions API was not available.");
            return false;
        }
        logger.log("[DEBUG] Medieval Factions was found successfully!");
        return true;
    }

    /**
     * @return the Medieval Factions API. Only valid after a successful {@link #resolve()}.
     */
    public MedievalFactionsApi getAPI() {
        return api;
    }

    /**
     * Resolves the MF faction a player belongs to, sending the player a standard error message and
     * returning null if that isn't possible. Centralizes a lookup that was previously duplicated
     * across every command.
     */
    public FactionView getFactionForPlayer(Player player) {
        FactionView faction = api.getFactionByPlayer(player.getUniqueId());
        if (faction == null) {
            player.sendMessage(Component.text("You must be in a faction to use this command.", NamedTextColor.RED));
        }
        return faction;
    }
}
