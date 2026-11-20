# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned

- Unit tests around scanning, tag read/write and rating weights.
- More tablet and foldable layouts.
- Additional library statistics and listening history.

## [0.1.0] - 2026-11-20

The first public release.

### Added

- Local library support through Android's Storage Access Framework, with multiple
  user-granted folders and no broad storage permission.
- Background playback with AndroidX Media3: notification, lock-screen controls, audio
  focus and headphone-unplug handling.
- Dashboard with library counts, recent additions, most-liked/disliked lists and
  continue-listening card.
- Songs, Albums, Artists, Files, Search and Playlists sections.
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

[Unreleased]: https://github.com/mel1s4/music-player-app/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mel1s4/music-player-app/releases/tag/v0.1.0
