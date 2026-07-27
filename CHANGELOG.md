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

### Changed
- **`/fi leave` no longer disbands a fief when its holder leaves.** It used to destroy the fief, its
  land and its name because one player walked away; it now runs succession
- A fief may have no holder while it waits to be granted. `FI_Fief.getOwner()` is nullable as a
  result, and gains `isVacant()` and `getHeir()`

## [0.11.0]

### Added
- Fief creation, disbanding, and management integrated with Medieval Factions
- Territory claiming for fiefs within faction land
- Member invite, join, leave, kick, and transfer commands
- Fief flags and config management
