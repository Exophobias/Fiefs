package dansplugins.fiefs;

import dansplugins.fiefs.commands.*;
import dansplugins.fiefs.config.ConfigMigrator;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.heraldry.HeraldryPresence;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.listeners.FactionEventListener;
import dansplugins.fiefs.listeners.InteractionListener;
import dansplugins.fiefs.listeners.MoveListener;
import dansplugins.fiefs.listeners.SuccessionPolicyListener;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.services.CommandService;
import dansplugins.fiefs.services.ConfigService;
import dansplugins.fiefs.services.StorageService;
import dansplugins.fiefs.services.SuccessionService;
import dansplugins.fiefs.utils.Logger;
import dansplugins.fiefs.utils.Scheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * NOT final, deliberately: MockBukkit loads a plugin by generating a ByteBuddy subclass of the main
 * class, so marking this final makes every test in the suite fail with "Cannot subclass primitive,
 * array or final types". Re-adding final breaks the whole test tier.
 *
 * @author Daniel McCoy Stephenson
 */
public class Fiefs extends JavaPlugin {
    private record ConfigGeneration(YamlConfiguration configuration,
                                    ConfigMigrator.Result compatibility) {
    }

    private final String pluginVersion = "v" + getPluginMeta().getVersion();
    // Constructor-time debug logging must never make Bukkit parse an untrusted disk file. Until
    // strict activation succeeds, getConfig() deliberately exposes only this empty bootstrap view.
    private final YamlConfiguration bootstrapConfiguration = new YamlConfiguration();
    private volatile ConfigGeneration configGeneration;

    private final CommandService commandService = new CommandService();
    private final Logger logger = new Logger(this);
    // configService MUST be initialized before medievalFactionsIntegrator. Field initializers run in
    // declaration order, and the integrator's constructor logs -> Logger.log -> Fiefs.isDebugEnabled()
    // -> configService.getBoolean(...). With the old ordering that dereferenced a null configService
    // and threw out of the plugin constructor.
    private final ConfigService configService = new ConfigService(this);
    private final MedievalFactionsIntegrator medievalFactionsIntegrator = new MedievalFactionsIntegrator(logger);
    private final PersistentData persistentData = new PersistentData(medievalFactionsIntegrator);
    private final StorageService storageService = new StorageService(configService, this, persistentData, logger, medievalFactionsIntegrator);
    private final Scheduler scheduler = new Scheduler(logger, this, storageService);
    private final ChunkService chunkService = new ChunkService(persistentData, medievalFactionsIntegrator, configService);
    // Takes the plugin rather than a java.util.logging.Logger, so the logger is resolved lazily. This
    // service reports at SEVERE and WARNING about another plugin's rule, so it must never route
    // through the debug-gated dansplugins.fiefs.utils.Logger above.
    private final SuccessionService successionService = new SuccessionService(medievalFactionsIntegrator, persistentData, this);

    /**
     * Whether {@link StorageService#load()} completed, i.e. whether {@link #persistentData} actually
     * reflects what is on disk.
     *
     * <p>Load-bearing for data safety. Bukkit marks a plugin enabled <em>before</em> invoking
     * {@code onEnable} and does not un-mark it if that throws, so {@code onDisable} still runs at
     * shutdown. Without this flag, any failed or short-circuited enable — a missing Medieval Factions,
     * a corrupt save file, anything — would reach {@code onDisable}, save empty in-memory state, and
     * overwrite fiefs.json and claimedChunks.json with {@code []}.
     */
    private boolean loaded = false;
    private volatile ConfigMigrator.Result configMigrationResult;

