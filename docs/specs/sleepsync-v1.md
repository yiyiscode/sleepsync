# Spec: SleepSync v1: manual sleep sync to Google Drive

Status: ready-for-agent. Tracked as GitHub issue #1; tickets are its sub-issues.
Date: 2026-11-25

## Problem Statement

I sleep badly and want an AI to help me find patterns, such as whether late bedtimes cut my deep sleep. My phone records each night in Health Connect, and an exporter app already drops it into Google Drive, which Claude can read through its connector. But that export is unusable for analysis: it holds raw per-stage segments rather than one row per night, every segment appears twice, and it is split across one file per day plus overlapping 30-day range files with no time zone. Claude has to reassemble and deduplicate everything before it can answer a simple question about my sleep.

## Solution

SleepSync is a personal Android app with one main action: **Sync**. When I tap it, the app reads every sleep session from Health Connect that is not yet in Drive and adds them to one CSV file per month (`sleep-YYYY-MM.csv`) in my Google Drive. Tapping Sync again never duplicates a night. Nights missed while the phone was off are picked up on the next sync. Claude then reads the CSVs through the Google Drive connector and analyzes weeks or months at once.

## User Stories

### First run and permissions
1. As a first-time user, I want the app to explain in one screen what it reads and where it writes, so that I know what I'm granting before any permission prompt.
2. As a first-time user, I want to grant read access to sleep data only, so that the app cannot see my other health records.
3. As a user who denied the Health Connect permission, I want a clear message and a button that reopens the permission request, so that I can recover without reinstalling.
4. As a user whose phone lacks Health Connect, or has an outdated version, I want the app to tell me and link to the update, so that I'm not stuck on a blank screen.
5. As a first-time user, I want to sign in with my Google account, so that the app can write to my Drive.
6. As a privacy-conscious user, I want the app to request access only to files it creates in Drive, so that it can never read or change my other Drive files.
7. As a user who cancels Google sign-in, I want the app to stay usable and offer sign-in again, so that a misclick does not break setup.

### Syncing
8. As a user, I want one Sync button on the main screen, so that syncing takes a single tap.
9. As a user, I want the sync to upload every sleep session newer than the newest one already in Drive, so that nights missed while my phone was off are filled in.
10. As a user, I want my first-ever sync to upload all the sleep history Health Connect still holds, so that Claude starts with as much data as possible.
11. As a user, I want tapping Sync twice in a row to change nothing the second time, so that I never get duplicate rows.
12. As a user, I want each night stored in the month file of the night's start time, so that a night starting 31 Oct at 23:40 lands in October and I can predict where to find it.
13. As a user, I want a sync that covers several months to update each month file, so that backfills across a month boundary work.
14. As a user, I want to see the result after each sync ("Added 2 nights", "Already up to date"), so that I know it worked.
15. As a user, I want the Sync button disabled while a sync runs, so that I cannot start two at once.
16. As a user, I want to see when the last successful sync happened and the newest night it contains, so that I can tell at a glance whether Drive is current.

### Data written
17. As Claude reading the files, I want one row per sleep session with fixed, documented columns, so that I can parse every month the same way.
18. As Claude, I want start and end times in ISO 8601 with the UTC offset, so that time zones and daylight-saving changes stay unambiguous.
19. As Claude, I want minutes asleep and minutes per stage (light, deep, REM, awake), so that I can analyze sleep quality, not just duration.
20. As Claude, I want stage columns left empty, not zero, when the tracker didn't record stages, so that "no data" never reads as "no deep sleep".
21. As Claude, I want a column naming the app that recorded the session, so that I can account for differences when the user switches trackers.
22. As Claude, I want rows sorted by start time, so that trends read top to bottom.
23. As a user, I want the CSV to open cleanly in Google Sheets, so that I can check the data myself.

### Failures and safety
24. As a user, I want a sync started while offline to fail with "No connection, nothing was changed", so that I know to retry later.
25. As a user whose Google sign-in expired, I want to be asked to sign in again, after which the sync can run, so that an expired session does not look like a broken app.
26. As a user whose Health Connect permission was revoked in system settings, I want the app to detect it at sync time and ask again, so that the failure explains itself.
27. As a user, I want a failure midway through a multi-month sync to keep the months already written and report which ones failed, so that a retry finishes the job without duplicates.
28. As a user, I want the app to never delete or rewrite existing rows, so that my history in Drive cannot be lost by a bug.
29. As a user who hand-edited a month file into an unreadable state, I want the sync to stop for that month with a clear error rather than overwrite it, so that my edits are not destroyed.
30. As a user, I want the app to keep working if I rename or move the SleepSync folder in Drive, or to tell me clearly what happened, so that tidying my Drive does not silently break syncing.
31. As a user, I want the app to keep no copy of my sleep data beyond what is needed for one sync, so that the phone holds no extra sensitive data.

