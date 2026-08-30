---
name: release
description: This skill should be used when the user asks to "release mugen", "cut a release", "prepare a release", "publish a new version", "run ./release", or wants to update the changelog from recent commits, tag the current version, and push the tag so CI builds and publishes the signed GitHub Release. Android/Gradle project — no Play Store, no appbundle build here (CI owns the build).
---

# Mugen Release Helper: Changelog, Tag, Push

To turn the work already on `develop` into a published release: update `CHANGELOG.md`
from the commits since the last documented version, create an annotated `v<versionName>`
tag whose message is that new changelog section, and push it so
`.github/workflows/release.yml` builds and publishes the signed GitHub Release.

**This skill does not build the app and does not bump the version.** `versionName` /
`versionCode` in `app/build.gradle.kts` are bumped separately (manually, or by the debug-build
rule in `AGENTS.md`). This skill releases whatever `versionName` currently says.

Why the tag format matters: the in-app updater reads
`GET /repos/h80r/mugen/releases/latest` and the post-update "O que há de novo" sheet reads
`GET /repos/h80r/mugen/releases/tags/v<versionName>` (see `ReleaseServiceImpl`). The tag
**must** be exactly `v<versionName>` and the Release **must not** be a prerelease, or those
screens stay empty.

## Command Logic

### 1. Preconditions

1. Confirm the working directory is the mugen repo (`app/build.gradle.kts` with
   `namespace = "dev.h80r.mugen"` exists).
2. Confirm the current branch is `develop`:
   `git rev-parse --abbrev-ref HEAD`. If not, warn and ask the user to confirm before
   continuing.
3. Confirm a clean working tree: `git status --porcelain` must be empty. If dirty, stop and
   tell the user to commit or stash first — the changelog commit must be isolated.
4. `git fetch --tags --prune`.

### 2. Resolve the version

1. Read `versionName`:
   `grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts` → `<version>` (e.g. `0.70.2`).
2. `<tag>` = `v<version>` (e.g. `v0.70.2`).
3. If `git rev-parse -q --verify "refs/tags/<tag>"` succeeds, the tag already exists — stop
   and tell the user to bump `versionName` in `app/build.gradle.kts` first (and `versionCode`).

### 3. Determine the changelog range

1. Read `CHANGELOG.md`. The most recent documented version is the first line matching
   `^## \[(.+?)\]` — capture it as `<last_doc>` (e.g. `v0.60`, or a collapsed header like
   `v0.30 - v0.35`; take the last/highest version token in the header).
2. Resolve `<prev_tag>`: try `git rev-parse -q --verify` on both `<last_doc>` and, if it has
   no leading `v`, `v<last_doc>` (and vice-versa) — the tag history contains both styles
   (`0.29`, `v0.60`). Use whichever resolves.
   - If none resolve (e.g. the collapsed header), fall back to the newest tag that does:
     `git for-each-ref --sort=-creatordate --format='%(refname:short)' refs/tags | head -1`.
3. Commit range: `git log --no-merges --format='%h%x09%s' <prev_tag>..HEAD`.
   The history is squash-merge, so each commit is one feature branch — subjects are already
   feature-scoped (e.g. `📦🗜️ Fix manga double-page reader bugs`).

### 4. Draft the new changelog section

1. Header: `## [<tag>] - <YYYY-MM-DD>` using today's date (UTC).
2. Bucket each commit into the file's existing sections, in this order, omitting empty ones:
   `### Adicionado`, `### Alterado`, `### Melhorado`, `### Removido`, `### Corrigido`,
   `### Outros`.
   - Strip leading gitmoji/emoji and branch-name parentheticals from subjects.
   - Drop pure Specter/CI bookkeeping commits unless they carry user-visible impact:
     `✅ Verify…`, `✅ Audit…`, `📝 Merge … spec delta`, `📦 Archive …`, `✨ Propose …`,
     `✨ Add … Specter change`, `📝 Generate baseline … specs`, spotless/lint-only commits.
3. Rewrite each kept subject as a readable, user-facing line in **Brazilian Portuguese**,
   past-participle style ("Adicionado modo …", "Corrigido crash …"), matching the tone of the
   existing backfilled sections. No PR numbers, no author handles (solo fork).
4. Show the drafted section to the user as a fenced block and **wait for explicit approval**
   before writing anything. Incorporate edits the user asks for.

### 5. Write and commit the changelog

1. Insert the approved section immediately below the `## Unreleased` line (leaving
   `## Unreleased` present and empty), above the previous top section.
2. Sanity-check with the shared extractor:
   `python3 tools/ci/extract-changelog-section.py "<tag>" CHANGELOG.md` must print the new
   section and exit 0 (this is exactly what `release.yml` runs to build the Release body).
3. Stage only that file: `git add CHANGELOG.md`.
4. Commit: `git commit -m "📝 docs: Update changelog for <tag>"`.

### 6. Tag and push

1. Write the new section body (without the `## [...]` header line) to a temp file.
2. Annotated tag: `git tag -a "<tag>" -F <tempfile>`.
3. Push the commit, then the tag:
   `git push origin develop` then `git push origin "<tag>"`.
   (If the user is not on `develop`, push the current branch instead and note it.)
4. The tag push triggers `.github/workflows/release.yml`.

### 7. Report

Print:
- `✅ Changelog atualizado e commitado para <tag>.`
- `✅ Tag <tag> criada e enviada.`
- The drafted changelog section, in a fenced block.
- `▶ CI: https://github.com/h80r/mugen/actions/workflows/release.yml`
- `▶ Release: https://github.com/h80r/mugen/releases/tag/<tag>` (available once CI finishes).
- Reminder: the release must publish as a non-prerelease so `releases/latest` (in-app
  updater) and `releases/tags/<tag>` (post-update changelog sheet) resolve.

## Notes

- No Gradle invocation here. If the user wants a local build too, that's a separate step.
- `release.yml` re-checks that the tag matches `versionName` and will fail loudly on a
  mismatch — this skill's step 2/3 checks exist to catch it before the push.
- The section boundaries this skill writes must stay compatible with
  `tools/ci/extract-changelog-section.py` (header `## [<version>] - <date>`, body runs to the
  next `## [`).
