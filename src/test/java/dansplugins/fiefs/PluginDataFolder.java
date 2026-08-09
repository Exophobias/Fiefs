package dansplugins.fiefs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where MockBukkit will put the plugin's data folder, worked out BEFORE the plugin is loaded.
 *
 * <p>Any test about a save file needs this, because the file has to exist before {@code onEnable} reads
 * it and only the load creates the folder. The trap is that the folder is <b>not</b> {@code plugins/Fiefs}:
 * MockBukkit names it after the plugin's name AND version, so it is {@code plugins/Fiefs-0.11.0-SNAPSHOT}.
 * A test that writes to {@code plugins/Fiefs} does not fail. The plugin simply finds no file, starts
 * empty, and every assertion that the file was left alone passes for the wrong reason -- which is
 * exactly what {@code FiefsLifecycleTest}'s corrupt-file test did until this existed.
 *
 * <p>Reading the name and the version out of plugin.yml rather than hardcoding them keeps this working
 * across a version bump. It is still an assumption about a MockBukkit convention, so every caller checks
 * it against {@code getDataFolder()} once the plugin is up. See {@link #assertIsWhereThePluginLooked}.
 */
final class PluginDataFolder {

    private PluginDataFolder() {
    }

    /** Creates and returns the folder the plugin is about to treat as its own. */
    static File create() {
        YamlConfiguration pluginYml;
        try (InputStream resource = PluginDataFolder.class.getResourceAsStream("/plugin.yml")) {
            pluginYml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(Objects.requireNonNull(resource, "plugin.yml"),
                            StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not read plugin.yml from the test classpath", e);
        }
        File folder = new File(MockBukkit.getMock().getPluginsFolder(),
                pluginYml.getString("name") + "-" + pluginYml.getString("version"));
        assertTrue(folder.mkdirs() || folder.isDirectory(), "could not create " + folder);
        return folder;
    }

    /**
     * Confirms the guess above was right, which turns a silently vacuous test into a failing one.
     *
     * <p>Call it after the plugin is loaded. If MockBukkit ever changes how it names a data folder, this
     * is the line that says so instead of leaving a green suite that proves nothing.
     */
    static void assertIsWhereThePluginLooked(File guessed, org.bukkit.plugin.Plugin plugin) {
        org.junit.jupiter.api.Assertions.assertEquals(guessed, plugin.getDataFolder(),
                "this test wrote its save file somewhere the plugin never read, so it proves nothing");
    }
}
