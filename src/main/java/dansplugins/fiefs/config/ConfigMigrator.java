package dansplugins.fiefs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict, template-first migration for Fiefs' operator-owned {@code config.yml}. */
public final class ConfigMigrator {
    public static final String VERSION_KEY = "config-version";
    public static final int CURRENT_VERSION = 1;

    private static final List<String> BOOLEAN_KEYS = List.of(
            "debugMode", "limitLand", "enableTerritoryAlerts");

    @FunctionalInterface
    interface ConfigWriter {
        void write(Path target, String contents, byte[] expectedCurrent) throws IOException;
    }

    public enum State {
        CURRENT,
        UPGRADED,
        INVALID,
        FUTURE,
        ERROR
    }

    /** A parsed runtime view derived from the exact bytes accepted by the migrator. */
    public record Prepared(YamlConfiguration configuration) {
        public Prepared {
            Objects.requireNonNull(configuration, "configuration");
        }
    }

    /** Sanitized migration outcome used by startup and the config status command. */
    public record Result(State state, int sourceVersion, Path backup, String detail,
                         Prepared prepared, byte[] loadedBytes) {
        public Result(State state, int sourceVersion, Path backup, String detail) {
            this(state, sourceVersion, backup, detail, null, null);
        }

        public Result {
            loadedBytes = loadedBytes == null ? null : loadedBytes.clone();
        }

        @Override
        public byte[] loadedBytes() {
            return loadedBytes == null ? null : loadedBytes.clone();
        }

        public boolean compatible() {
            return state == State.CURRENT || state == State.UPGRADED;
        }

        public int loadedVersion() {
            return compatible() ? CURRENT_VERSION : sourceVersion;
        }

        /** Rechecks the exact validated bytes immediately before runtime publication. */
        public boolean stillInstalled(Path configFile) throws IOException {
            return loadedBytes != null && Arrays.equals(loadedBytes, Files.readAllBytes(configFile));
        }
    }

    private record Version(boolean valid, int value, String issue) {
        private Version(boolean valid, int value) {
            this(valid, value, null);
        }
    }

    static final class FileContentChangedException extends IOException {
        private static final long serialVersionUID = 1L;

        private FileContentChangedException() {
            super("target changed while replacement was prepared");
        }
    }

    private ConfigMigrator() {
    }

    public static Result upgrade(Path configFile, String bundledYaml) {
        return upgrade(configFile, bundledYaml, ConfigMigrator::writeUtf8AtomicRequired);
    }

