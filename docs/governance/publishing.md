# Publishing `org.konaframework` artifacts

How maintainers publish Kona framework jars (and sister packages) for consumption. **Destination:** [Maven Central](https://central.sonatype.com/) under groupId `org.konaframework`.

**Gradle plumbing (this repo):** shared `com.vanniktech.maven.publish` convention in the root `build.gradle.kts` — POM metadata, sources jar, empty javadoc jar (Kotlin), optional GPG signing, Central Portal publisher, and `:bom` (`kona-bom`). **Local / CI still use** `publishToMavenLocal`. **Public Central release** needs a Portal user token, GPG key, non-SNAPSHOT version, and a human-triggered tag (see checklist below).

Public install docs may still say generated apps resolve `org.konaframework:…:0.1.0-SNAPSHOT` from `mavenLocal()` until the first non-SNAPSHOT train lands on Central.

## Namespace verified — next steps (first Central release)

**Status:** namespace **`org.konaframework`** is verified on the Central Publisher Portal. Gradle + tag workflow are on `main`. Artifacts are **not** on Central until a human completes the steps below.

Do these in order:

1. **GPG signing key** (required by Central for every release artifact)  
   - Generate or reuse an org key; publish the **public** key to a keyserver (e.g. `keys.openpgp.org`).  
   - Export the armored private key for CI: `gpg --export-secret-keys --armor <key-id>`.
2. **Central Portal user token** (not your login password)  
   - Portal → [User tokens](https://central.sonatype.com/usertoken) → Generate User Token.  
   - Note the token **username** and **password** once (they are not recoverable after the modal closes).
3. **GitHub Environment `maven-central`** on each of `kona`, `kona-brew`, and `kona-inertia`  
   (`Settings → Environments → maven-central`).  
   `release.yml` jobs set `environment: maven-central`, so the four secrets must be
   **Environment secrets** on that environment (not only repository secrets):

   | Secret | Value |
   |--------|--------|
   | `MAVEN_CENTRAL_USERNAME` | Portal token username |
   | `MAVEN_CENTRAL_PASSWORD` | Portal token password |
   | `SIGNING_IN_MEMORY_KEY` | Full armored private key (`-----BEGIN PGP PRIVATE KEY BLOCK-----` …) |
   | `SIGNING_IN_MEMORY_KEY_PASSWORD` | Key passphrase (empty string secret if none) |

   Optional: add required reviewers on the environment before the publish job runs.
4. **Release version commit**  
   - On a release branch: set `kona.version=0.1.0` in `gradle.properties` (drop `-SNAPSHOT`).  
   - Open PR → green CI → merge to `main`.
5. **Tag** `v0.1.0` on that merge commit (`git tag v0.1.0 <sha> && git push origin v0.1.0`).  
   - `.github/workflows/release.yml` runs `publishToMavenCentral` (refuses SNAPSHOT).
6. **Portal UI — Publish**  
   - Open [Deployments](https://central.sonatype.com/publishing/deployments).  
   - When the deployment is `VALIDATED`, click **Publish** (`automaticRelease` is `false` on purpose for the first train).  
   - Wait ~10–30 minutes; confirm on [Central search](https://search.maven.org/solrsearch/select?q=g:org.konaframework).
7. **Sisters after Kona is visible**  
   - Align `brew.version` / `inertia.version` + catalog `kona` to `0.1.0`.  
   - Same four secrets on those repos → tag `v0.1.0` → Portal Publish → kona → brew → inertia.
8. **Bump development versions** back to `0.1.1-SNAPSHOT` on each `main`.
9. **Docs** — drop “must publish locally” for released coordinates in public install guides.

### Trusted Publishing (OIDC) — not available yet for Central

“Trusted Publishing” here means **Maven Central Portal ↔ GitHub Actions OIDC** (no long-lived Portal password in repo secrets), analogous to PyPI/npm trusted publishers.

| Fact | Detail |
|------|--------|
| Official Central auth today | [Portal user token](https://central.sonatype.org/publish/generate-portal-token/) + **GPG-signed** artifacts |
| vanniktech `0.34.x` | Expects `mavenCentralUsername` / `mavenCentralPassword` (+ in-memory signing key) |
| GitHub Actions OIDC → Central | **Not generally available** in Sonatype’s public docs as of this writing; do not invent an unverified OIDC bridge |
| First release path | Portal token + GPG secrets (steps 1–6 above) |
| After first publish | Prefer shorter-lived Portal tokens; watch Sonatype / vanniktech for Trusted Publishing / OIDC; when GA, wire `permissions: id-token: write` and drop long-lived `MAVEN_CENTRAL_*` |

Do **not** block the first `0.1.0` train waiting for OIDC. Publish with tokens, then migrate when Central ships a supported trusted-publisher flow.

**Cloudflare note:** “first publish, then harden” for **docs hosting** is separate — see [docs-site.md](docs-site.md) (Cloudflare GitHub integration / dashboard without `CLOUDFLARE_*` GitHub secrets, optional Wrangler secrets later). That is not Maven Trusted Publishing.

## Repos and coordinates

| Repo | Path (typical) | Version property | Publishable artifact(s) |
|------|----------------|------------------|-------------------------|
| [konaframework/kona](https://github.com/konaframework/kona) | `~/kona` | `kona.version` in `gradle.properties` | All `modules/*` named `kona-*` + `:bom` → `kona-bom` |
| [konaframework/kona-brew](https://github.com/konaframework/kona-brew) | `~/kona-brew` | `brew.version` | `org.konaframework:kona-brew` (`brew/`) |
| [konaframework/kona-inertia](https://github.com/konaframework/kona-inertia) | `~/kona-inertia` | `inertia.version` | `org.konaframework:kona-inertia` (`inertia/`) |

- **GroupId:** `org.konaframework` (set in each repo’s root `build.gradle.kts` `allprojects`).
- **First public release version (all three):** `0.1.0` (non-SNAPSHOT on the release train; bump to `0.1.1-SNAPSHOT` after Central publish).
- Sisters also set catalog / `kona.version` to the same Kona release they compile against.
- **Not published:** samples (`hello-*`, `brew-sample-*`, `inertia-sample-*`), benchmarks, docs apps.

As of the last research pass, [Maven Central search](https://search.maven.org/solrsearch/select?q=g:org.konaframework) returned **zero** `org.konaframework` artifacts until a successful release. GitHub Packages is unused.

## What is configured today

### Shared publishing convention (`kona`)

Root `build.gradle.kts` applies [`com.vanniktech.maven.publish` 0.34.0](https://vanniktech.github.io/gradle-maven-publish-plugin/) to every `kona-*` Kotlin module and to `:bom`:

- Coordinates: `org.konaframework:<project.name>:<kona.version>` (`kona-bom` for the BOM)
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
| `kona` `.github/workflows/ci.yml` | Publishes **Kona only** to `mavenLocal`, then runs tests + Kover. Does **not** check out or publish `kona-brew` / `kona-inertia` — sisters are not required by this repo |
| `kona` `.github/workflows/release.yml` | On `v*` tags: `environment: maven-central`; refuse SNAPSHOT; `publishToMavenCentral` with Portal token + GPG |
| `kona-brew` / `kona-inertia` | Check out matching Kona → `publishToMavenLocal` → sister tests/samples + Kover; release workflows mirror tag publish after Kona |

Brew / Inertia kit HTTP smoke (component update, Inertia HTML + `X-Inertia` JSON) lives in the sister sample suites, not in `kona` CI.

### Installer / day-to-day path

`scripts/install.sh` publishes core modules (including `:bom`) then, when sister checkouts exist, publishes those packages to Maven Local. That local convenience path is **not** mirrored by `kona` CI.

## Artifact inventory (`kona`)

All at `org.konaframework:<artifactId>:<kona.version>`:

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

## Ordered checklist (reference)

### A. Prerequisites — accounts & keys

1. **Sonatype Central Portal account** — done for maintainers; namespace **`org.konaframework` verified**.  
2. **GPG signing key** — still required for first and subsequent releases.  
3. **Portal user token** — `mavenCentralUsername` / `mavenCentralPassword` (or `ORG_GRADLE_PROJECT_*`).  
4. **Signing material** — in-memory key preferred for CI (`signingInMemoryKey` + optional password).  
5. **GitHub Actions secrets** — table in [Namespace verified — next steps](#namespace-verified--next-steps-first-central-release).

Do **not** commit tokens, GPG private keys, or `gradle.properties` with secrets.

### B. Version bump strategy

| Kind | Version shape | Notes |
|------|---------------|--------|
| Local / CI / install.sh | `0.1.0-SNAPSHOT` | Current default; overwritable in `~/.m2` |
| First public release | `0.1.0` | Must **not** be SNAPSHOT for a release tag |
| Sisters | Align `brew.version` / `inertia.version` and catalog `kona` with the Kona version they compile against | Publish Kona **before** brew/inertia |

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

ls ~/.m2/repository/org/konaframework/kona-core/0.1.0-SNAPSHOT/
ls ~/.m2/repository/org/konaframework/kona-bom/0.1.0-SNAPSHOT/
ls ~/.m2/repository/org/konaframework/kona-brew/0.1.0-SNAPSHOT/
ls ~/.m2/repository/org/konaframework/kona-inertia/0.1.0-SNAPSHOT/
```

### E. Real remote publish

```bash
# After secrets + non-SNAPSHOT version are ready:
cd ~/kona
./gradlew publishToMavenCentral --no-configuration-cache -x test
# Then brew → inertia with matching versions
```

Or push tag `v0.1.0` and let `.github/workflows/release.yml` run.

**Do not run a public Central publish** until:

1. Namespace `org.konaframework` is verified (done).
2. Signing + Portal token exist (local or CI secrets).
3. Version is a non-SNAPSHOT release version on the tagged commit.
4. A human explicitly asks to execute the release.

## Human steps still required

Gradle and the release workflow are in place; the following are **not** done in-repo:

1. ~~Claim `org.konaframework`~~ — **done** (verified on Portal).
2. Generate a Portal user token; add the four secrets to Environment **`maven-central`** on `kona`, then brew/inertia.
3. Create/export a GPG key; put the armored private key in `SIGNING_IN_MEMORY_KEY` on that environment; publish the public key to a keyserver.
4. Release PR: non-SNAPSHOT `0.1.0` → merge → tag `v0.1.0` → Portal **Publish** → sisters → bump to `0.1.1-SNAPSHOT`.
5. Confirm artifacts on Central search / Portal deployments UI.
6. Update public install docs to drop the “must publish locally” requirement for released versions.
7. When Sonatype ships Trusted Publishing / OIDC for Central and vanniktech supports it: rewire `release.yml` and drop long-lived Portal passwords.

## Blockers (current environment)

| Blocker | Status |
|---------|--------|
| Shared publish convention (POM / sources / javadoc / Central / signing hook) | **Implemented** in Gradle |
| `:bom` Maven publication (`kona-bom`) | **Implemented** |
| Tag `release.yml` workflow | **Implemented** (needs secrets) |
| Namespace `org.konaframework` claimed on Central | **Verified** (human) |
| Portal / signing credentials on Environment `maven-central` | **Human** — create env secrets on each repo |
| Maven Central Trusted Publishing (OIDC) | **Not GA** — first release uses Portal token + GPG |
| Non-SNAPSHOT `0.1.0` on release train | Set in release PRs; tag after merge |
| Artifacts actually on Maven Central | **Not published** until tag + Portal **Publish** |

**Not blocked:** local dry-run via `publishToMavenLocal` (Gradle, CI, and `install.sh`).

## Destination summary

| Destination | Intended? | Works today? |
|-------------|-----------|--------------|
| `mavenLocal` (`~/.m2`) | Yes — primary for SNAPSHOT / install / CI | **Yes** |
| Maven Central | Yes — public releases (`org.konaframework`) | **Plumbing yes; token + GPG + tag no** |
| GitHub Packages | Optional / not specified in PLAN | **No** (not configured) |

## Related docs

- [HANDOFF.md](HANDOFF.md) — session resume; links here for publish commands
- [docs-site.md](docs-site.md) — Cloudflare Pages first publish without GitHub secrets
- [apps/docs … installation](../../apps/docs/src/content/docs/docs/guides/installation.mdx) — end-user install + mavenLocal note
- [PLAN.md](../architecture/PLAN.md) — groupId `org.konaframework`
- Sister READMEs: `kona-brew`, `kona-inertia` — local publish prerequisites
