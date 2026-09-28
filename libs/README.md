# Vendored API jar

One compile-time contract lives here as a committed jar rather than being built from source in CI,
the way the Medieval-Factions fork is.

The reason is access, not preference. `PatriamHeraldry` is a private repository, and a workflow's
default `GITHUB_TOKEN` is scoped to its own repository, so `actions/checkout` cannot reach it. The
alternatives were a personal access token held as a secret, which expires and then breaks CI
silently and much later, or making the repository public. Committing 74 KB of API surface was the
smaller cost. `PatriamMFAddon/libs/` vendors two other private APIs for exactly this reason, and
this follows it.

`provided` scope: it is needed to compile against and never shipped inside `Fiefs.jar`.
PatriamHeraldry unpacks these classes into its own plugin jar, so a second copy inside this one
would give the server two different `SubjectResolver` types and the `ServicesManager` lookup would
match neither.

## Provenance

| Jar | Source | Commit | Built with |
|---|---|---|---|
| `patriamheraldry-api-1.0.0.jar` | `Exophobias/PatriamHeraldry` (`master`) | `9c2e73478c3b07a50941283efb3a30097dfe072a` | `mvn -pl patriamheraldry-api -am clean package`, JDK 25 |

Refreshed on 2026-09-28 from that clean commit, with all 120 API tests passing. It includes
`SubjectPublicationChangedEvent` and the current `SubjectResolver` contract required by
`FiefSubjectResolver`. The earlier August API copy lacked those members and failed compilation.

`CHECKSUMS` records the sha256 of each file as committed, and CI
checks it. That proves the file is the one that was vetted and nothing more: it **cannot** tell you
the jar is up to date, because the source it came from is unreachable from CI, so a stale jar passes
the gate happily.

The row above previously named `a1fe6cb`, and the jar really had gone stale against it. Between that
commit and `63f77ed` the api grew per-charge entitlement (`ChargeCode`, `ChargeCodeError`,
`ChargeEntitlement`), gained a JavaScript port of the codec and the validator, and took two more
charges into `vocabulary.json`. None of that is used from here, which is exactly why nothing
complained.

## Keeping it current

From a machine that can see both clones:

```
cd ../PatriamHeraldry
./mvnw -pl patriamheraldry-api -am clean package
cp patriamheraldry-api/target/patriamheraldry-api-1.0.0.jar \
   ../Fiefs/libs/
cd ../Fiefs/libs && sha256sum -b *.jar *.pom | sed 's/ \*/ */' > CHECKSUMS
```

Use the shared build lock and a clean source checkout so the recorded commit describes the
actual jar. Packaging avoids installing Heraldry's parent-bearing descriptor; CI installs the
standalone descriptor beside this jar. Then run the suite. `HeraldryAbsenceTest` catches a
version skew that matters: if the api
moved a type this plugin implements, the bridge stops compiling, and if the api is missing entirely,
that tier proves Fiefs still works without it.

`PatriamMFAddon/tools/refresh-vendored-api.sh` rebuilds this same module and compares it against the
copy in **that** repository's `libs/`. It knows nothing about this one. So a refresh over there
leaves this copy stale and says nothing about it, which is how this row came to name a commit five
api changes behind. Do both in the same sitting, or this file is the one that rots.

The `.pom` beside the jar is hand-written and must stay parentless. See the comment inside it; the
embedded descriptor in the jar inherits from a parent that is not vendored, and installing that one
makes every later dependency resolution fail on a missing parent.
