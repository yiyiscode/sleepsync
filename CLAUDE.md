# Development Process — SleepSync

## Language
Communicate in English in this project (replies, docs, code comments, commit messages). This overrides the global Traditional Chinese default.

## Purpose
This is a learning project: the goal is to practice the full software lifecycle (plan → design → implement → test → deploy → maintain) on one real app. When helping, name which lifecycle phase the current work belongs to and keep each phase's artifact (requirements, ADR, tests, release notes, retro) instead of skipping straight to code.

## The app
SleepSync: a personal Android app that reads sleep sessions from Health Connect and uploads them to Google Drive, so Claude can read them via the Drive connector.

Decisions already made (record changes as ADRs, don't silently revisit):
- Kotlin + Jetpack Compose. Personal APK only, no Play Store (avoids the health-data policy review).
- Google Sign-In + Drive API with the `drive.file` scope only.
- Output: one CSV per month, `sleep-YYYY-MM.csv`, one row per night. Dedup key = sleep session start time, so re-syncing never duplicates rows.
- v1: manual "Sync" button. v2: daily automatic sync with WorkManager.
- Keep read (Health Connect) → transform (CSV + dedup) → upload (Drive) as separate layers. Tests attach at one seam: the Sync entry point, with fake sleep source and fake sleep store. Spec: `docs/specs/sleepsync-v1.md`.
- CI: GitHub Actions builds and signs the APK; every release gets a version tag.
- Obsidian is the engineering notebook (requirements, ADRs, retros), not part of the app.

## Workflow
- Repo: public GitHub `yiyiscode/sleepsync`, default branch `main`. Tickets are GitHub Issues, linked to the spec issue; dependencies are written as "Blocked by #n" in the body.
- Labels: `ready-for-agent` (agents implement, the user reviews the PR) and `ready-for-human` (the user writes it; Claude coaches instead of writing the code). The Sync core is `ready-for-human`: it is where the learning happens.
- Every change lands through a PR that closes its issue. CI must be green before merge.

## Open questions
- Health Connect history may be limited (~30 days) on some devices: verify, and backfill missed days in v1.