### Release and maintenance
32. As the developer, I want each release built and signed by CI from a version tag, so that any release can be reproduced without my laptop.
33. As the developer, I want the app to show its version number, so that bug notes in Obsidian can name the exact build.
34. As the developer, I want sync failures to give a short reason I can copy, so that I can file an issue with evidence.

## Implementation Decisions

- **Glossary.** A *sleep session* is one Health Connect sleep record. A *night* is a sleep session as stored in the CSV; v1 treats them one-to-one. The *sleep source* is where sessions are read from. The *sleep store* is where month files are read and written. *Sync* is the single operation that moves new sessions from source to store.
- **One deep module: Sync.** It is the only entry point the UI calls. Given a sleep source and a sleep store, it:
  1. finds the newest night already in the store
  2. reads every newer session from the source, or all available history if the store is empty
  3. groups them by the month of their local start time
  4. merges each group into that month's file
  5. returns a result
- **Two interfaces (ports) at Sync's edges:**
  - *Sleep source*: "give me sleep sessions starting after time T". The production adapter wraps Health Connect.
  - *Sleep store*: "find the newest stored night", "read month M", "write month M". The production adapter wraps the Drive API and stores files as `sleep-YYYY-MM.csv` in one app-created folder.
- **Sync result** is a closed set of outcomes the UI maps to messages:
  - *Synced* (nights added, months touched)
  - *Up to date*
  - *Needs Health Connect permission*
  - *Needs sign-in*
  - *Offline*
  - *Partially synced* (which months failed and why)
  - *Month file unreadable* (which month)
- **Deduplication key** is the session start instant. A night whose start already exists in the month file is skipped, and so is a repeat of the same start within one batch (real Health Connect data returned every segment twice). Existing rows are never modified or removed: merging only appends, then the file is re-sorted by start.
- **Month assignment** uses the session's start time in the time zone recorded with the session, falling back to the device zone.
- **CSV contract** (one header row; columns in this order):
  - `start`, `end`: ISO 8601 with offset
  - `in_bed_min`
  - `asleep_min`
  - `light_min`, `deep_min`, `rem_min`, `awake_min`: empty when stages are absent
  - `source_app`
- **Failure isolation.** Each month file is written independently. A failure on one month does not roll back the others, which is safe because the next sync dedups.
- **Unparseable month file.** If a month file exists but cannot be parsed against the contract, Sync stops for that month and reports it. It never overwrites the file.
- **Permissions.**
  - Health Connect: read sleep only.
  - Google: the Drive scope limited to app-created files (ADR-001), so no Google verification is needed for personal use.
- **Trigger.** v1 syncs only when the user taps Sync. The module takes no dependency on a scheduler, so v2 can call the same entry point from WorkManager unchanged.
- **Stack.**
  - Kotlin and Jetpack Compose.
  - Personal APK only; no Play Store.
  - GitHub Actions builds, tests and signs releases from version tags.

## Testing Decisions

- **One seam: Sync.** Tests call the Sync entry point with an in-memory fake sleep source and an in-memory fake sleep store, then assert on two things: the resulting month files and the returned result. This seam replaces the earlier plan to unit-test only the pure transform; the transform is exercised through Sync.
- **What makes a good test here:** it states a user-visible behavior (an input history plus existing Drive files, then the expected files plus the expected result) and never inspects internal helpers or call order. A refactor that keeps behavior must keep every test green.
- **Behaviors to cover at the seam:**
  - first sync uploads full history
  - second sync adds nothing
  - nights missed during a gap are backfilled
  - a night crossing midnight on the last day of a month goes to the start month
  - a sync spanning two months writes both files
  - sessions without stages produce empty stage cells
  - existing rows are preserved byte-for-byte
  - an unreadable month file is left untouched and reported
  - the store failing on one month gives a partial result, and a retry completes with no duplicates
  - offline, sign-in-expired and permission-revoked map to their results
- **Fakes** are part of the test suite, not mocks of each call. The fake store holds month files as strings, so tests read like real CSVs.
- **Adapters** (Health Connect, Drive) are not unit-tested. Each gets one documented manual check on the real phone per release.
- **Prior art:** none yet; this is a new codebase. These Sync tests become the pattern later features copy.

## Out of Scope

- Automatic or scheduled sync (planned for v2 via WorkManager)
- Any data other than sleep (steps, heart rate and so on)
- A cloud backend, multiple users, or sharing
- Play Store release
- Charts or analysis inside the app (Claude does the analysis)
- Editing or deleting nights, and syncing deletions made in Health Connect
- Merging naps or split sessions into one "night"
- iOS

## Further Notes

- **History (checked 2026-11-25, issue #2):** an existing 30-day export held only ~6 days of sleep data, so the first backfill will be small. Whether Health Connect caps history at ~30 days is still unconfirmed and no longer matters for v1.
- **Naps:** several sessions per day are common in real data. Decision: one row per session; Claude can filter by duration. Revisit after real use.
- **Data safety:** CLAUDE.md lists the decisions this spec relies on. Record any change to the CSV contract as an ADR, since Claude's analyses depend on it.
