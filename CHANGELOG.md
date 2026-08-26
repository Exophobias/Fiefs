# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Schema-1 configuration adoption. Existing unversioned files migrate automatically from schema 0
  into the bundled key order with exact backups, strict ambiguous/future YAML rejection, preserved
  explicit and extension values, and startup diagnostics. Invalid config now blocks startup before
  Fiefs reads or can rewrite mutable fief data.
- A `Dev Release` workflow, which republishes a rolling `dev` prerelease of `main` on every non-documentation push. This is what Dan's Plugin Manager's experimental channel installs from: `/dpm get fiefs --experimental` reads `releases/tags/dev`, so without it there is nothing for that command to download. The prerelease is unreleased, unreviewed code and is marked as such.
- Succession for fief holders. A departing holder's fief passes to their named heir, then to the
  longest-standing remaining member, then reverts to the parent faction. Anyone who has left that
  faction is passed over, since a fief is held from it
- `/fi heir (playerName|clear)` for the holder to name who inherits (`fiefs.heir`)
- `/fi grant "fiefName" (playerName)` and `/fi revoke "fiefName"` for the head of a faction to grant
  and take back its fiefs (`fiefs.grant`, `fiefs.revoke`)
- A third `/fi help` page for the three new commands
- `/fi capital` for the holder to name the fief's seat (`fiefs.capital`). A rebellion needs a chunk
  that can fall, and a fief with no capital cannot be seceded with
- `heldSince` on a fief, so how long its holder has held it can be asked
- `FiefsAPI` published through Bukkit's `ServicesManager`, so another plugin can read fiefs without
  naming a Fiefs class at link time
- **A stable id on every fief**, exposed as `FI_Fief.getId()` with `FiefsAPI.getFiefById(UUID)` beside
  it. A fief was found by name everywhere, and `/fi rename` changes the name, so anything another
  plugin stored about a fief pointed at whoever took the old name next. A fief saved before this
  existed is given an id as it loads, and `fiefs.json` is rewritten during that same boot rather than
  at the next shutdown: a crash between the two would mint a different id on the following boot, and
  the only symptom of that is a record that has quietly stopped belonging to anybody
- A PatriamHeraldry `SubjectResolver` for fiefs, so a fief can bear a coat of arms. It answers who a
  player is acting for and whether they may speak for it -- the fief's holder, or the head of its
  faction, which is the authority `/fi grant` already uses. PatriamHeraldry is a **soft** dependency:
  everything that names it lives in one guarded package, and Fiefs is unchanged on a server without it

### Changed

- **`/fi leave` no longer disbands a fief when its holder leaves.** It used to destroy the fief, its
  land and its name because one player walked away; it now runs succession
- A fief may have no holder while it waits to be granted. `FI_Fief.getOwner()` is nullable as a
  result, and gains `isVacant()` and `getHeir()`
- **One unreadable row in `fiefs.json` or `claimedChunks.json` no longer stops the plugin enabling.**
  An unreadable *file* still refuses to enable, which is right -- everything is unknown, and starting
  empty would let the next shutdown write `[]` over data that is still there. A single unreadable
  *row* is not that: the other forty entries are perfectly readable, and aborting over one of them
  cost the server every fief it had. A bad row is now skipped, logged with its contents, and **kept**
  -- written back out verbatim on the next save, so nothing unparseable is silently deleted, and
  logged at every startup so somebody fixes it

### Fixed

- The `Dev Release` workflow now retries publishing the `dev` prerelease before giving up. The release and its tag have to be deleted and recreated for the tag to move to the new commit, and a transient API failure inside that window previously left the repository with no `dev` release at all until the workflow was re-run by hand. Each attempt now starts from a clean slate, and an exhausted retry fails loudly.
- A fief's capital is cleared when Medieval-Factions unclaims that chunk, instead of pointing at land
  the fief no longer holds
- `Fief.members` and `PersistentData`'s collections are copy-on-write. They are mutated on the main
  thread by `/fi join`, `/fi kick`, `/fi leave` and the faction listener, and read from another
  plugin's asynchronous sweep; a concurrent grow makes a defensive `new ArrayList<>(members)` pad
  with nulls, which silently inflates any count taken from it -- and a count of a fief's members is
  exactly what a rebellion's majority test reads
