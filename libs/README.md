# Vendored API jar

One compile-time contract lives here as a committed jar rather than being built from source in CI,
the way the Medieval-Factions fork is.

The reason is access, not preference. `PatriamHeraldry` is a private repository, and a workflow's
default `GITHUB_TOKEN` is scoped to its own repository, so `actions/checkout` cannot reach it. The
alternatives were a personal access token held as a secret, which expires and then breaks CI
silently and much later, or making the repository public. Committing 51 KB of API surface was the
smaller cost. `PatriamMFAddon/libs/` vendors two other private APIs for exactly this reason, and
this follows it.

`provided` scope: it is needed to compile against and never shipped inside `Fiefs.jar`.
PatriamHeraldry unpacks these classes into its own plugin jar, so a second copy inside this one
would give the server two different `SubjectResolver` types and the `ServicesManager` lookup would
match neither.

## Provenance

| Jar | Source | Commit | Built with |
|---|---|---|---|
| `patriamheraldry-api-1.0.0.jar` | `Exophobias/PatriamHeraldry` (`master`) | `a1fe6cb1a588355c14c09fd61c436901f80e2635` | `./mvnw -pl patriamheraldry-api -am install`, JDK 25 |

That commit is dated 2026-08-09. `CHECKSUMS` records the sha256 of each file as committed, and CI
checks it. That proves the file is the one that was vetted and nothing more: it **cannot** tell you
the jar is up to date, because the source it came from is unreachable from CI, so a stale jar passes
the gate happily.

## Keeping it current

From a machine that can see both clones:

```
cd ../PatriamHeraldry
./mvnw -pl patriamheraldry-api -am install
cp ~/.m2/repository/com/github/exophobias/patriamheraldry-api/1.0.0/patriamheraldry-api-1.0.0.jar \
   ../Fiefs/libs/
cd ../Fiefs/libs && sha256sum -b *.jar *.pom | sed 's/ \*/ */' > CHECKSUMS
```

Then run the suite. `HeraldryAbsenceTest` is what catches a version skew that matters: if the api
moved a type this plugin implements, the bridge stops compiling, and if the api is missing entirely,
that tier proves Fiefs still works without it.

The `.pom` beside the jar is hand-written and must stay parentless. See the comment inside it; the
embedded descriptor in the jar inherits from a parent that is not vendored, and installing that one
makes every later dependency resolution fail on a missing parent.
