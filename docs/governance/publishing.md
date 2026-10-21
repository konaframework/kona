# Publishing `org.konaframework` artifacts

How maintainers publish Kona framework jars (including Brew and Inertia) for consumption. **Destination:** [Maven Central](https://central.sonatype.com/) under groupId `org.konaframework`.

**Gradle plumbing (this repo):** shared `com.vanniktech.maven.publish` convention in the root `build.gradle.kts` — POM metadata, sources jar, empty javadoc jar (Kotlin), optional GPG signing, Central Portal publisher, and `:bom` (`kona-bom`). **Local / CI still use** `publishToMavenLocal`. **Public Central release** needs a Portal user token, GPG key, non-SNAPSHOT version, and a human-triggered tag (see checklist below).

Generated apps resolve `org.konaframework:…` from Maven Central. The curl installer (`scripts/install.sh`) checks out the latest Central `kona-cli` release tag, not `main`.

## Last published release — `0.2.0`

**Status:** **shipped.** Tag `v0.2.0` → `release.yml` uploaded; Portal **Publish** completed. On Maven Central as `org.konaframework:kona-cli:0.2.0` (and the rest of the train). **Do not** re-tag or republish `0.2.0`. Prior cuts: `0.1.1` (first remonorepo train), `0.1.0`. Sister repos remain archived.

## `main` vs Central

`kona.version` on `main` may still read `0.2.0` while `main` has commits **after** the tag. Those commits are unreleased. **`curl …/scripts/install.sh | bash` installs the latest Maven Central release (git tag `vX.Y.Z`), not `main`.** Use `./scripts/install.sh --local` or `--dev` / `KONA_REF=main` for unreleased trees.

### When ready to ship the next train

1. Confirm `main` is green, `kona.version` is the new non-SNAPSHOT, and release notes are ready.
2. Human tags: `git tag vX.Y.Z <merge-sha> && git push origin vX.Y.Z`.
3. Wait for `.github/workflows/release.yml` → Environment `maven-central`.
4. Portal **Publish** when `VALIDATED` (`automaticRelease=false`).

### Other follow-ups

1. Confirm artifacts on [Central search](https://search.maven.org/solrsearch/select?q=g:org.konaframework) (`kona-core`, `kona-bom`, `kona-cli`, `kona-brew`, `kona-inertia`, …).
2. **Usage / File Count** — org hit Central **File Count** monthly limit (grace through **2026-11-17**). Check [Usage Center](https://central.sonatype.com/); if still tight, email `central-support@sonatype.com` for a higher limit. See [Reducing Publishing Usage](https://central.sonatype.org/publish/reducing-publishing-usage/).

### File-count mitigations (this stack)

- Required per module: jar + pom + sources + javadoc + `.asc` signatures (keep).
- Optional reduction (Sonatype / vanniktech): skip checksums of `.asc` and drop unused `sha256`/`sha512` checksums. That lands in **vanniktech `0.37+`**, which needs **Gradle 9** — this repo is still Gradle **8.14** / plugin **0.34.0**, so those defaults are **not** enabled yet. Prefer Usage Center + limit increase for `0.1.1`; schedule Gradle 9 + plugin bump later.

## Namespace verified — first Central cut (`0.1.0`)

**Status:** namespace **`org.konaframework`** is verified. Tag `v0.1.0` was pushed; release workflow uploaded once. Remonorepo of Brew + Inertia landed **after** that tag — they ship in **`0.1.1`**.

Prerequisites (still required for every release):

1. **GPG signing key** — public key on a keyserver; armored private key in CI.
2. **Central Portal user token** — [User tokens](https://central.sonatype.com/usertoken).
3. **GitHub Environment `maven-central`** on `konaframework/kona` with:

   | Secret | Value |
   |--------|--------|
   | `MAVEN_CENTRAL_USERNAME` | Portal token username |
   | `MAVEN_CENTRAL_PASSWORD` | Portal token password |
   | `SIGNING_IN_MEMORY_KEY` | Full armored private key (`-----BEGIN PGP PRIVATE KEY BLOCK-----` …) |
   | `SIGNING_IN_MEMORY_KEY_PASSWORD` | Key passphrase (empty string secret if none) |

### Trusted Publishing (OIDC) — not available yet for Central

“Trusted Publishing” here means **Maven Central Portal ↔ GitHub Actions OIDC** (no long-lived Portal password in repo secrets), analogous to PyPI/npm trusted publishers.

| Fact | Detail |
|------|--------|
| Official Central auth today | [Portal user token](https://central.sonatype.org/publish/generate-portal-token/) + **GPG-signed** artifacts |
| vanniktech `0.34.x` | Expects `mavenCentralUsername` / `mavenCentralPassword` (+ in-memory signing key) |
| GitHub Actions OIDC → Central | **Not generally available** in Sonatype’s public docs as of this writing; do not invent an unverified OIDC bridge |
| Release path | Portal token + GPG secrets |
| After publish | Prefer shorter-lived Portal tokens; watch Sonatype / vanniktech for Trusted Publishing / OIDC |

**Cloudflare note:** docs hosting is separate — see [docs-site.md](docs-site.md). That is not Maven Trusted Publishing.

## Repo and coordinates

| Repo | Path (typical) | Version property | Publishable artifact(s) |
|------|----------------|------------------|-------------------------|
| [konaframework/kona](https://github.com/konaframework/kona) | `~/kona` | `kona.version` in `gradle.properties` | All `modules/*` named `kona-*` (including `modules/brew` → `kona-brew`, `modules/inertia` → `kona-inertia`) + `:bom` → `kona-bom` |

- **GroupId:** `org.konaframework` (root `build.gradle.kts` `allprojects`).
- **Version train:** one `kona.version` for every artifact (including brew/inertia). **Monorepo only** — no sister-repo tags.
- **Not published:** samples (`hello-*`, `brew-sample-*`, `inertia-sample-*`), docs apps, `js/brew`.
- Archived sister repos `kona-brew` / `kona-inertia` are no longer publish sources.

## What is configured today

### Shared publishing convention (`kona`)

Root `build.gradle.kts` applies [`com.vanniktech.maven.publish` 0.34.0](https://vanniktech.github.io/gradle-maven-publish-plugin/) to every `kona-*` Kotlin module and to `:bom`:

- Coordinates: `org.konaframework:<project.name>:<kona.version>` (`kona-bom` for the BOM)
- POM: name, description, URL, Apache-2.0 license, developers, SCM → `github.com/konaframework/kona`
- Artifacts: main jar + sources jar + empty javadoc jar (Kotlin)
- `publishToMavenCentral(automaticRelease = false)` — Central Portal (not legacy OSSRH); human **Publish** in Portal after `VALIDATED`
- `signAllPublications()` **only when** `signingInMemoryKey` / `signing.keyId` (or `ORG_GRADLE_PROJECT_*` equivalents) are present — so `publishToMavenLocal` keeps working without GPG

`:kona-brew` and `:kona-inertia` use the same convention (they are `kona-*` Kotlin modules). `:bom` constrains both.

### Tasks

| Task | Purpose |
|------|---------|
| `./gradlew publishToMavenLocal` | Local dry-run / installer / CI (works today) |
| `./gradlew publishToMavenCentral` | Upload to Central Portal (needs credentials + signing + non-SNAPSHOT for a release) |
| `./gradlew publishAndReleaseToMavenCentral` | Portal upload + automatic release (prefer explicit human release in the Portal unless you set `automaticRelease = true`) |

### CI pattern

| Workflow | Behavior |
|----------|----------|
| `.github/workflows/ci.yml` | `publishToMavenLocal` (all `kona-*` including brew/inertia) → Kover on every production module → sample smokes (`hello-*`, `brew-sample-*`, `inertia-sample-*`) + Brew JS Vitest job |
| `.github/workflows/release.yml` | On `v*` tags: `environment: maven-central`; refuse SNAPSHOT; `publishToMavenCentral` for the **whole** train (one Gradle invocation) |

### Installer / day-to-day path

`scripts/install.sh` (curl path) resolves `<release>` from Maven Central `kona-cli` metadata, clones that git tag into `~/.kona`, then publishes that tree to Maven Local and links `~/.local/bin/kona`. `--local` builds the current checkout instead. `--dev` / `KONA_REF=main` installs unreleased `main`. It does **not** need sister-repo clones.

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
| `:kona-brew` | `kona-brew` (`modules/brew`) |
| `:kona-inertia` | `kona-inertia` (`modules/inertia`) |
| `:kona-cli` | `kona-cli` |

## Ordered checklist (reference)

### A. Prerequisites — accounts & keys

1. **Sonatype Central Portal account** — done for maintainers; namespace **`org.konaframework` verified**.  
2. **GPG signing key** — still required for first and subsequent releases.  
3. **Portal user token** — `mavenCentralUsername` / `mavenCentralPassword` (or `ORG_GRADLE_PROJECT_*`).  
4. **Signing material** — in-memory key preferred for CI (`signingInMemoryKey` + optional password).  
5. **GitHub Actions secrets** — Environment **`maven-central`** table above.

Do **not** commit tokens, GPG private keys, or `gradle.properties` with secrets.

### B. Version bump strategy

| Kind | Version shape | Notes |
|------|---------------|--------|
| Local / CI / `--local` | `kona.version` on the checkout | Overwritable in `~/.m2` |
| Curl installer default | latest Central `kona-cli` | Tag `vX.Y.Z`; currently `0.2.0` |
| Public release (last cut) | `0.2.0` | Tagged `v0.2.0`; on Central |
| Prior cuts | `0.1.1`, `0.1.0` | `0.1.0` predates in-tree brew/inertia |

### C. Module publish order

```bash
cd /path/to/kona
./gradlew publishToMavenLocal -x test   # or publishToMavenCentral for a real release
# Includes :kona-brew and :kona-inertia (and :bom constraints)
```

### D. Local dry-run (safe — do this anytime)

```bash
cd ~/kona && ./gradlew publishToMavenLocal -x test

ls ~/.m2/repository/org/konaframework/kona-core/0.2.0/
ls ~/.m2/repository/org/konaframework/kona-bom/0.2.0/
ls ~/.m2/repository/org/konaframework/kona-brew/0.2.0/
ls ~/.m2/repository/org/konaframework/kona-inertia/0.2.0/
```

### E. Real remote publish

```bash
# After secrets + non-SNAPSHOT version are ready:
cd ~/kona
./gradlew publishToMavenCentral --no-configuration-cache -x test
```

Or push a new version tag (e.g. `v0.2.0` when ready) and let `.github/workflows/release.yml` run.

**Do not run a public Central publish** until:

1. Namespace `org.konaframework` is verified (done).
2. Signing + Portal token exist (local or CI secrets).
3. Version is a non-SNAPSHOT release version on the tagged commit.
4. A human explicitly tags / asks to execute the release.

## Human steps still required

1. ~~Claim `org.konaframework`~~ — **done** (verified on Portal).
2. Portal token + GPG secrets on Environment **`maven-central`** (done for `0.1.0` upload; keep current).
3. ~~Release PR `0.1.1` → tag → upload → Portal Publish~~ — **done** (`v0.1.1` on Central).
4. Confirm artifacts stay visible on Central search / Portal deployments UI.
5. **`kona.version=0.2.0` is on `main` but not tagged** — keep public install docs on `0.1.1` until `v0.2.0` ships.
6. Watch File Count / Usage Center; request a higher limit if needed before grace ends (2026-11-17).
7. When Sonatype ships Trusted Publishing / OIDC for Central and vanniktech supports it: rewire `release.yml` and drop long-lived Portal passwords.
8. Later: Gradle 9 + vanniktech ≥0.37 for default signature-checksum exclusion (file-count reduction).

## Blockers (current environment)

| Blocker | Status |
|---------|--------|
| Shared publish convention (POM / sources / javadoc / Central / signing hook) | **Implemented** in Gradle |
| `:bom` Maven publication (`kona-bom`) incl. brew/inertia constraints | **Implemented** |
| Tag `release.yml` workflow (whole monorepo train) | **Implemented** |
| Namespace `org.konaframework` claimed on Central | **Verified** (human) |
| Portal / signing credentials on Environment `maven-central` | **Human** — env secrets on `kona` |
| Maven Central Trusted Publishing (OIDC) | **Not GA** — releases use Portal token + GPG |
| Non-SNAPSHOT `0.1.1` on release train | **Done** — tagged `v0.1.1` |
| Artifacts actually on Maven Central | **Done** for `0.1.0` / `0.1.1` (Portal Publish) |
| File Count monthly limit | **Grace until 2026-11-17**; check Usage Center / request higher limit |

**Not blocked:** local dry-run via `publishToMavenLocal` (Gradle, CI, and `install.sh`).

## Destination summary

| Destination | Intended? | Works today? |
|-------------|-----------|--------------|
| `mavenLocal` (`~/.m2`) | Yes — primary for SNAPSHOT / install / CI | **Yes** |
| Maven Central | Yes — public releases (`org.konaframework`) | **Plumbing yes; human tag + Portal Publish** |
| GitHub Packages | Optional / not specified in PLAN | **No** (not configured) |

## Related docs

- [HANDOFF.md](HANDOFF.md) — session resume; links here for publish commands
- [docs-site.md](docs-site.md) — Cloudflare Pages first publish without GitHub secrets
- [apps/docs … installation](../../apps/docs/src/content/docs/docs/guides/installation.mdx) — end-user install + mavenLocal note
- [PLAN.md](../architecture/PLAN.md) — groupId `org.konaframework`
- Archived sister repos (`kona-brew`, `kona-inertia`) — point consumers at this monorepo
