# Fiefs User Guide

## What is Fiefs?

Fiefs is a Spigot plugin that adds a sub-faction territory system to Medieval Factions servers. Faction members can create fiefs — named sub-groups within their faction — and claim chunks of faction land for those fiefs.

## Requirements

- [Medieval Factions](https://github.com/Dans-Plugins/Medieval-Factions) must be installed.

## Installation

1. Download the latest `Fiefs-<version>.jar` from the [Releases](https://github.com/Dans-Plugins/Fiefs/releases) page.
2. Place the JAR (and the Medieval Factions JAR) in your server's `plugins/` folder.
3. Restart the server.

## Getting Started

1. Create a fief within your faction: `/fi create "Fief Name"`
2. Invite members: `/fi invite <player>`
3. Claim faction land for your fief: stand in a faction-owned chunk and run `/fi claim`
4. Check fief ownership of a chunk: `/fi checkclaim`
5. View all fiefs in your faction: `/fi list`

## Holding a fief

A fief is held **from** your faction rather than owned outright, so it outlives whoever holds it.
Name an heir with `/fi heir <player>` and they take the fief if you depart. Without an heir it goes
to the fief's longest-standing member, and if there is nobody left it reverts to the faction, keeping
its name, land and members until the head of the faction grants it again with
`/fi grant "<fief>" <player>`. Somebody who has left the faction cannot inherit a fief held from it.

The head of the faction may also take a fief back with `/fi revoke "<fief>"`. That leaves the fief
standing and only removes its holder; use `/fi disband` if the intent really is to destroy it.

## Who Can Do What

- Inviting players, kicking members, viewing and setting flags, renaming, transferring and disbanding a fief are for its owner only; another member is told so.
- Any member of a fief can claim faction land for it, unclaim its land and change its description. Claiming needs land the faction has claimed that no fief holds yet; unclaiming works only on land the player's own fief holds.
- A player in no fief cannot claim or unclaim land for a fief.
- Fiefs adds no restriction of its own to faction land that no fief has claimed: there, only Medieval Factions' rules apply (its members can build and use blocks, subject to its locks, gates and other rules), whatever fief a player is in.

## Fief Flags

A fief's owner views and changes its flags with `/fi flags show` and `/fi flags set <flag> <value>`.

| Flag | Default | Effect |
|------|---------|--------|
| `claimedLandProtected` | `true` | When `true`, only members of the fief can build, break blocks, open containers and doors, and use item frames and armour stands in the land the fief has claimed; other members of the faction, in another fief or in none, are refused. When `false`, the fief's land is open to every member of the faction (Medieval Factions' own protection still keeps other factions out). Only the flag of the fief that holds the land counts. |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `fiefs.default` | `true` | View plugin version and info (bare `/fi`). |
| `fiefs.help` | `true` | View the help menu. |
| `fiefs.list` | `true` | List fiefs. |
| `fiefs.create` | `true` | Create a fief. |
| `fiefs.disband` | `true` | Disband a fief. |
| `fiefs.info` | `true` | View fief information. |
| `fiefs.members` | `true` | View fief members. |
| `fiefs.join` | `true` | Join a fief. |
| `fiefs.leave` | `true` | Leave a fief. |
| `fiefs.invite` | `true` | Invite a player to a fief. |
| `fiefs.kick` | `true` | Kick a player from a fief. |
| `fiefs.transfer` | `true` | Transfer fief ownership. |
| `fiefs.heir` | `true` | Name who inherits your fief if you depart. |
| `fiefs.grant` | `true` | Grant one of your faction's fiefs. Checked against the head of the faction at execution. |
| `fiefs.revoke` | `true` | Take one of your faction's fiefs back. Checked against the head of the faction at execution. |
| `fiefs.desc` | `true` | Set a fief description. |
| `fiefs.rename` | `true` | Rename a fief. |
| `fiefs.claim` | `true` | Claim a chunk for a fief. |
| `fiefs.unclaim` | `true` | Unclaim a chunk from a fief. |
| `fiefs.checkclaim` | `true` | Check which fief owns a chunk. |
| `fiefs.flags` | `true` | View and alter fief flags. |
| `fiefs.config` | `op` | View and alter plugin config options. |
| `fiefs.whois` | `true` | Check which fief a player is a member of. |

## Support

Ask questions in the [Discord server](https://discord.gg/xXtuAQ2) or open a [GitHub issue](https://github.com/Dans-Plugins/Fiefs/issues).
