# Vendored API jar

One compile-time contract lives here as a committed jar rather than being built from source in CI,
the way the Medieval-Factions fork is.

The reason is access, not preference. `PatriamHeraldry` is a private repository, and a workflow's
default `GITHUB_TOKEN` is scoped to its own repository, so `actions/checkout` cannot reach it. The
alternatives were a personal access token held as a secret, which expires and then breaks CI
silently and much later, or making the repository public. Vendoring only the API surface avoids cross-repository credentials. `PatriamMFAddon/libs/` vendors two other private APIs for exactly this reason, and
this follows it.

`provided` scope: it is needed to compile against and never shipped inside `Fiefs.jar`.
PatriamHeraldry unpacks these classes into its own plugin jar, so a second copy inside this one
would give the server two different `SubjectResolver` types and the `ServicesManager` lookup would
match neither.

## Provenance

| Jar | Source | Commit | Built with |
|---|---|---|---|
| `patriamheraldry-api-1.1.0.jar` | `Exophobias/PatriamHeraldry` (`master`) | `4ac6683b6b13be0b8b1965ec4538261fc21de265` | canonical Paper 26.3 dependency build, JDK 25 |

Heraldry was refreshed separately on 2026-09-30 from the completed canonical Java 25 /
Paper 26.3 dependency build at `4ac6683b6b13be0b8b1965ec4538261fc21de265`. The 1.1.0 contract adds the permission view and
staff realm-binding API required by the current consumers. All 45 API class files were compared
byte for byte with that build's compiler output; subsequent Studio UI changes do not change
any API build input. The 118,540-byte jar has SHA256
`089edc73ac0ecef1d4998fce572d43e1a18554a9d73a49df75bacc4e44a4706c`.
The superseded 1.0.0 jar and descriptor were removed; the supplied 1.1.0 descriptor stays parentless.
Other private contracts retain their earlier provenance.

Historical receipt: refreshed on 2026-09-28 from `9c2e73478c3b07a50941283efb3a30097dfe072a`, with all 120 API tests passing. It includes
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
cp patriamheraldry-api/target/patriamheraldry-api-1.1.0.jar \
   ../Fiefs/libs/
cd ../Fiefs/libs && sha256sum -b *.jar *.pom | sed 's/ \*/ */' > CHECKSUMS
```

Use the shared build lock and a clean source checkout so the recorded commit describes the
actual jar. Packaging avoids installing Heraldry's parent-bearing descriptor; CI installs the
standalone descriptor beside this jar. Update its version and filename together with the jar,
remove the superseded contract, regenerate checksums, then run the suite. `HeraldryAbsenceTest` catches a
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
