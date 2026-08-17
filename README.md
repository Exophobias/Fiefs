# Fiefs

## Description

Fiefs is a Minecraft plugin that allows faction members to create fiefs (sub-factions) within [Medieval Factions](https://github.com/Dans-Plugins/Medieval-Factions). Fiefs function as sub-factions, allowing for more granular organization within a faction.

## Installation

### First Time Installation

1. Download the plugin from the [releases page](https://github.com/Dans-Plugins/Fiefs/releases).
2. Place the jar in the `plugins` folder of your server.
3. Restart your server.

### Dependencies

This plugin depends on [Medieval Factions](https://github.com/Dans-Plugins/Medieval-Factions) in order to work.

## Usage

### Documentation

- [User Guide](USER_GUIDE.md) – Getting started and common scenarios
- [Commands Reference](COMMANDS.md) – Complete list of all commands
- [Configuration Guide](CONFIG.md) – Detailed configuration options

### Wiki & Additional Resources

- [Wiki Guide](https://github.com/Dans-Plugins/Fiefs/wiki/Guide)
- [FAQ](https://github.com/Dans-Plugins/Fiefs/wiki/FAQ)

## Support

You can find the support Discord server [here](https://discord.gg/xXtuAQ2).

### Experiencing a bug?

Please fill out a bug report [here](https://github.com/Dans-Plugins/Fiefs/issues/new?template=bug_report.md).

- [Known Bugs](https://github.com/Dans-Plugins/Fiefs/issues?q=is%3Aopen+is%3Aissue+label%3Abug)

## Contributing

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [Notes for Developers](https://github.com/Dans-Plugins/Fiefs/wiki/Developer-Notes)

## Testing

### Build Verification

Linux / macOS:

    mvn clean package

Windows:

    mvn clean package

If you see `BUILD SUCCESS`, the project has built successfully. `mvn clean package` also runs the JUnit test suite under `src/test/`.

## Development

### Building the Plugin

1. Clone the repository: `git clone https://github.com/Dans-Plugins/Fiefs.git`
2. Build the plugin: `mvn clean package`
3. The compiled JAR will be in the `target/` directory.

### Manual Testing

1. Build the plugin with `mvn clean package`.
2. Copy the JAR from `target/` into your test server's `plugins/` folder.
3. Start or restart the server.

## Authors and Acknowledgement

### Developers

| Name | Main Contributions |
|------|--------------------|
| Daniel Stephenson | Creator |

This plugin was requested by Laughingspade.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0).

You are free to use, modify, and distribute this software, provided that:

- Source code is made available under the same license when distributed.
- Changes are documented and attributed.
- No additional restrictions are applied.

See the [LICENSE](LICENSE) file for the full text of the GPL-3.0 license.

## Project Status

This project is in active development.

### bStats

You can view the bStats page for the plugin [here](https://bstats.org/plugin/bukkit/Fiefs/12743).
