package dansplugins.fiefs.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void currentConfigIsValidatedWithoutRewriteOrBackup() throws Exception {
        String current = template().replace("debugMode: false", "debugMode: true");
        Path file = write(current);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.CURRENT, result.state());
        assertEquals(1, result.sourceVersion());
        assertNull(result.backup());
        assertArrayEquals(current.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
        assertTrue(result.prepared().configuration().getBoolean("debugMode"));
        assertEquals(0, backupCount());
    }

    @Test
    void schemaZeroUsesTemplateOrderAndPreservesExplicitAndUnknownValues() throws Exception {
        String legacy = """
                version: v0.11.0
                debugMode: true
                limitLand: false
                enableTerritoryAlerts: true
                extension:
                  nested:
                    token: keep-me
                """;
        byte[] original = legacy.getBytes(StandardCharsets.UTF_8);
        Path file = write(legacy);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());
        String migrated = Files.readString(file);

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertEquals(0, result.sourceVersion());
        assertArrayEquals(original, Files.readAllBytes(result.backup()));
        assertTrue(migrated.indexOf("config-version: 1") < migrated.indexOf("debugMode: true"));
        assertTrue(migrated.indexOf("debugMode: true") < migrated.indexOf("limitLand: false"));
        assertTrue(migrated.indexOf("enableTerritoryAlerts: true")
                < migrated.indexOf("extension:"));
        assertTrue(migrated.contains("token: keep-me"));
        assertFalse(migrated.matches("(?s).*\nversion:.*"));
        assertTrue(migrated.contains("# Enables verbose diagnostic logging."));
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        Path file = write(legacy(false, true, false));

        ConfigMigrator.Result first = ConfigMigrator.upgrade(file, template());
        byte[] once = Files.readAllBytes(file);
        ConfigMigrator.Result second = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.UPGRADED, first.state());
        assertEquals(ConfigMigrator.State.CURRENT, second.state());
        assertArrayEquals(once, Files.readAllBytes(file));
        assertEquals(1, backupCount());
    }

    @Test
    void emptyLegacyFileAdoptsAllDefaults() throws Exception {
        Path file = write("");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertTrue(Files.readString(file).contains("config-version: 1"));
        assertFalse(result.prepared().configuration().getBoolean("debugMode"));
        assertTrue(result.prepared().configuration().getBoolean("limitLand"));
    }

    @Test
    void flowStyleLegacyRootIsAValidSchemaZeroInput() throws Exception {
        Path file = write("{debugMode: true, limitLand: false, enableTerritoryAlerts: true, x: 7}\n");

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertTrue(result.prepared().configuration().getBoolean("debugMode"));
        assertEquals(7, result.prepared().configuration().getInt("x"));
    }

    @Test
    void flowStyleCurrentRootWithPlainMarkerIsAcceptedUnchanged() throws Exception {
        String current = "{config-version: 1, debugMode: false, limitLand: true, "
                + "enableTerritoryAlerts: true, extension: retained}\n";
        Path file = write(current);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.CURRENT, result.state(), result.detail());
        assertEquals(current, Files.readString(file));
        assertEquals("retained", result.prepared().configuration().getString("extension"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "config-version:\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: null\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: '1'\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "'config-version': 1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "\"config\\x2dversion\": 1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: !!int 1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: &schema 1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: -1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: 01\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: +1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: 1.0\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: 1\nconfig-version: 1\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n",
            "config-version: 999\n\"config\\x2dversion\": 0\ndebugMode: false\nlimitLand: true\nenableTerritoryAlerts: true\n"
    })
    void ambiguousMarkersAreRejectedWithoutWrites(String contents) throws Exception {
        Path file = write(contents);
        byte[] before = Files.readAllBytes(file);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.INVALID, result.state());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(0, backupCount());
    }

    @Test
    void futureVersionIsRejectedWithoutDowngrade() throws Exception {
        String future = template().replace("config-version: 1", "config-version: 2");
        Path file = write(future);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.FUTURE, result.state());
        assertEquals(2, result.sourceVersion());
        assertEquals(future, Files.readString(file));
        assertEquals(0, backupCount());
    }

    @Test
    void malformedYamlAndMultipleDocumentsAreRejected() throws Exception {
        for (String invalid : Arrays.asList(
                "debugMode: [unterminated\n",
                legacy(false, true, true) + "---\ndebugMode: false\n")) {
            Path file = write(invalid);
            byte[] before = Files.readAllBytes(file);

            ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

            assertFalse(result.compatible());
            assertArrayEquals(before, Files.readAllBytes(file));
            Files.delete(file);
        }
        assertEquals(0, backupCount());
    }

    @Test
    void nullAndMergeEntriesAreRejectedBecauseBukkitCannotPreserveThemExactly() throws Exception {
        String explicitNull = legacy(false, true, true) + "extension: null\n";
        Path nullFile = write(explicitNull);
        ConfigMigrator.Result nullResult = ConfigMigrator.upgrade(nullFile, template());
        assertEquals(ConfigMigrator.State.INVALID, nullResult.state());
        assertEquals(explicitNull, Files.readString(nullFile));

        Files.delete(nullFile);
        String merge = """
                defaults: &defaults
                  debugMode: false
                <<: *defaults
                limitLand: true
                enableTerritoryAlerts: true
                """;
        Path mergeFile = write(merge);
        ConfigMigrator.Result mergeResult = ConfigMigrator.upgrade(mergeFile, template());
        assertEquals(ConfigMigrator.State.INVALID, mergeResult.state());
        assertEquals(merge, Files.readString(mergeFile));
        assertEquals(0, backupCount());
    }

    @Test
    void wrongKnownTypeAndIncompleteCurrentSchemaAreRejected() throws Exception {
        String wrongType = legacy(false, true, true).replace("debugMode: false", "debugMode: nope");
        Path file = write(wrongType);
        ConfigMigrator.Result legacyResult = ConfigMigrator.upgrade(file, template());
        assertEquals(ConfigMigrator.State.INVALID, legacyResult.state());
        assertEquals(wrongType, Files.readString(file));

        Files.delete(file);
        String incomplete = "config-version: 1\ndebugMode: false\nlimitLand: true\n";
        file = write(incomplete);
        ConfigMigrator.Result currentResult = ConfigMigrator.upgrade(file, template());
        assertEquals(ConfigMigrator.State.INVALID, currentResult.state());
        assertEquals(incomplete, Files.readString(file));
        assertEquals(0, backupCount());
    }

    @Test
    void invalidUtf8IsRejectedUnchanged() throws Exception {
        Path file = temporaryDirectory.resolve("config.yml");
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};
        Files.write(file, invalid);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertArrayEquals(invalid, Files.readAllBytes(file));
        assertEquals(0, backupCount());
    }

    @Test
    void atomicWriteFailureLeavesInstalledBytesAndExactBackup() throws Exception {
        String legacy = legacy(true, false, true);
        Path file = write(legacy);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template(),
                (target, contents, expected) -> { throw new IOException("simulated"); });

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertEquals(legacy, Files.readString(file));
        assertNotNull(result.backup());
        assertArrayEquals(legacy.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(result.backup()));
    }

    @Test
    void concurrentOperatorEditWinsTheCompareAndSwapRace() throws Exception {
        Path file = write(legacy(false, true, true));
        String external = legacy(true, false, false) + "external: retained\n";

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template(),
                (target, contents, expected) -> {
                    Files.writeString(target, external);
                    ConfigMigrator.writeUtf8AtomicRequired(target, contents, expected);
                });

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertEquals(external, Files.readString(file));
        assertNotNull(result.backup());
    }

    @Test
    void unexpectedPostWriteBytesBlockActivation() throws Exception {
        Path file = write(legacy(false, true, true));

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template(),
                (target, contents, expected) -> Files.writeString(target, contents + "tampered: true\n"));

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertNull(result.prepared());
        assertTrue(result.detail().contains("changed immediately"));
    }

    @Test
    void commandOwnedUpdateIsAtomicAndNeverMutatesTheActiveSnapshotFirst() throws Exception {
        String current = template() + "extension:\n  retained: opaque\n";
        Path file = write(current);
        ConfigMigrator.Result active = ConfigMigrator.upgrade(file, template());

        ConfigMigrator.Result updated = ConfigMigrator.updateBoolean(
                file, active, "debugMode", true);

        assertEquals(ConfigMigrator.State.CURRENT, updated.state(), updated.detail());
        assertFalse(active.prepared().configuration().getBoolean("debugMode"));
        assertTrue(updated.prepared().configuration().getBoolean("debugMode"));
        assertEquals("opaque", updated.prepared().configuration().getString("extension.retained"));
        assertArrayEquals(Files.readAllBytes(file), updated.loadedBytes());
        assertEquals(0, backupCount(), "routine plugin-owned edits do not create migration backups");

        String operatorEdit = Files.readString(file) + "operator-edit: retained\n";
        ConfigMigrator.Result raced = ConfigMigrator.updateBoolean(
                file, updated, "limitLand", false, (target, contents, expected) -> {
                    Files.writeString(target, operatorEdit, StandardCharsets.UTF_8);
                    ConfigMigrator.writeUtf8AtomicRequired(target, contents, expected);
                });
        assertEquals(ConfigMigrator.State.ERROR, raced.state());
        assertEquals(operatorEdit, Files.readString(file));
        assertTrue(updated.prepared().configuration().getBoolean("limitLand"));
    }

    @Test
    void existingBackupNameIsNeverOverwritten() throws Exception {
        Path occupied = temporaryDirectory.resolve("config.yml.v0.bak");
        Files.writeString(occupied, "do-not-replace");
        Path file = write(legacy(false, true, true));

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file, template());

        assertEquals(ConfigMigrator.State.UPGRADED, result.state());
        assertEquals("do-not-replace", Files.readString(occupied));
        assertEquals("config.yml.v0.bak.1", result.backup().getFileName().toString());
    }

    @Test
    void invalidBundledTemplateCannotRewriteAnOperatorFile() throws Exception {
        String legacy = legacy(false, true, true);
        Path file = write(legacy);

        ConfigMigrator.Result result = ConfigMigrator.upgrade(file,
                template().replace("config-version: 1", "config-version: 2"));

        assertEquals(ConfigMigrator.State.ERROR, result.state());
        assertEquals(legacy, Files.readString(file));
        assertEquals(0, backupCount());
    }

    private Path write(String contents) throws IOException {
        Path file = temporaryDirectory.resolve("config.yml");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private long backupCount() throws IOException {
        try (var files = Files.list(temporaryDirectory)) {
            return files.filter(path -> path.getFileName().toString().contains(".bak")).count();
        }
    }

    private static String legacy(boolean debug, boolean limit, boolean alerts) {
        return "debugMode: " + debug + "\n"
                + "limitLand: " + limit + "\n"
                + "enableTerritoryAlerts: " + alerts + "\n";
    }

    private static String template() throws IOException {
        try (InputStream input = ConfigMigratorTest.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new IOException("test classpath does not contain config.yml");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
