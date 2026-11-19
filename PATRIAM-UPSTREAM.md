# Patriam upstream integration

November 19, 2026: official main through fe413ef (15 incoming commits since the
November 2 review) is integrated. Keep the existing published API coordinate, stable MF
integration and operator schema. Usage-reporting code/config/tests remain excluded: this
fork has no telemetry, and its strict preserving configuration lifecycle already rejects
unreadable files instead of writing defaults over them.

The upstream save-before-load fix adds a second storage admission barrier: neither save nor
persistence verification is admitted until both documents have loaded. A new load revokes
that admission before reading either file, so a failed reload cannot reuse an earlier success.
Retain the stronger existing plugin lifecycle flag, quarantine preservation, dirty retry,
atomic file writes, claim-coverage and durable deferred-unclaim checks. Regression fixtures
now load before mutating state; exact-byte tests cover never-loaded and failed-reload saves.

Bare `/fi` keeps the maintained permission-filtered help route and its `fiefs.help` gate.
The retained metadata command separately enforces `fiefs.default`, including its argument
entry point, while keeping Adventure messages and the existing constructor. Upstream
interaction tests use stable API/world doubles and retain owner/outsider controls. Keep the
existing embassy reservation and fail-closed interaction policy. Upstream Ponder/JUnit shade
exclusions are unnecessary here: Ponder and the shade plugin were already removed entirely.

November 2, 2026: official main through 093d346 (24 incoming commits) is integrated.
Preserve the existing published Fiefs coordinate and stable MedievalFactions API rather than
upstream's coupling to private MfFaction internals. Current actor/command governance, succession,
nonblocking UUID lookup, claim reservation fences and permission-filtered staff help remain.

The incoming multiword-name parser and strict Boolean/case handling duplicate stronger maintained
implementations: retain ArgumentParser (including null handling and preservation of quoted spaces)
and atomic ConfigService updates with the existing schema. No config keys/defaults changed.
Keep TraceClient deleted and reporting unconnected. Adapt console messages and successful description
feedback to Adventure; retain dirty-state marking. Invitations now supply an actual quoted join command.

Incoming characterization tests use stable API faction doubles, Adventure text, maintained constructor
contracts and five-minute dirty-only autosaves. Owner departure tests assert succession delegation
and preservation, not upstream's destructive fief deletion. Existing FiefSuccessionTest covers the
actual succession policies. Existing migration/help/claim/permission tests remain in place.
