# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes |
| < 0.1 | No |

This is an early project; security fixes are released on `main` and tagged as patch
releases when practical.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through GitHub's private vulnerability reporting:

<https://github.com/mel1s4/music-player-app/security/advisories/new>

If that is unavailable, contact the maintainer ([@mel1s4](https://github.com/mel1s4))
through GitHub and ask for a private channel before sharing details.

Please include:

- A description of the issue and the impact you believe it has.
- The app version (`0.1`, or the commit you built), Android version and device/emulator.
- Steps to reproduce, ideally with a minimal file or proof of concept.
- Any relevant logs, stack traces or screenshots.

Do not include other people's personal data, copyrighted media you cannot share, or
credentials.

## What is in scope

- The Storage Access Framework document handling and persisted tree grants.
- Audio tag parsing and writing (jaudiotagger is a third-party dependency, but the way
  the app feeds it is in scope).
- Media3 playback, the exported `PlaybackService`, and the media session.
- The local SQLite database and migrations.
- File operations: copying, renaming, tag write-back and compression output.
- Anything that could leak data off the device or bypass the folder grants.

## Out of scope

- Issues that require root, an unlocked bootloader, a malicious OS image, or physical
  access to an unlocked device.
- Bugs in Android itself, the device vendor's media stack, or third-party libraries that
  the app cannot reasonably work around. Report those upstream as well.
- Denial of service caused by intentionally enormous playlists or files when no
  realistic attack path exists beyond filling the user's own storage.

## Design notes relevant to security

- The app does **not** request `INTERNET`, so it cannot upload library data.
- `android:allowBackup="false"` keeps the database and preferences out of cloud backups.
- File access is limited to tree URIs the user explicitly grants; no broad storage
  permission is requested.
- Exported components are limited to the launcher aliases, `MainActivity` and the
  Media3 `PlaybackService`. Please report anything that looks over-exported.
- Downloaded or shared audio files are untrusted input: parsing errors should fail
  closed (skip the file) rather than crash or corrupt other tracks.

## Disclosure

We will try to acknowledge a report within 7 days. Once a fix is available, we will
coordinate a disclosure date with you and credit you in the release notes unless you ask
to remain anonymous. There is no bug bounty.
