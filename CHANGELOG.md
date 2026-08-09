# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added
- `/fi rename "new name"` command allowing fief owners to rename their fief (`fiefs.rename`)
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
- **One unreadable row in `fiefs.json` or `claimedchunks.json` no longer stops the plugin enabling.**
  An unreadable *file* still refuses to enable, which is right -- everything is unknown, and starting
  empty would let the next shutdown write `[]` over data that is still there. A single unreadable
  *row* is not that: the other forty entries are perfectly readable, and aborting over one of them
  cost the server every fief it had. A bad row is now skipped, logged with its contents, and **kept**
  -- written back out verbatim on the next save, so nothing unparseable is silently deleted, and
  logged at every startup so somebody fixes it

### Fixed
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

## [0.11.0]

### Added
- Fief creation, disbanding, and management integrated with Medieval Factions
- Territory claiming for fiefs within faction land
- Member invite, join, leave, kick, and transfer commands
- Fief flags and config management
