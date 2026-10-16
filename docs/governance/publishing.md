# Publishing `io.konaframework` artifacts

How maintainers publish Kona framework jars (and sister packages) for consumption. **Intended long-term destination:** [Maven Central](https://central.sonatype.com/) under groupId `io.konaframework`. **Today:** only `mavenLocal` (`~/.m2/repository`) is wired; there is no remote repository, signing, or release workflow yet.

Public install docs already state this: generated apps resolve `io.konaframework:…:0.1.0-SNAPSHOT` from `mavenLocal()` / Central, and SNAPSHOT jars are **not** on Central until this guide’s “Remote publish” work lands.

## Repos and coordinates

| Repo | Path (typical) | Version property | Publishable artifact(s) |
|------|----------------|------------------|-------------------------|
| [konaframework/kona](https://github.com/konaframework/kona) | `~/kona` | `kona.version` in `gradle.properties` | All `modules/*` with `maven-publish` (see inventory) |
| [konaframework/kona-brew](https://github.com/konaframework/kona-brew) | `~/kona-brew` | `brew.version` | `io.konaframework:kona-brew` (`brew/`) |
| [konaframework/kona-inertia](https://github.com/konaframework/kona-inertia) | `~/kona-inertia` | `inertia.version` | `io.konaframework:kona-inertia` (`inertia/`) |

- **GroupId:** `io.konaframework` (set in each repo’s root `build.gradle.kts` `allprojects`).
- **Current version (all three):** `0.1.0-SNAPSHOT` (also mirrored as `kona.version` / catalog `kona` in brew & inertia).
- **BOM:** `bom/` is a `java-platform` with constraints; it does **not** apply `maven-publish` yet (not in the local publish set).
- **Not published:** samples (`hello-*`, `brew-sample-*`, `inertia-sample-*`), benchmarks, docs apps.

As of this writing, [Maven Central search](https://search.maven.org/solrsearch/select?q=g:io.konaframework) returns **zero** `io.konaframework` artifacts. GitHub Packages is unused by Gradle config (no `maven.pkg.github.com` repository blocks).

## What is configured today

### Per-module publishing (local only)

Every library module uses the same minimal pattern:

```kotlin
plugins {
    `maven-publish`
    // …
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "kona-…"
        }
    }
}
```

There is **no**:

- `publishing.repositories { … }` (no Central / GitHub Packages / custom Nexus URL)
- `signing` plugin or GPG configuration
- POM enrichment (`name`, `description`, `url`, `licenses`, `developers`, `scm`)
- `withSourcesJar()` / `withJavadocJar()`
- Release / tag GitHub Actions workflow that runs `publish`

Root `./gradlew tasks --group=publishing` therefore only exposes Maven Local tasks (`publishToMavenLocal`, `publishMavenPublicationToMavenLocal`). The generic `publish` task has nowhere remote to go until repositories are added.

### CI pattern

| Workflow | Behavior |
|----------|----------|
| `kona` `.github/workflows/ci.yml` | Builds sister checkouts, then `./gradlew publishToMavenLocal`, then brew/inertia `:…:publishToMavenLocal`, then tests |
| `kona-brew` / `kona-inertia` CI | Checkout/build Kona `main` → `publishToMavenLocal` → publish own artifact to Maven Local → test |

No `release.yml` / tag-triggered publish exists in any of the three product repos.

### Installer / day-to-day path

`scripts/install.sh` publishes core modules (ordered list) then sister packages to Maven Local — same destination as CI. That is the supported path for local development until Central is live.

## Artifact inventory (`kona`)

All at `io.konaframework:<artifactId>:<kona.version>`:

| Project | artifactId |
|---------|------------|
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

1. **Sonatype Central Portal account** for the `konaframework` org / maintainers who will publish  
   - Register at [central.sonatype.com](https://central.sonatype.com/).  
   - Claim / verify namespace **`io.konaframework`** (DNS or GitHub proof as Sonatype requires). **Not done yet** if search still shows zero artifacts.
2. **GPG signing key** for releases (Central requires signed artifacts for published releases)  
   - Generate or reuse an org key; publish the public key to a keyserver.  
   - Keep the private key out of the repo (CI secret or local agent).
3. **Portal user token** (username + password/token pair from Central Portal) for Gradle.  
   - Typical Gradle property names (convention; wire when implementing remote publish):  
     - `mavenCentralUsername` / `mavenCentralPassword`  
     - or `ORG_GRADLE_PROJECT_mavenCentralUsername` / `ORG_GRADLE_PROJECT_mavenCentralPassword`  
     - older OSSRH-era names (`sonatypeUsername` / `ossrh*`) only if you deliberately use a legacy plugin — prefer the current Central Portal publisher API / `com.vanniktech.maven.publish` / Gradle `maven-publish` + Portal docs.
4. **Optional GitHub Packages** (not configured; only if you choose it as a staging channel): `GITHUB_TOKEN` with `write:packages`, repository URL `https://maven.pkg.github.com/konaframework/<repo>`.
5. **CI secrets** (when automating): store signing material + Portal token in GitHub Actions secrets for `kona`, `kona-brew`, `kona-inertia`. As of the research pass for this doc, local `~/.gradle/gradle.properties` had **no** signing/Portal keys, and no publish-related env vars were present in the agent environment.

Do **not** commit tokens, GPG private keys, or `gradle.properties` with secrets.

### B. Version bump strategy

| Kind | Version shape | Notes |
|------|---------------|--------|
| Local / CI / install.sh | `0.1.0-SNAPSHOT` | Current default; overwritable in `~/.m2` |
| First public release | `0.1.0` (or agreed semver) | Must **not** be SNAPSHOT for Central release |
| Sisters | Align `brew.version` / `inertia.version` and catalog `kona` with the Kona version they compile against | Publish Kona **before** brew/inertia |

Recommended flow for a release train:

1. Land all code on `main` (green CI).
2. On a release branch/tag: set `kona.version=X.Y.Z` (drop `-SNAPSHOT`).
3. Publish Kona modules (+ BOM once publishing is added).
4. Bump brew/inertia `kona` catalog + their own versions to `X.Y.Z`; publish sisters.
5. Tag repos (`vX.Y.Z`); open GitHub Releases if desired.
6. Bump back to `X.Y.(Z+1)-SNAPSHOT` on `main` for continued development.

Keep brew/inertia `libs.versions.toml` `[versions].kona` in lockstep with the Kona jars they resolve (today both pin `0.1.0-SNAPSHOT` and prefer `mavenLocal` for `io.konaframework`).

### C. Module publish order

Gradle resolves inter-project dependencies when publishing from the Kona root, so a single root command is enough for local publish:

```bash
cd /path/to/kona
./gradlew publishToMavenLocal -x test
```

`install.sh` uses an explicit order (safe if you publish subsets): roughly  
`core` → `filesystem` → `support` → `http` → … → `cli` (see `PUBLISH_TASKS` in `scripts/install.sh`).

Then sisters (**after** Kona jars exist in Maven Local or Central):

```bash
cd /path/to/kona-brew
./gradlew :kona-brew:publishToMavenLocal -x test

cd /path/to/kona-inertia
./gradlew :kona-inertia:publishToMavenLocal -x test
```

Do **not** publish brew/inertia first: their `build.gradle.kts` repositories put `mavenLocal { includeGroup("io.konaframework") }` ahead of Central, and catalogs depend on `io.konaframework:kona-*`.

### D. Local dry-run (safe — do this anytime)

```bash
# Full Kona set → ~/.m2/repository/io/konaframework/...
cd ~/kona && ./gradlew publishToMavenLocal -x test

# Sisters
cd ~/kona-brew && ./gradlew :kona-brew:publishToMavenLocal -x test
cd ~/kona-inertia && ./gradlew :kona-inertia:publishToMavenLocal -x test

# Sanity
ls ~/.m2/repository/io/konaframework/kona-core/0.1.0-SNAPSHOT/
ls ~/.m2/repository/io/konaframework/kona-brew/0.1.0-SNAPSHOT/
ls ~/.m2/repository/io/konaframework/kona-inertia/0.1.0-SNAPSHOT/
```

Optional: `./gradlew publishToMavenLocal --dry-run` lists tasks without writing jars.

This is the **only** publish path that works with the current Gradle config.

### E. Real remote publish — **blocked until Gradle + accounts are ready**

After implementing remote publishing (see “Work still required”), a typical release command shape will look like:

```bash
# Example only — task names depend on the plugin you add
cd ~/kona
./gradlew publish --no-configuration-cache
# or plugin-specific: ./gradlew publishToMavenCentral / publishAndReleaseToMavenCentral
```

Then the same for brew and inertia with their release versions.

**Do not run a public Central publish** until:

1. Namespace `io.konaframework` is verified on the Central Portal.
2. Signing + Portal credentials exist (local or CI).
3. Publications include required POM metadata + sources (and javadoc or equivalent Central requirement).
4. Version is a non-SNAPSHOT release version.
5. A human explicitly asks to execute the release.

## Work still required (implementation, not this doc)

Before the first Central release, expect a focused Gradle/CI change (separate PR from this guide):

1. Shared publishing convention (root `build.gradle.kts` or `buildSrc` / convention plugin): POM metadata, sources/javadoc jars, signing, Central repository/publisher.
2. Enable `maven-publish` on `:bom` (artifact id e.g. `kona-bom`) and keep constraints in sync (including comments pointing at brew/inertia).
3. Map credentials via `ORG_GRADLE_PROJECT_*` or env; document secret names in this file when chosen.
4. Add a **tag/release** workflow that publishes on `v*` tags (and never on every push to `main` for release versions).
5. Mirror the same publishing convention in `kona-brew` and `kona-inertia`.
6. Update public install docs once non-SNAPSHOT jars are on Central so apps can drop the “must publish locally” requirement for released versions.

Suggested plugin options (pick one and standardize): Sonatype’s current Central Portal Gradle instructions, or `com.vanniktech.maven.publish` (common for Kotlin multi-module). Do **not** embed Quarkus/Micronaut/Spring for publishing.

## Blockers (current)

| Blocker | Status |
|---------|--------|
| Remote `publishing.repositories` / Central publisher | **Missing** |
| GPG signing plugin + keys | **Missing** |
| POM license/SCM/developer metadata | **Missing** |
| Sources / javadoc jars | **Missing** |
| `:bom` Maven publication | **Missing** |
| Namespace `io.konaframework` on Central with published artifacts | **None published** (search empty) |
| Portal / signing credentials in env or `~/.gradle` | **Not present** in researched environment |
| GitHub Actions publish/release workflow | **Missing** (CI only uses Maven Local) |
| Non-SNAPSHOT version | Still `0.1.0-SNAPSHOT` everywhere |
| Explicit human go-ahead to publish publicly | Required by process |

**Not blocked:** local dry-run via `publishToMavenLocal` (supported by Gradle, CI, and `install.sh`).

## Destination summary

| Destination | Intended? | Works today? |
|-------------|-----------|--------------|
| `mavenLocal` (`~/.m2`) | Yes — primary for SNAPSHOT / install / CI | **Yes** |
| Maven Central | Yes — public releases (`io.konaframework`) | **No** (config + namespace + credentials) |
| GitHub Packages | Optional / not specified in PLAN | **No** (not configured) |

## Related docs

- [HANDOFF.md](HANDOFF.md) — session resume; links here for publish commands
- [apps/docs … installation](../../apps/docs/src/content/docs/docs/guides/installation.mdx) — end-user install + mavenLocal note
- [PLAN.md](../architecture/PLAN.md) — groupId `io.konaframework`
- Sister READMEs: `kona-brew`, `kona-inertia` — local publish prerequisites