- Save files are written to a temporary file and moved into place, so a crash or a full disk cannot
  leave a half-written save. The previous code truncated the real file before writing it
- `Fief.isSameFief` compares the fief's id. It compared holder, name and faction, which are three
  fields that all change while the fief stays the same fief: it answered false for a fief compared
  across a rename or a regrant, and true for two different fiefs that happened to agree on all three
- The test that proves a corrupt `fiefs.json` is never overwritten was writing to `plugins/Fiefs`,
  while MockBukkit's data folder is `plugins/Fiefs-<version>`. The plugin never opened the file, so it
  passed by leaving alone a file it had not read. It now writes where the plugin looks and asserts the
  plugin actually refused to enable

## [0.12.0-SNAPSHOT-8-8-2026] – 2026-08-08

### Changed
- Fiefs is now developed AI-first. Day-to-day feature work, grooming, review and maintenance run through AI agents working directly against this repository, with the maintainers setting direction and approving what lands. The version bump marks that change in how the project is built — it is not a break in behaviour, configuration or stored data, and existing installations can upgrade in place. Released as `0.12.0-SNAPSHOT-8-8-2026`: the AI-first line has not yet been verified in live operation, and the dated snapshot designation stays until it has.

### Added
- `/fi rename "new name"` command allowing fief owners to rename their fief (`fiefs.rename`)
- `/fi whois <player>` command allowing players to check which fief a given player is a member of (`fiefs.whois`)

### Fixed
- `fiefs.default`, the permission node `DefaultCommand` (bare `/fi`) declares, was missing from
  `plugin.yml` and undocumented; it is now registered with `default: true` and listed alongside
  every other command's permission node
- Fiefs saved to `fiefs.json` failed to load on startup, throwing a `NullPointerException` during
  plugin enable — a fief's flags were read before they were initialized. Servers with existing fief
  data could not start the plugin, and because the failed load left in-memory data empty, a
  subsequent save could write an empty `fiefs.json` over it
- `/fi kick` refused to kick any member with "That player is not in your fief." — the target's fief
  was looked up by fief name using the player's name instead of by their UUID
- `/fi invite` no longer invites a player who already belongs to another fief; the "already in a
  fief" check was looking the target up by fief name and never matched
- `/fi desc`'s no-argument usage message now shows double quotes, matching the double-quote parsing
  the command actually requires
- A load failure partway through `fiefs.json` or `claimedChunks.json` (a malformed entry or invalid
  JSON) no longer leaves in-memory fief/claim data empty; the file is now parsed fully before
  replacing the existing in-memory data, and saving is skipped until the file is fixed and the
  server restarted, so a bad load can no longer overwrite good data on disk
- A zero-byte `fiefs.json` or `claimedChunks.json` — which a crash or kill during the shutdown save
  can leave behind — was treated as a corrupt file, which disabled saving for the whole session and
  silently discarded every fief created or changed during it. An empty save file now loads as "no
  data", the same as a missing one, and leaves saving enabled
- The `./plugins/Fiefs/` save directory is now created with `mkdirs()` rather than `mkdir()`, so the
  save no longer fails silently in environments where `./plugins/` does not already exist
- An existing `fiefs.json` or `claimedChunks.json` that cannot be opened for reading (for example
  after a permission or ownership change on the server's data directory) was previously treated the
  same as a missing file, loading as "no fiefs/claims" and leaving saving enabled — so the next save
  could overwrite real data with an empty file. It is now treated as a failed load, matching the
  existing handling for a malformed file, and saving is skipped until the file is fixed
- `fiefs.json` and `claimedChunks.json` are now closed as soon as they have been read or written.
  The load left its reader open for the garbage collector to clean up, which on Windows kept the
  file locked long enough for a later save to fail, and a save that threw part-way through left its
  file open and unflushed

## [0.11.0]

### Added
- Fief creation, disbanding, and management integrated with Medieval Factions
- Territory claiming for fiefs within faction land
- Member invite, join, leave, kick, and transfer commands
- Fief flags and config management
