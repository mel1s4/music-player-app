# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned

- Unit tests around scanning, tag read/write and rating weights.
- More tablet and foldable layouts.
- More library statistics, and a listening-history screen built on the recorded play
  timestamps.

## [0.4.0] - 2026-11-21

### Added

- **No rating** smart list: tracks that are still at the neutral score.
- **Reorder contents** on a folder in the Files browser: long-press the folder and its
  songs are moved into `artist/album/` folders from the metadata the scanner already
  has, or `artist/` when there is no album. Songs without an artist are skipped, songs
  already in the right folder are left alone, and ratings, tags, favourites, listening
  history and playlist entries follow the moved files.

## [0.3.0] - 2026-11-21

### Added

- **Smart lists** on the Playlists screen: **Liked Songs** and **Disliked Songs**
  computed from the signed ratings, and **Unheard songs** for tracks that have not been
  played yet. A track counts as heard after ten seconds of real playback (or half of a
  shorter track), so skipping through a list does not mark everything as listened.

### Fixed

- Tapping a song in Songs, Search or a Dashboard list now queues that list in the order
  it is shown, so playback continues with the next song instead of stopping after the
  selected one or following a different library order.

## [0.2.0] - 2026-11-20

### Added

- Delete-file action in every song's overflow menu, with a confirmation dialog and
  cleanup of ratings, tags, favourites, playlist entries and the play queue.
- Artist and album autocomplete on the edit-song form: matches appear as you type in a
  scrollable panel, so several existing values are one tap away.
- **Hear the difference** on the edit-song form: convert a 20-second excerpt or the full
  song at several bitrates, play the versions back to back, and keep the one that sounds
  right. The chosen version is saved as the full song.
- Swipe up on the Now Playing header or artwork to open the song editor.
- A **Playback notifications** entry in Settings opens the system notification page.

### Changed

- On the edit-song form, artist and title now come before the file name, and **Save** is
  a sticky bar that stays directly above the keyboard while the form scrolls.
- Swipe down on the Now Playing header or artwork to hide the player (the header already
  supported it; the artwork now does too).

### Removed

- The "Continue listening" card from the Dashboard; the mini player already carries the
  current track and opens the full player.

### Fixed

- Artist suggestions on the edit-song form open while typing and scroll inside the
  form; the keyboard inset no longer hides the list.
- Playback notifications and lock-screen controls now appear. Android 13+ requires the
  runtime notification permission, and the MediaSession had no track metadata or launch
  intent; the app now asks once, shows title/artist/album on the lock screen and in the
  shade, and opens the app when tapped.

## [0.1.0] - 2026-11-20

The first public release.

### Added

- Local library support through Android's Storage Access Framework, with multiple
  user-granted folders and no broad storage permission.
- Background playback with AndroidX Media3: notification, lock-screen controls, audio
  focus and headphone-unplug handling.
- Dashboard with library counts, recent additions, most-liked/disliked lists,
  continue-listening card, and one-tap **Rescan** / **Free up space** actions.
- Songs, Albums, Artists, Files, Search and Playlists sections.
- Songs default to **date added** (newest first), with title, artist and duration also
  available from the sort menu.
- Folder breadcrumbs, per-folder counts and **starred folders** pinned at the Files root.
- Signed ratings, favourites, free-form tags, manual playlists and tag-driven dynamic
  playlists.
- Metadata editing, file renaming and artwork management.
- Native tag reading and writing for MP3/ID3v2, Vorbis comments and MP4 atoms, with
  optional rating/favourite/tag mirroring into the audio files.
- AAC compression and a free-up-space flow that can replace originals or keep both.
- Fast launch from the cached scan, with background refresh and a non-blocking
  scanning indicator.
- Cyberpunk-green Material 3 theme with light/dark modes, five launcher icons and
  English, Spanish, French and Portuguese resources.
- Swipe-to-change-song on the Now Playing artwork.

### Fixed

- Playback no longer stops silently when the playing file is rewritten by a tag edit,
  rating sync or rename; the player reopens the current media item at the same position.
- Deleted files are pruned from the metadata cache instead of lingering in the launch
  snapshot.
- A failed scan no longer leaves the list stuck in a permanent scanning state.

[Unreleased]: https://github.com/mel1s4/music-player-app/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/mel1s4/music-player-app/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/mel1s4/music-player-app/releases/tag/v0.1.0