    static Result upgrade(Path configFile, String bundledYaml, ConfigWriter writer) {
        Objects.requireNonNull(configFile, "configFile");
        Objects.requireNonNull(bundledYaml, "bundledYaml");
        Objects.requireNonNull(writer, "writer");

        final byte[] installedBytes;
        try {
            installedBytes = Files.readAllBytes(configFile);
        } catch (IOException failure) {
            return new Result(State.ERROR, -1, null, "config.yml could not be read");
        }

        final String installedText;
        try {
            installedText = decodeUtf8(installedBytes);
        } catch (CharacterCodingException failure) {
            return new Result(State.ERROR, -1, null,
                    "the installed config.yml is not valid UTF-8");
        }

        Version installedVersion = readVersion(installedText);
        if (!installedVersion.valid()) {
            State state = "config.yml is not valid YAML".equals(installedVersion.issue())
                    ? State.ERROR : State.INVALID;
            return new Result(state, -1, null, installedVersion.issue() == null
                    ? VERSION_KEY + " must be one plain, unquoted decimal integer"
                    : installedVersion.issue());
        }
        if (installedVersion.value() > CURRENT_VERSION) {
            return new Result(State.FUTURE, installedVersion.value(), null,
                    "schema v" + installedVersion.value()
                            + " is newer than supported schema v" + CURRENT_VERSION);
        }

        final YamlConfiguration installed;
        final YamlConfiguration defaults;
        try {
            installed = parse(installedText);
        } catch (InvalidConfigurationException failure) {
            return new Result(State.ERROR, installedVersion.value(), null,
                    "the installed config.yml is not valid YAML");
        }
        try {
            defaults = parse(bundledYaml);
        } catch (InvalidConfigurationException failure) {
            return new Result(State.ERROR, installedVersion.value(), null,
                    "the plugin jar contains an invalid default config.yml");
        }

        Version bundledVersion = readVersion(bundledYaml);
        if (!bundledVersion.valid() || bundledVersion.value() != CURRENT_VERSION
                || validationIssue(defaults) != null) {
            return new Result(State.ERROR, installedVersion.value(), null,
                    "the plugin jar contains invalid configuration defaults");
        }

        int sourceVersion = installedVersion.value();
        YamlConfiguration candidate = installed;
        int workingVersion = sourceVersion;
        while (workingVersion < CURRENT_VERSION) {
            candidate = switch (workingVersion) {
                case 0 -> migrateZeroToOne(installed, bundledYaml);
                default -> null;
            };
            if (candidate == null) {
                return new Result(State.ERROR, sourceVersion, null,
                        "no migration exists from schema v" + workingVersion);
            }
            workingVersion++;
        }

        String issue = validationIssue(candidate);
        if (issue != null) {
            return new Result(State.INVALID, sourceVersion, null,
                    "the configuration candidate is invalid: " + issue);
        }

        if (sourceVersion == CURRENT_VERSION) {
            try {
                if (!Arrays.equals(installedBytes, Files.readAllBytes(configFile))) {
                    return new Result(State.ERROR, sourceVersion, null,
                            "config.yml changed while it was being validated; retry");
                }
            } catch (IOException failure) {
                return new Result(State.ERROR, sourceVersion, null,
                        "config.yml could not be rechecked after validation");
            }
            return new Result(State.CURRENT, sourceVersion, null,
                    "schema v" + CURRENT_VERSION + " is current",
                    new Prepared(candidate), installedBytes);
        }

        final Path backup;
        try {
            backup = createVerifiedBackup(configFile, installedBytes, sourceVersion);
        } catch (FileContentChangedException failure) {
            return new Result(State.ERROR, sourceVersion, null,
                    "config.yml changed while its migration backup was prepared; retry");
        } catch (AtomicMoveNotSupportedException failure) {
            return new Result(State.ERROR, sourceVersion, null,
                    "the filesystem cannot atomically create the migration backup");
        } catch (IOException failure) {
            return new Result(State.ERROR, sourceVersion, null,
                    "config.yml could not be backed up safely");
        }

        String migratedText = candidate.saveToString();
        byte[] migratedBytes = migratedText.getBytes(StandardCharsets.UTF_8);
        try {
            writer.write(configFile, migratedText, installedBytes);
        } catch (FileContentChangedException failure) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "config.yml changed while its replacement was prepared; it was not replaced");
        } catch (AtomicMoveNotSupportedException failure) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "the filesystem cannot atomically replace config.yml");
        } catch (IOException failure) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "the migrated config.yml could not replace the installed file");
        }

        try {
            byte[] promotedBytes = Files.readAllBytes(configFile);
            if (!Arrays.equals(migratedBytes, promotedBytes)) {
                return new Result(State.ERROR, sourceVersion, backup,
                        "config.yml changed immediately after migration; activation is blocked");
            }
            String promotedText = decodeUtf8(promotedBytes);
            Version promotedVersion = readVersion(promotedText);
            YamlConfiguration promoted = parse(promotedText);
            if (!promotedVersion.valid() || promotedVersion.value() != CURRENT_VERSION
                    || validationIssue(promoted) != null) {
                return new Result(State.ERROR, sourceVersion, backup,
                        "the promoted config.yml failed validation");
            }
            return new Result(State.UPGRADED, sourceVersion, backup,
                    "upgraded schema v" + sourceVersion + " to v" + CURRENT_VERSION,
                    new Prepared(promoted), promotedBytes);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            return new Result(State.ERROR, sourceVersion, backup,
                    "the promoted config.yml could not be reloaded and validated");
        }
    }

    /**
     * Atomically applies one plugin-owned boolean edit without mutating the live last-known-good
     * snapshot first. Unknown keys, comments, and their order are carried through Bukkit's parsed
     * representation; a concurrent operator edit wins the compare-and-swap race unchanged.
     */
    public static Result updateBoolean(Path configFile, Result active,
                                       String key, boolean value) {
        return updateBoolean(configFile, active, key, value,
                ConfigMigrator::writeUtf8AtomicRequired);
    }

    static Result updateBoolean(Path configFile, Result active, String key, boolean value,
                                ConfigWriter writer) {
        Objects.requireNonNull(configFile, "configFile");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(writer, "writer");
        if (!BOOLEAN_KEYS.contains(key)) {
            return new Result(State.INVALID, active.loadedVersion(), null,
                    "the requested config key is not plugin-owned");
        }
        byte[] expected = active.loadedBytes();
        if (!active.compatible() || active.prepared() == null || expected == null) {
            return new Result(State.ERROR, active.sourceVersion(), null,
                    "no exact active config snapshot is available for an update");
        }

        final YamlConfiguration candidate;
        try {
            String exactText = decodeUtf8(expected);
            Version version = readVersion(exactText);
            candidate = parse(exactText);
            if (!version.valid() || version.value() != CURRENT_VERSION
                    || validationIssue(candidate) != null) {
                return new Result(State.ERROR, active.sourceVersion(), null,
                        "the active config snapshot no longer passes validation");
            }
        } catch (CharacterCodingException | InvalidConfigurationException failure) {
            return new Result(State.ERROR, active.sourceVersion(), null,
                    "the active config snapshot could not be prepared for an update");
        }

        candidate.set(key, value);
        String updatedText = candidate.saveToString();
        byte[] updatedBytes = updatedText.getBytes(StandardCharsets.UTF_8);
        try {
            writer.write(configFile, updatedText, expected);
        } catch (FileContentChangedException failure) {
            return new Result(State.ERROR, active.sourceVersion(), null,
                    "config.yml changed while the update was prepared; the operator's edit won");
        } catch (AtomicMoveNotSupportedException failure) {
            return new Result(State.ERROR, active.sourceVersion(), null,
                    "the filesystem cannot atomically replace config.yml");
        } catch (IOException failure) {
            return new Result(State.ERROR, active.sourceVersion(), null,
                    "config.yml could not be updated safely");
        }

        try {
            byte[] promotedBytes = Files.readAllBytes(configFile);
            if (!Arrays.equals(updatedBytes, promotedBytes)) {
                return new Result(State.ERROR, CURRENT_VERSION, null,
                        "config.yml changed immediately after the update; activation is blocked");
            }
            String promotedText = decodeUtf8(promotedBytes);
            Version promotedVersion = readVersion(promotedText);
            YamlConfiguration promoted = parse(promotedText);
            if (!promotedVersion.valid() || promotedVersion.value() != CURRENT_VERSION
                    || validationIssue(promoted) != null) {
                return new Result(State.ERROR, CURRENT_VERSION, null,
                        "the updated config.yml failed exact post-write validation");
            }
            return new Result(State.CURRENT, CURRENT_VERSION, null,
                    "updated " + key + " in schema v" + CURRENT_VERSION,
                    new Prepared(promoted), promotedBytes);
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            return new Result(State.ERROR, CURRENT_VERSION, null,
                    "the updated config.yml could not be reloaded and validated");
        }
    }

    /** Uses the bundled layout and comments, then overlays explicit legacy choices. */
    private static YamlConfiguration migrateZeroToOne(YamlConfiguration installed,
                                                        String bundledYaml) {
        final YamlConfiguration candidate;
        try {
            candidate = parse(bundledYaml);
        } catch (InvalidConfigurationException impossible) {
            throw new IllegalStateException("bundled config was already parsed", impossible);
        }
        overlayInstalledValues(candidate, installed, true);
        candidate.set(VERSION_KEY, CURRENT_VERSION);
        return candidate;
    }

    private static void overlayInstalledValues(ConfigurationSection target,
                                               ConfigurationSection installed,
                                               boolean root) {
        List<String> knownKeys = new ArrayList<>(target.getKeys(false));
        List<String> installedKeys = new ArrayList<>(installed.getKeys(false));

        for (String key : knownKeys) {
            if ((root && VERSION_KEY.equals(key)) || !installedKeys.contains(key)) {
                continue;
            }
            Object bundledValue = target.get(key);
            Object installedValue = installed.get(key);
            if (bundledValue instanceof ConfigurationSection bundledSection
                    && installedValue instanceof ConfigurationSection installedSection) {
                overlayInstalledValues(bundledSection, installedSection, false);
            } else {
                replaceKnownValue(target, key, installedValue);
            }
        }
        for (String key : installedKeys) {
            // Legacy "version" was the jar version, changed every release, and was never an
            // operator setting. Schema 0 -> 1 deliberately removes it in favour of diagnostics.
            if ((root && (VERSION_KEY.equals(key) || "version".equals(key)))
                    || knownKeys.contains(key)) {
                continue;
            }
            copyValue(target, installed, key);
        }
    }

    private static void replaceKnownValue(ConfigurationSection target, String key,
                                          Object installedValue) {
        List<String> comments = target.getComments(key);
        List<String> inlineComments = target.getInlineComments(key);
        if (installedValue instanceof ConfigurationSection section) {
            ConfigurationSection replacement = target.createSection(key);
            for (String child : section.getKeys(false)) {
                copyValue(replacement, section, child);
            }
        } else {
            target.set(key, installedValue);
        }
        target.setComments(key, comments);
        target.setInlineComments(key, inlineComments);
    }

    private static void copyValue(ConfigurationSection target,
                                  ConfigurationSection installed,
                                  String key) {
        Object value = installed.get(key);
        if (value instanceof ConfigurationSection section) {
            ConfigurationSection copy = target.createSection(key);
            for (String child : section.getKeys(false)) {
                copyValue(copy, section, child);
            }
        } else {
            target.set(key, value);
        }
    }

    /** Returns key names and expected types only; configured values never enter diagnostics. */
    private static String validationIssue(ConfigurationSection candidate) {
        Object schema = candidate.get(VERSION_KEY);
        if (!(schema instanceof Integer) || ((Integer) schema) != CURRENT_VERSION) {
            return VERSION_KEY + " must equal " + CURRENT_VERSION;
        }
        for (String key : BOOLEAN_KEYS) {
            if (!(candidate.get(key) instanceof Boolean)) {
                return key + " must be a boolean";
            }
        }
        return null;
    }

    private static Version readVersion(String text) {
        final Object loaded;
        final Node document;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Yaml loader = new Yaml(new SafeConstructor(options));
            loaded = loader.load(text);

            LoaderOptions composeOptions = new LoaderOptions();
            composeOptions.setAllowDuplicateKeys(false);
            Yaml composer = new Yaml(new SafeConstructor(composeOptions));
            List<Node> documents = new ArrayList<>();
            composer.composeAll(new StringReader(text)).forEach(documents::add);
            if (documents.size() > 1) {
                return new Version(false, -1,
                        "config.yml must contain exactly one YAML document");
            }
            document = documents.isEmpty() ? null : documents.get(0);
        } catch (DuplicateKeyException duplicate) {
            return new Version(false, -1,
                    "config.yml contains duplicate or ambiguous YAML keys");
        } catch (RuntimeException malformed) {
            return new Version(false, -1, "config.yml is not valid YAML");
        }

        if (document == null) {
            return new Version(true, 0);
        }
        if (!(loaded instanceof Map<?, ?> mapping) || !(document instanceof MappingNode root)) {
            return new Version(false, -1, "config.yml must contain a top-level mapping");
        }
        String unsafeIssue = unsupportedYamlIssue(document,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (unsafeIssue != null) {
            return new Version(false, -1, unsafeIssue);
        }
        if (!mapping.containsKey(VERSION_KEY)) {
            return new Version(true, 0);
        }

        ScalarNode physicalValue = null;
        for (NodeTuple tuple : root.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key
                    && VERSION_KEY.equals(key.getValue())) {
                if (!key.isPlain() || !Tag.STR.equals(key.getTag())
                        || key.getAnchor() != null
                        || !physicalToken(text, key).equals(VERSION_KEY)
                        || !(tuple.getValueNode() instanceof ScalarNode value)
                        || !value.isPlain() || !Tag.INT.equals(value.getTag())
                        || value.getAnchor() != null
                        || !physicalToken(text, value).equals(value.getValue())
                        || physicalValue != null) {
                    return new Version(false, -1,
                            VERSION_KEY + " must be one plain, unquoted decimal integer");
                }
                physicalValue = value;
            }
        }
        Object raw = mapping.get(VERSION_KEY);
        if (physicalValue == null
                || !physicalValue.getValue().matches("0|[1-9][0-9]*")
                || !isInteger(raw)) {
            return new Version(false, -1,
                    VERSION_KEY + " must be one plain, unquoted decimal integer");
        }
        try {
            int value = Integer.parseInt(physicalValue.getValue());
            if (((Number) raw).longValue() != value) {
                return new Version(false, -1,
                        VERSION_KEY + " must be one plain, unquoted decimal integer");
            }
            return new Version(true, value);
        } catch (NumberFormatException failure) {
            return new Version(false, -1,
                    VERSION_KEY + " must fit a non-negative 32-bit integer");
        }
    }

    private static String unsupportedYamlIssue(Node node, Set<Node> visited) {
        if (!visited.add(node)) {
            return null;
        }
        if (Tag.NULL.equals(node.getTag())) {
            return "config.yml contains a null value that cannot be preserved";
        }
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                Node key = tuple.getKeyNode();
                if (Tag.MERGE.equals(key.getTag())) {
                    return "config.yml contains a YAML merge key that cannot be preserved";
                }
                if (!(key instanceof ScalarNode scalar) || !Tag.STR.equals(scalar.getTag())) {
                    return "config.yml contains a non-string mapping key";
                }
                String keyIssue = unsupportedYamlIssue(key, visited);
                if (keyIssue != null) {
                    return keyIssue;
                }
                String valueIssue = unsupportedYamlIssue(tuple.getValueNode(), visited);
                if (valueIssue != null) {
                    return valueIssue;
                }
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node child : sequence.getValue()) {
                String issue = unsupportedYamlIssue(child, visited);
                if (issue != null) {
                    return issue;
                }
            }
        } else if (node instanceof AnchorNode anchor) {
            return unsupportedYamlIssue(anchor.getRealNode(), visited);
        }
        return null;
    }

    private static boolean isInteger(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long;
    }

    private static String physicalToken(String text, ScalarNode scalar) {
        int start = scalar.getStartMark().getIndex();
        int end = scalar.getEndMark().getIndex();
        if (start < 0 || end < start || end > text.length()) {
            return "";
        }
        return text.substring(start, end).trim();
    }

    private static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(text);
        return yaml;
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }

    private static Path createVerifiedBackup(Path configFile, byte[] expected,
                                              int sourceVersion) throws IOException {
        Path parent = parentOf(configFile);
        Path temporary = Files.createTempFile(parent,
                "." + configFile.getFileName() + ".v" + sourceVersion + ".bak-", ".tmp");
        boolean promoted = false;
        try {
            writeForced(temporary, expected);
            if (!Arrays.equals(expected, Files.readAllBytes(configFile))) {
                throw new FileContentChangedException();
            }
            Path backup = nextBackupPath(configFile, sourceVersion);
            Files.move(temporary, backup, StandardCopyOption.ATOMIC_MOVE);
            promoted = true;
            return backup;
        } finally {
            if (!promoted) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Path nextBackupPath(Path configFile, int sourceVersion) {
        Path parent = configFile.toAbsolutePath().normalize().getParent();
        String base = configFile.getFileName() + ".v" + sourceVersion + ".bak";
        Path candidate = parent.resolve(base);
        for (int suffix = 1; Files.exists(candidate); suffix++) {
            candidate = parent.resolve(base + "." + suffix);
        }
        return candidate;
    }

    static void writeUtf8AtomicRequired(Path target, String content,
                                        byte[] expectedCurrent) throws IOException {
        Path parent = parentOf(target);
        Path temporary = parent.resolve("." + target.getFileName() + "."
                + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            Files.createFile(temporary);
            writeForced(temporary, content.getBytes(StandardCharsets.UTF_8));
            if (!Arrays.equals(expectedCurrent, Files.readAllBytes(target))) {
                throw new FileContentChangedException();
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void writeForced(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static Path parentOf(Path path) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("config.yml has no parent directory");
        }
        return parent;
    }
}