    /**
     * This runs when the server starts.
     */
    @Override
    public void onEnable() {
        if (!initializeConfig()) {
            // No mutable state, listeners, services, or schedules have been loaded yet. Explicitly
            // disable because some Bukkit test/runtime implementations leave a plugin marked enabled
            // when onEnable merely returns after a trust failure.
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Resolved HERE, not in a field initializer: MF registers its API with the ServicesManager in
        // its own onEnable, and Bukkit constructs all plugins before enabling any of them.
        if (!medievalFactionsIntegrator.resolve()) {
            getLogger().severe("Medieval Factions was not found. Fiefs cannot enable.");
            return;
        }

        storageService.load();
        loaded = true;
        registerEventHandlers();
        initializeCommandService();
        scheduler.scheduleAutosave();

        // Published through the ServicesManager as well as through getAPI(), which is the route
        // every other plugin on this server already uses for Medieval Factions and PatriamUtils.
        // The difference is not style: getAPI() forces a consumer to hold this class, so a consumer
        // that treats Fiefs as optional has to name a Fiefs type merely to ask whether Fiefs is
        // there -- which is the linkage failure the guard classes on the other side exist to avoid.
        // Registered AFTER the load, so nothing can read an empty store and cache the answer.
        getServer().getServicesManager().register(
                FiefsAPI.class, getAPI(), this, ServicePriority.Normal);

        // Also after the load, and for a second reason on top of that one: the guard behind this call
        // is what keeps Fiefs working on a server with no PatriamHeraldry. Nothing about heraldry is
        // named from here. See HeraldryPresence.
        HeraldryPresence.register(this, persistentData, medievalFactionsIntegrator);
    }

    /**
     * This runs when the server stops.
     */
    @Override
    public void onDisable() {
        // Never save state we never loaded — that writes [] over the real save files. See #loaded.
        if (loaded) {
            storageService.save();
        }
    }

    /**
     * This method handles commands sent to the minecraft server and interprets them if the label matches one of the core commands.
     * @param sender The sender of the command.
     * @param cmd The command that was sent. This is unused.
     * @param label The core command that has been invoked.
     * @param args Arguments of the core command. Often sub-commands.
     * @return A boolean indicating whether the execution of the command was successful.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Bare /fi is handled here rather than routed through the dispatcher, deliberately. Bukkit has
        // already checked the sender against the `fiefs` command's own permission by this point, and
        // going through the dispatcher would additionally demand fiefs.default.
        if (args.length == 0) {
            DefaultCommand defaultCommand = new DefaultCommand(this);
            return defaultCommand.execute(sender);
        }

        return commandService.interpretAndExecuteCommand(sender, args);
    }

    /**
     * This can be used to get the version of the plugin.
     * @return A string containing the version preceded by 'v'
     */
    public String getVersion() {
        return pluginVersion;
    }

    /** Returns only the exact snapshot that passed strict physical and typed validation. */
    @Override
    public FileConfiguration getConfig() {
        ConfigGeneration current = configGeneration;
        return current == null ? bootstrapConfiguration : current.configuration();
    }

    /**
     * Compatibility shim for callers that used the old jar-version config check.
     *
     * @return whether the installed configuration is blocked by its schema state.
     */
    public boolean isVersionMismatched() {
        ConfigMigrator.Result result = configMigrationResult;
        return result != null && !result.compatible();
    }

    /**
     * Checks if debug is enabled.
     * @return Whether debug is enabled.
     */
    public boolean isDebugEnabled() {
        return configService.getBoolean("debugMode");
    }

    /** Atomically persists and then publishes one command-owned boolean setting. */
    public boolean updateConfigBoolean(String key, boolean value) {
        ConfigGeneration current = configGeneration;
        if (current == null) {
            configMigrationResult = new ConfigMigrator.Result(ConfigMigrator.State.ERROR, -1,
                    null, "no active config snapshot is available");
            return false;
        }
        Path installed = getDataFolder().toPath().resolve("config.yml");
        ConfigMigrator.Result attempt = ConfigMigrator.updateBoolean(
                installed, current.compatibility(), key, value);
        configMigrationResult = attempt;
        if (!attempt.compatible()) {
            getLogger().warning("Config update was blocked: " + attempt.detail());
            return false;
        }
        try {
            if (!attempt.stillInstalled(installed)) {
                configMigrationResult = new ConfigMigrator.Result(ConfigMigrator.State.ERROR, -1,
                        null, "config.yml changed before its updated snapshot could be activated");
                return false;
            }
        } catch (IOException failure) {
            configMigrationResult = new ConfigMigrator.Result(ConfigMigrator.State.ERROR, -1,
                    null, "config.yml could not be rechecked before activation");
            return false;
        }

        // One pointer publishes the exact post-write snapshot and its matching CAS baseline.
        configGeneration = new ConfigGeneration(attempt.prepared().configuration(), attempt);
        return true;
    }

    public FiefsAPI getAPI() {
        return new FiefsAPI(persistentData, successionService);
    }

    /**
     * The live in-memory state.
     *
     * <p>Public for tests, which need to assert on fiefs and claims directly rather than through the
     * read-only {@link FiefsAPI} wrapper. Other plugins should use {@link #getAPI()} — this returns a
     * mutable internal object and its shape is not stable.
     */
    public PersistentData getPersistentData() {
        return persistentData;
    }

    private boolean initializeConfig() {
        saveDefaultConfig();
        Path installed = getDataFolder().toPath().resolve("config.yml");

        final String bundled;
        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().severe("Fiefs.jar does not contain config.yml; startup is blocked.");
                return false;
            }
            bundled = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            getLogger().severe("Fiefs.jar config.yml could not be read; startup is blocked.");
            return false;
        }

