# Publishing `io.konaframework` artifacts

How maintainers publish Kona framework jars (and sister packages) for consumption. **Intended destination:** [Maven Central](https://central.sonatype.com/) under groupId `io.konaframework`.

**Gradle plumbing (this repo):** shared `com.vanniktech.maven.publish` convention in the root `build.gradle.kts` — POM metadata, sources jar, empty javadoc jar (Kotlin), optional GPG signing, Central Portal publisher, and `:bom` (`kona-bom`). **Local / CI still use** `publishToMavenLocal`. **Public Central release** still needs namespace claim, Portal token, GPG key, non-SNAPSHOT version, and a human-triggered tag.

Public install docs may still say generated apps resolve `io.konaframework:…:0.1.0-SNAPSHOT` from `mavenLocal()` until the first non-SNAPSHOT train lands on Central.

## Repos and coordinates

| Repo | Path (typical) | Version property | Publishable artifact(s) |
|------|----------------|------------------|-------------------------|
| [konaframework/kona](https://github.com/konaframework/kona) | `~/kona` | `kona.version` in `gradle.properties` | All `modules/*` named `kona-*` + `:bom` → `kona-bom` |
| [konaframework/kona-brew](https://github.com/konaframework/kona-brew) | `~/kona-brew` | `brew.version` | `io.konaframework:kona-brew` (`brew/`) |
| [konaframework/kona-inertia](https://github.com/konaframework/kona-inertia) | `~/kona-inertia` | `inertia.version` | `io.konaframework:kona-inertia` (`inertia/`) |

- **GroupId:** `io.konaframework` (set in each repo’s root `build.gradle.kts` `allprojects`).
- **Current development version (all three):** `0.1.0-SNAPSHOT` (also mirrored as `kona.version` / catalog `kona` in brew & inertia).
- **First public release target:** `0.1.0` (drop `-SNAPSHOT` on the release tag commit).
- **Not published:** samples (`hello-*`, `brew-sample-*`, `inertia-sample-*`), benchmarks, docs apps.

As of the last research pass, [Maven Central search](https://search.maven.org/solrsearch/select?q=g:io.konaframework) returned **zero** `io.konaframework` artifacts until a successful release. GitHub Packages is unused.

## What is configured today

### Shared publishing convention (`kona`)

Root `build.gradle.kts` applies [`com.vanniktech.maven.publish` 0.34.0](https://vanniktech.github.io/gradle-maven-publish-plugin/) to every `kona-*` Kotlin module and to `:bom`:

- Coordinates: `io.konaframework:<project.name>:<kona.version>` (`kona-bom` for the BOM)
- POM: name, description, URL, Apache-2.0 license, developers, SCM → `github.com/konaframework/kona`
- Artifacts: main jar + sources jar + empty javadoc jar (Kotlin)
- `publishToMavenCentral(automaticRelease = false)` — Central Portal (not legacy OSSRH)
- `signAllPublications()` **only when** `signingInMemoryKey` / `signing.keyId` (or `ORG_GRADLE_PROJECT_*` equivalents) are present — so `publishToMavenLocal` keeps working without GPG

Sister repos mirror the same pattern for `:kona-brew` / `:kona-inertia`.

### Tasks

| Task | Purpose |
|------|---------|
| `./gradlew publishToMavenLocal` | Local dry-run / installer / CI (works today) |
| `./gradlew publishToMavenCentral` | Upload to Central Portal (needs credentials + signing + non-SNAPSHOT for a release) |
| `./gradlew publishAndReleaseToMavenCentral` | Portal upload + automatic release (prefer explicit human release in the Portal unless you set `automaticRelease = true`) |

### CI pattern

| Workflow | Behavior |
|----------|----------|
| `kona` `.github/workflows/ci.yml` | Sister checkouts → `publishToMavenLocal` → brew/inertia local publish → tests |
| `kona` `.github/workflows/release.yml` | On `v*` tags: refuse SNAPSHOT versions; `publishToMavenCentral` with secrets |
| `kona-brew` / `kona-inertia` | Same local CI pattern; release workflows mirror tag publish after Kona |

### Installer / day-to-day path

`scripts/install.sh` publishes core modules (including `:bom`) then sister packages to Maven Local — same destination as CI.

## Artifact inventory (`kona`)

All at `io.konaframework:<artifactId>:<kona.version>`:

| Project | artifactId |
|---------|------------|
| `:bom` | `kona-bom` |
| `:kona-core` | `kona-core` |
| `:kona-filesystem` | `kona-filesystem` |
| `:kona-support` | `kona-support` |
| `:kona-http` | `kona-http` |
| `:kona-cadence` | `kona-cadence` |
| `:kona-auth` | `kona-auth` |
| `:kona-session` | `kona-session` |
| `:kona-redis` | `kona-redis` |
| `:kona-cache` | `kona-cache` |
| `:kona-crema` | `kona-crema` |
| `:kona-events` | `kona-events` |
| `:kona-queue` | `kona-queue` |
| `:kona-mail` | `kona-mail` |
| `:kona-notifications` | `kona-notifications` |
| `:kona-schedule` | `kona-schedule` |
| `:kona-grind` | `kona-grind` |
| `:kona-drip` | `kona-drip` |
| `:kona-process` | `kona-process` |
| `:kona-concurrency` | `kona-concurrency` |
| `:kona-testing` | `kona-testing` |
| `:kona-broadcasting` | `kona-broadcasting` |
| `:kona-search` | `kona-search` |
| `:kona-lsp` | `kona-lsp` |
| `:kona-cli` | `kona-cli` |

Sister repos (after Kona is available from Maven Local or Central):

| Project | artifactId | Depends on (Maven coords) |
|---------|------------|---------------------------|
| `:kona-brew` | `kona-brew` | `kona-core`, `http`, `crema`, `session`, `support`, `auth`, `cadence`, `filesystem` (+ compileOnly/test `kona-grind`) |
| `:kona-inertia` | `kona-inertia` | `kona-core`, `http`, `crema` |

## Ordered checklist

### A. Prerequisites (accounts & keys) — required before *any* public release

1. **Sonatype Central Portal account** for the `konaframework` org / maintainers  
   - Register at [central.sonatype.com](https://central.sonatype.com/).  
   - Claim / verify namespace **`io.konaframework`** (DNS or GitHub proof as Sonatype requires).
2. **GPG signing key** for releases  
   - Generate or reuse an org key; publish the public key to a keyserver (e.g. `keys.openpgp.org`).  
   - Keep the private key out of the repo (CI secret or local agent).
3. **Portal user token** from Central Portal → Account → Generate User Token.  
   - Gradle / env property names (wired):  
     - `mavenCentralUsername` / `mavenCentralPassword`  
     - or `ORG_GRADLE_PROJECT_mavenCentralUsername` / `ORG_GRADLE_PROJECT_mavenCentralPassword`
4. **Signing material** (either):  
   - In-memory (preferred for CI): `signingInMemoryKey` (armored private key, `\n` escaped or multiline secret) + `signingInMemoryKeyPassword`  
   - or classic: `signing.keyId` / `signing.password` / `signing.secretKeyRingFile`
5. **GitHub Actions secrets** (same names for `kona`, `kona-brew`, `kona-inertia` release workflows):

| Secret | Maps to |
|--------|---------|
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |
| `SIGNING_IN_MEMORY_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |

Do **not** commit tokens, GPG private keys, or `gradle.properties` with secrets.

### B. Version bump strategy

| Kind | Version shape | Notes |
|------|---------------|--------|
| Local / CI / install.sh | `0.1.0-SNAPSHOT` | Current default; overwritable in `~/.m2` |
| First public release | `0.1.0` | Must **not** be SNAPSHOT for a release tag |
| Sisters | Align `brew.version` / `inertia.version` and catalog `kona` with the Kona version they compile against | Publish Kona **before** brew/inertia |

Recommended flow for a release train:

1. Land all code on `main` (green CI).
2. On a release commit/tag: set `kona.version=0.1.0` (drop `-SNAPSHOT`).
3. Tag `v0.1.0` → `release.yml` runs `publishToMavenCentral` (or run locally with the same env).
4. In Central Portal, review the deployment and **Publish** if `automaticRelease` is false.
5. Bump brew/inertia `kona` catalog + their own versions to `0.1.0`; tag and publish sisters (**after** Kona is visible).
6. Bump back to `0.1.1-SNAPSHOT` on `main` for continued development.

### C. Module publish order

```bash
cd /path/to/kona
./gradlew publishToMavenLocal -x test   # or publishToMavenCentral for a real release
```

Then sisters (**after** Kona jars exist):

```bash
cd /path/to/kona-brew
./gradlew :kona-brew:publishToMavenLocal -x test

cd /path/to/kona-inertia
./gradlew :kona-inertia:publishToMavenLocal -x test
```

### D. Local dry-run (safe — do this anytime)

```bash
cd ~/kona && ./gradlew publishToMavenLocal -x test
cd ~/kona-brew && ./gradlew :kona-brew:publishToMavenLocal -x test
cd ~/kona-inertia && ./gradlew :kona-inertia:publishToMavenLocal -x test

ls ~/.m2/repository/io/konaframework/kona-core/0.1.0-SNAPSHOT/
ls ~/.m2/repository/io/konaframework/kona-bom/0.1.0-SNAPSHOT/
ls ~/.m2/repository/io/konaframework/kona-brew/0.1.0-SNAPSHOT/
ls ~/.m2/repository/io/konaframework/kona-inertia/0.1.0-SNAPSHOT/
```

### E. Real remote publish

```bash
# After namespace + secrets + non-SNAPSHOT version are ready:
cd ~/kona
./gradlew publishToMavenCentral --no-configuration-cache -x test
# Then brew → inertia with matching versions
```

Or push tag `v0.1.0` and let `.github/workflows/release.yml` run.

**Do not run a public Central publish** until:

1. Namespace `io.konaframework` is verified on the Central Portal.
2. Signing + Portal credentials exist (local or CI secrets above).
3. Version is a non-SNAPSHOT release version on the tagged commit.
4. A human explicitly asks to execute the release.

## Human steps still required (credentials / namespace)

Gradle and the release workflow are in place; the following are **not** done in-repo:

1. Claim **`io.konaframework`** on [central.sonatype.com](https://central.sonatype.com/) (DNS TXT or GitHub verification).
2. Generate a Portal user token; add the four GitHub secrets listed above to `kona`, `kona-brew`, and `kona-inertia`.
3. Create/export a GPG key; put the armored private key in `SIGNING_IN_MEMORY_KEY` and passphrase in `SIGNING_IN_MEMORY_KEY_PASSWORD`; publish the public key to a keyserver.
4. On a release branch/commit: set `kona.version=0.1.0` (and sister versions), open PRs, merge, tag `v0.1.0` on each repo in order **kona → brew → inertia**.
5. Confirm artifacts on [Central search](https://search.maven.org/solrsearch/select?q=g:io.konaframework) / Portal deployments UI.
6. Update public install docs to drop the “must publish locally” requirement for released versions.

## Blockers (current environment)

| Blocker | Status |
|---------|--------|
| Shared publish convention (POM / sources / javadoc / Central / signing hook) | **Implemented** in Gradle |
| `:bom` Maven publication (`kona-bom`) | **Implemented** |
| Tag `release.yml` workflow | **Implemented** (needs secrets) |
| Namespace `io.konaframework` claimed on Central | **Human** |
| Portal / signing credentials in env, `~/.gradle`, or GH secrets | **Missing** in agent environment |
| Non-SNAPSHOT version on a release tag | Still `0.1.0-SNAPSHOT` on `main` |
| Artifacts actually on Maven Central | **Not published** until humans complete the steps above |

**Not blocked:** local dry-run via `publishToMavenLocal` (Gradle, CI, and `install.sh`).

## Destination summary

| Destination | Intended? | Works today? |
|-------------|-----------|--------------|
| `mavenLocal` (`~/.m2`) | Yes — primary for SNAPSHOT / install / CI | **Yes** |
| Maven Central | Yes — public releases (`io.konaframework`) | **Plumbing yes; credentials/namespace no** |
| GitHub Packages | Optional / not specified in PLAN | **No** (not configured) |

## Related docs

- [HANDOFF.md](HANDOFF.md) — session resume; links here for publish commands
- [apps/docs … installation](../../apps/docs/src/content/docs/docs/guides/installation.mdx) — end-user install + mavenLocal note
- [PLAN.md](../architecture/PLAN.md) — groupId `io.konaframework`
- Sister READMEs: `kona-brew`, `kona-inertia` — local publish prerequisites
