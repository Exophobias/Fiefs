# Fiefs Configuration

Configuration can be viewed and changed in-game with `/fi config`. Command-owned changes use an
atomic compare-and-swap write and become live only after exact post-write validation; a concurrent
operator edit wins and the previous runtime settings stay active. A `config.yml` is generated in
`plugins/Fiefs/` on first run.

Fiefs uses a top-level integer `config-version`, currently `1`. An existing file without that key is
schema 0: on the next start Fiefs makes an exact same-directory backup, rebuilds the file in the
bundled order, and places newly shipped settings where this guide and the bundled template define
them. Existing explicit values and recursively nested unknown extension keys are retained; the old
generated `version` key is removed because jar version belongs in diagnostics, not operator config.
Old/local comments are replaced by the current bundled comments.

A blank, quoted, duplicated, malformed, negative, or future marker—and YAML that would lose an
explicit null/merge entry—is rejected without changing the installed file. Fiefs then stays disabled
before loading its mutable fief data. Fix the named structural problem or restore the exact `.bak`
file and restart. Fiefs has no credential-bearing config values, so owner-only backup permissions are
not required. Unknown keys are accepted as extension keys and remain after their known siblings.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `config-version` | Integer | `1` | Configuration schema. Do not edit manually. |
| `debugMode` | Boolean | `false` | Enables verbose debug logging to the console. |
| `limitLand` | Boolean | `true` | Whether fiefs are restricted to land already claimed by their faction. |
| `enableTerritoryAlerts` | Boolean | `true` | Whether players receive a message when entering or leaving fief territory. |
