# RESUME

## Status (2026-11-27)
- Role: the user reviews and merges PRs; Claude implements every code ticket test-first (red commit, then green commit). `ready-for-human` now means only steps Claude cannot do (accounts, secrets, phone taps). CLAUDE.md update is in PR #23 (to `main`).
- Integration branch `integration/v1` → draft PR #21 to `main`.
- Phone acceptance passed: #12 (comment posted) and #13 (`SleepSync/sleep-2026-11.csv` in Drive, readable through the Drive connector; #13 comment posted).
- Health Connect "rate limit" root cause: on the last page `pageToken` is `""`, not null, and `""` restarts at page 1, so the loop never ended. Fix: PR #26 (`fix/hc-paging`).
- Health Sync copies every Samsung Health session back into Health Connect, so each night appears twice (two source apps, same start). #14 dedup by start instant handles it.

## Open PRs into `integration/v1` (merge in this order)
| PR | Ticket | State |
|---|---|---|
| #22 | #14 dedup | CI green |
| #24 | #16 recover from missing permission / sign-in | CI green (fixed recursive type inference, 9d1d2ad) |
| #26 | Health Connect paging fix | CI green |
| #25 | #18 sync status | CI green |

## Next
1. All four PRs are CI green (2026-11-27). The user reviews and merges #22 → #24 → #26 → #25.
2. While merging (Claude): resolve conflicts; `UpToDate` must refresh `lastSyncAt`; `SyncResult.message()` must cover every result; map the Health Connect rate-limit error to a friendly result.
3. Done 2026-11-27: dedup + paging fix verified on the phone (branch `test/phone-dedup`, now installed; delete after merge). Drive November file is clean: 15 rows. After merging, re-check on the phone with #16/#18 included.
4. Done: #13 acceptance comment posted.
5. Clean up: branch `exp/hc-alpha11`, worktree `D:\Learning\sleepsync-exp`, agent worktrees under `.claude/worktrees/`.
6. Remaining tickets: #15 (backfill), then #17.
7. Human-only setup: release signing keystore + 4 GitHub Secrets; register the release-key SHA-1 as a second Android OAuth client. Obtainium is deferred.
8. Merge PRs #20 and #23 into `main`; PR #21 goes last.

## Phone test recipe (CI uploads no debug APK)
1. `gh workflow run release.yml --ref <branch>` (dry run) → artifact `dryrun-unsigned-apk`.
2. `zipalign -f -p 4`, then `apksigner sign` with `~/.android/debug.keystore` (alias `androiddebugkey`, password `android`; its SHA-1 is registered as OAuth client "SleepSync debug" in GCP). In bash set `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
3. `adb install -r --user 0 <apk>` (user 150 is Secure Folder). adb path: `C:\Users\yiyi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
4. Read the screen with `uiautomator dump`; don't relaunch with `monkey` (it loses the status).

## Possible new tickets
- CI uploads a debug APK.
- Account picker (if multi-account sign-in problems come back).

## Key decisions
See CLAUDE.md, "Decisions already made" and "Workflow".