        ConfigMigrator.Result result = ConfigMigrator.upgrade(installed, bundled);
        configMigrationResult = result;
        getLogger().info("Config status: plugin=" + getPluginMeta().getVersion()
                + ", supported=" + ConfigMigrator.CURRENT_VERSION
                + ", source=" + result.sourceVersion()
                + ", installed=" + (result.compatible()
                        ? result.loadedVersion() : "unverified")
                + ", state=" + result.state().name().toLowerCase() + ".");
        if (!result.compatible()) {
            getLogger().severe("Fiefs config activation is blocked: " + result.detail());
            return false;
        }

        try {
            if (!result.stillInstalled(installed)) {
                configMigrationResult = new ConfigMigrator.Result(ConfigMigrator.State.ERROR, -1,
                        result.backup(), "config.yml changed during activation");
                getLogger().severe("Fiefs config changed during activation; startup is blocked.");
                return false;
            }
        } catch (IOException failure) {
            configMigrationResult = new ConfigMigrator.Result(ConfigMigrator.State.ERROR, -1,
                    result.backup(), "config.yml could not be rechecked during activation");
            getLogger().severe("Fiefs config could not be rechecked during activation.");
            return false;
        }
        configGeneration = new ConfigGeneration(result.prepared().configuration(), result);
        return true;
    }

    public ConfigMigrator.Result getConfigMigrationResult() {
        return configMigrationResult;
    }

    public ConfigMigrator.Result getActiveConfigResult() {
        ConfigGeneration current = configGeneration;
        return current == null ? null : current.compatibility();
    }

    /**
     * Registers the plugin's event handlers.
     */
    private void registerEventHandlers() {
        ArrayList<Listener> listeners = new ArrayList<>(Arrays.asList(
                new MoveListener(configService, chunkService, medievalFactionsIntegrator),
                new InteractionListener(chunkService, persistentData, logger, this),
                new FactionEventListener(persistentData, successionService),
                // Reports which succession ladder is actually in force once the whole server is up,
                // and drops a policy whose owning plugin stops functioning. Events rather than a
                // scheduled check: this feature adds no timer, no sweep and no clock.
                new SuccessionPolicyListener(successionService)
        ));
        PluginManager pluginManager = getServer().getPluginManager();
        listeners.forEach(listener -> pluginManager.registerEvents(listener, this));
    }

    /**
     * Initializes the command service with the plugin's subcommands.
     */
    private void initializeCommandService() {
        ArrayList<FiefsCommand> commands = new ArrayList<FiefsCommand>(Arrays.asList(
                new CapitalCommand(medievalFactionsIntegrator, persistentData),
                new CheckClaimCommand(persistentData, chunkService),
                new ClaimCommand(medievalFactionsIntegrator, persistentData, chunkService),
                new ConfigCommand(configService),
                new CreateCommand(medievalFactionsIntegrator, persistentData, logger),
                new DescCommand(medievalFactionsIntegrator, persistentData),
                new DisbandCommand(medievalFactionsIntegrator, persistentData),
                new FlagsCommand(medievalFactionsIntegrator, persistentData),
                new GrantCommand(medievalFactionsIntegrator, persistentData),
                new HeirCommand(medievalFactionsIntegrator, persistentData, successionService),
                new HelpCommand(),
                new InfoCommand(medievalFactionsIntegrator, persistentData, successionService),
                new InviteCommand(medievalFactionsIntegrator, persistentData),
                new JoinCommand(medievalFactionsIntegrator, persistentData, successionService),
                new KickCommand(medievalFactionsIntegrator, persistentData, successionService),
                new LeaveCommand(medievalFactionsIntegrator, persistentData, successionService),
                new ListCommand(medievalFactionsIntegrator, persistentData),
                new MembersCommand(medievalFactionsIntegrator, persistentData),
                new RenameCommand(medievalFactionsIntegrator, persistentData),
                new RevokeCommand(medievalFactionsIntegrator, persistentData),
                new SuccessionCommand(medievalFactionsIntegrator, persistentData, successionService),
                new TransferCommand(medievalFactionsIntegrator, persistentData),
                new UnclaimCommand(medievalFactionsIntegrator, persistentData, chunkService),
                new WhoisCommand(persistentData)
        ));
        commandService.initialize(commands, "That command wasn't found.");
    }
}
