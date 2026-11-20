# Privacy Policy

**Effective date:** 2026-11-20

Music Player is a local-first Android app. It does not collect, transmit, sell or share
personal data. There is no account, no analytics, no advertising SDK and no telemetry.
The app does not request the `INTERNET` permission, so it has no technical ability to
send your library anywhere.

## What the app accesses

| Data | Why | Where it goes |
|---|---|---|
| Audio files in folders you pick | Scan, browse, play, tag and compress your music | Stays on your device |
| Folder grants chosen through the Storage Access Framework | Persist access across launches | Stored by Android; the app keeps the tree URIs in its preferences |
| Ratings, favourites, tags, playlists, first-seen timestamps, starred folders | Remember what you do in the app | `library.db` inside the app's private storage |
| Track metadata cache | Make the next launch fast | `library.db` |
| Cover art | Show album/artist artwork | Read from your folders or the app's private cache |
| Playback state / current track | Notification, lock screen and media session controls | Held by the foreground playback service |

If you enable **“Update audio files”** in Settings, ratings, favourites and app tags are
written into the audio files themselves. This is a local file write and is off if you
turn the setting off. Metadata edits and compression are local file operations too.

## Permissions

- **Storage Access Framework** — the app can only read/write folders you explicitly pick.
  It does not request `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` or
  `MANAGE_EXTERNAL_STORAGE`.
- **Foreground service / media playback** — keeps audio running when the app is in the
  background and shows the media notification.
- **Post notifications** — used for the playback notification on Android 13+.
- **Wake lock** — prevents the CPU from sleeping during playback.

There is no location, camera, microphone, contacts or internet permission.

## Retention and deletion

Everything lives in the app's private storage. Uninstalling the app removes its database,
preferences and caches. Files you edited, renamed or compressed remain in your music
folders; the app cannot delete them for you outside the actions you explicitly take in
the UI.

`android:allowBackup="false"` is set, so the database and preferences are not copied to
Android cloud backups.

## Children

The app has no accounts, ads or data collection and is not directed at children.

## Changes

If this policy changes, the change will be committed to this repository and the effective
date above will be updated. For questions, open a GitHub issue at
<https://github.com/mel1s4/music-player-app/issues>.

## Contact

Maintainer: [@mel1s4](https://github.com/mel1s4)
