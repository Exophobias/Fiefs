package dansplugins.fiefs;

import dansplugins.fiefs.commands.*;
import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.externalapi.FiefsAPI;
import dansplugins.fiefs.integrators.MedievalFactionsIntegrator;
import dansplugins.fiefs.listeners.FactionEventListener;
import dansplugins.fiefs.listeners.InteractionListener;
import dansplugins.fiefs.listeners.MoveListener;
import dansplugins.fiefs.commands.abs.FiefsCommand;
import dansplugins.fiefs.services.ChunkService;
import dansplugins.fiefs.services.CommandService;
import dansplugins.fiefs.services.ConfigService;
import dansplugins.fiefs.services.StorageService;
import dansplugins.fiefs.utils.Logger;
import dansplugins.fiefs.utils.Scheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    private final String pluginVersion = "v" + getPluginMeta().getVersion();

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

    /**
     * This runs when the server starts.
     */
    @Override
    public void onEnable() {
        initializeConfig();

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

    /**
     * Checks if the version is mismatched.
     * @return A boolean indicating if the version is mismatched.
     */
    public boolean isVersionMismatched() {
        String configVersion = this.getConfig().getString("version");
        if (configVersion == null || this.getVersion() == null) {
            return false;
        } else {
            return !configVersion.equalsIgnoreCase(this.getVersion());
        }
    }

    /**
     * Checks if debug is enabled.
     * @return Whether debug is enabled.
     */
    public boolean isDebugEnabled() {
        return configService.getBoolean("debugMode");
    }

    public FiefsAPI getAPI() {
        return new FiefsAPI(persistentData);
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

    private void initializeConfig() {
        if (!(new File(getDataFolder(), "config.yml").exists())) {
            configService.saveMissingConfigDefaultsIfNotPresent();
        }
        else {
            // pre load compatibility checks
            if (isVersionMismatched()) {
                configService.saveMissingConfigDefaultsIfNotPresent();
            }
            reloadConfig();
        }
    }

    /**
     * Registers the plugin's event handlers.
     */
    private void registerEventHandlers() {
        ArrayList<Listener> listeners = new ArrayList<>(Arrays.asList(
                new MoveListener(configService, chunkService, medievalFactionsIntegrator),
                new InteractionListener(chunkService, persistentData, logger, this),
                new FactionEventListener(persistentData)
        ));
        PluginManager pluginManager = getServer().getPluginManager();
        listeners.forEach(listener -> pluginManager.registerEvents(listener, this));
    }

    /**
     * Initializes the command service with the plugin's subcommands.
     */
    private void initializeCommandService() {
        ArrayList<FiefsCommand> commands = new ArrayList<FiefsCommand>(Arrays.asList(
                new CheckClaimCommand(persistentData, chunkService),
                new ClaimCommand(medievalFactionsIntegrator, persistentData, chunkService),
                new ConfigCommand(configService),
                new CreateCommand(medievalFactionsIntegrator, persistentData, logger),
                new DescCommand(medievalFactionsIntegrator, persistentData),
                new DisbandCommand(medievalFactionsIntegrator, persistentData),
                new FlagsCommand(medievalFactionsIntegrator, persistentData),
                new HelpCommand(),
                new InfoCommand(medievalFactionsIntegrator, persistentData),
                new InviteCommand(medievalFactionsIntegrator, persistentData),
                new JoinCommand(medievalFactionsIntegrator, persistentData),
                new KickCommand(medievalFactionsIntegrator, persistentData),
                new LeaveCommand(medievalFactionsIntegrator, persistentData),
                new ListCommand(medievalFactionsIntegrator, persistentData),
                new MembersCommand(medievalFactionsIntegrator, persistentData),
                new RenameCommand(medievalFactionsIntegrator, persistentData),
                new TransferCommand(medievalFactionsIntegrator, persistentData),
                new UnclaimCommand(medievalFactionsIntegrator, persistentData, chunkService)
        ));
        commandService.initialize(commands, "That command wasn't found.");
    }
}