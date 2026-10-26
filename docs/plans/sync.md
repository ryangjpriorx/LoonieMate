# Sync between phones (Android)

The plan agreed on 2026-10-26. Keep the same budget on two phones through one encrypted file in Google Drive (or any
cloud app that shows up in Android's file picker, like Nextcloud). Work in small stages; each is committed, built and
tested on GitHub before the next. **To pick up again: find the first unticked stage below and start there.**

## Decisions

- **One Google account** (the same person on two phones, or both phones signed in to one account).
- **Android's file picker, no Google sign-in**: "Create a sync file" or "Use an existing sync file" picks a file
  through the system picker (Drive appears there); LoonieMate keeps permission to it. No Google Cloud project, no OAuth.
- **Android only for now.** The web version keeps export / import; the sync file is a normal encrypted backup, so the
  web version can import it.
- **Password remembered on the phone**, encrypted with a key in Android's Keystore, never sent anywhere.

## How it works

- The sync file is exactly an encrypted backup (`Backup.encrypt`), so it can also be imported by hand anywhere.
- **Three-way merge** (`engine/.../Sync.kt`): each phone keeps the data as it was after its last sync (the *base*).
  For every item: if only one side changed it since the base, that change wins; if both did, the newest change wins
  (and for a month's Paid / Done ticks, each tick is decided the same way on its own). Deletions carry over through
  tombstones as before. This is what lets an Undo on one phone stick on the other. The first sync (no base) uses the
  ordinary combine (`Backup.merge`).
- One sync: read the file → decrypt (the derived key is reused while the file's salt stays the same, so only the
  first sync is slow) → merge with this phone's data and the base → apply to this phone if anything changed → write
  the file if it differs → save the new base. A successful sync also counts as a backup.
- **When**: when the app opens (at most once a minute), 15 seconds after a change while the app is open, when it goes
  to the background, every 6 hours in the background with internet (WorkManager), and **Sync now**.
- If two phones write at the same moment, one write can replace the other in the file, but neither phone loses
  anything: each keeps its own full copy, and the next sync merges it back in.
- Device-only settings (look, reminders, accessibility) are not synced.

## Stages

- [x] **Stage 0**: this plan.
- [ ] **Stage 1**: `Sync.reconcile(local, remote, base)` in the engine with tests (two phones through one file: edits on
  both, newest wins, Undo of a tick sticks, deletions carry over, first sync with no base).
- [ ] **Stage 2**: `sync/SyncManager.kt`: file access with a persisted permission, password in the Keystore, the sync
  steps, key cache, errors (can't reach the file, wrong password, not a LoonieMate file), triggers and the background job.
- [ ] **Stage 3**: Settings card "Sync between phones" (create / use existing / password / status / Sync now / Stop
  syncing); "Synced 5 min ago" under the title on Today; texts that said "later with Google Drive sync". Then release.
