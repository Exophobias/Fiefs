# Fiefs Commands

All commands use `/fi` or `/fiefs` as the base. Fiefs requires Medieval Factions to be installed.

| Command | Description | Permission |
|---------|-------------|------------|
| `/fi help {1\|2\|3}` | View command list (3 pages). | `fiefs.help` |
| `/fi list` | List the fiefs in your faction. | `fiefs.list` |
| `/fi create` | Create a new fief in your faction. | `fiefs.create` |
| `/fi disband` | Disband your fief. | `fiefs.disband` |
| `/fi info` | View information about your fief or another fief. | `fiefs.info` |
| `/fi members` | View members of your fief or another fief. | `fiefs.members` |
| `/fi join` | Join a fief you have been invited to. | `fiefs.join` |
| `/fi leave` | Leave your current fief. | `fiefs.leave` |
| `/fi invite` | Invite a player to your fief. | `fiefs.invite` |
| `/fi kick` | Kick a player from your fief. | `fiefs.kick` |
| `/fi transfer` | Transfer ownership of your fief to another player. | `fiefs.transfer` |
| `/fi heir [playerName\|clear]` | Name who inherits your fief if you depart, or show the standing nomination. | `fiefs.heir` |
| `/fi grant "fiefName" (playerName)` | Grant one of your faction's fiefs to a member of it. Head of the faction only. | `fiefs.grant` |
| `/fi revoke "fiefName"` | Take one of your faction's fiefs back into the faction's hands. Head of the faction only. | `fiefs.revoke` |
| `/fi desc` | Set a description for your fief. | `fiefs.desc` |
| `/fi rename` | Rename your fief. | `fiefs.rename` |
| `/fi claim` | Claim a chunk of faction land for your fief. | `fiefs.claim` |
| `/fi unclaim` | Unclaim a chunk of land from your fief. | `fiefs.unclaim` |
| `/fi checkclaim` | Check which fief owns the chunk you are standing in. | `fiefs.checkclaim` |
| `/fi flags` | View and alter your fief's configuration flags. | `fiefs.flags` |
| `/fi config` | View and alter plugin config options. | `fiefs.config` |

## Succession

A fief is held **from** a faction rather than owned outright, so it never simply disappears when the
player holding it goes. When a holder departs, by `/fi leave` or by leaving the faction, the fief
passes in this order:

1. the heir they named with `/fi heir`, if that player is still a member of the parent faction;
2. otherwise the longest-standing remaining member of the fief who is still in the parent faction;
3. otherwise the fief **reverts to the faction**, which keeps its name, land, members and flags but
   has no holder until the head of the faction grants it with `/fi grant`.

Nobody who has left the parent faction can inherit a fief held from it, whether they were named heir
or are the most senior member. The nomination is dropped whenever the fief changes hands, so each
holder names their own heir.

The head of the faction can also `/fi revoke` a fief from its holder and `/fi grant` it to somebody
else. Neither destroys anything: revoking leaves the fief standing with its members and land, exactly
as a reversion does.
