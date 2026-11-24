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

## [0.7.1] - 2026-11-24

### Added

- **Go to** in a song's kebab menu: a submenu that opens the song's **Artist** page, its
  **Album** page, or its **Folder** in Files. A song without an artist or album tag
  hides the matching destination instead of opening an empty page.
- **Delete** in every folder's kebab menu on Files. A confirmation shows how many songs
  disappear, and the songs' ratings, tags, playlist entries, pins and queue slots are
  dropped along with the files.

### Fixed

- Library scans skip hidden files and folders (names starting with a dot), so a
  Syncthing `.stversions` mirror no longer imports every song a second time.

## [0.7.0] - 2026-11-24

### Added

- Star toggles directly on album and artist archive cards and on their detail pages,
  so a collection can be pinned without opening its artwork editor. Starred items keep
  their collapsible section at the top of the archive.

### Changed

- The **Your library** summary moved from the top of Songs to the top of the Dashboard,
  above the Songs / Files / Albums / Artists browse tiles: song count, storage used,
  hours, **Rescan** and **Free up space**. Songs now opens straight into its sort and
  shuffle controls.
- Bulk selection now starts from **Bulk selection** in a song's kebab menu instead of a
  long-press. The entry is available on Songs, Search, Files, album and artist pages and
  on playlist rows, and the selected song is the first one in the selection.
- A song's kebab menu now has an **Edit** entry that opens a submenu instead of jumping
  to the editor: **Edit page**, **Change title**, **Set artist**, **Set album** and
  **Edit tags**. The artist and album dialogs autocomplete from the library like the
  editor's fields, and the tags dialog shows the song's tags as checked pills plus the
  frequently used library tags as one-tap pills.
- Album and artist names can wrap to two lines on the Albums and Artists pages and in
  the album headers of an artist page, so a long name is no longer cut after one line.

### Removed

- The **Recently added**, **Most liked** and **Most disliked** sections on the Dashboard,
  along with the bulk-selection bar that came with the disliked list. Ratings are
  unchanged and still set the weighted-shuffle score; the Liked / Disliked / No rating /
  Unheard smart playlists cover browsing by rating.

## [0.6.1] - 2026-11-22

### Changed

- The pull-to-search gesture now starts with a compact search cue and progress line. The
  Search page only appears after a preview threshold, and release-to-open requires a
  larger threshold. Gestures that scrolled the list to the top no longer turn into a
  search; only a fresh pull that begins at the top activates it.
- The Now Playing timeline now sits above the cover art in the vertical layout instead
  of being overlaid on the artwork.

## [0.6.0] - 2026-11-22

This release turns the library into a more complete browsing and editing workspace:
collections can be starred, every archive shares Songs' ordering tools, search has a
pull-down gesture, and the player and editors have been tightened around the artwork.

### Added

- **Star albums and artists** from their artwork editors. Starred collections are
  persisted and pinned in a new collapsible **Starred** section at the top of the Albums
  and Artists archives, above the alphabetical grid.
- The song editor now offers the **eight most-used tags across the library** that the
  current song does not already carry, as one-tap chips; adding one updates the tag list
  immediately and removes it from the suggestions.
- **Pull down to search**: at the top of any scrollable page, dragging down reveals the
  Search page in a finger-linked vertical reveal. Releasing past the threshold commits
  to Search; a short pull springs back. The top-bar magnifier uses the same animation.
- **Compress** in the bulk-selection bar of **Most disliked**: select one or more songs
  and open the existing batch compression sheet without leaving the Dashboard.
- **Folder actions menu**: every folder in Files now has a kebab menu with **Reorder
  contents**, **Move** and **Explode**. Move places the whole folder in another folder
  of the same library; Explode moves the folder's contents into its parent and deletes
  the folder when nothing is left, skipping names that already exist.
- **Albums, Artists and Files** now use the same four ordering options as Songs —
  date added, title, artist and duration — plus the Songs-style alphabetical rail for
  title/artist orders. Album/artist orders use their tracks' aggregates; Files sorts
  folders and the songs inside them.
- **Reorder tracks in albums and manual playlists** by dragging the handle beside the
  track number. Album pages remember their order across restarts; playlist order is
  stored in the playlist itself. Smart/dynamic playlists stay read-only.
- **Long-press bulk selection** on every song row — Songs, Search results, Files,
  album/artist pages, playlists, tags and smart lists. The shared selection bar can set
  the artist or album, set tags, add the selection to a playlist, compress it, or delete
  it. Compress and Delete confirm first; artist/album edits preserve every other tag,
  and the currently playing file is written without interrupting playback.
- **System theme** in the three-way **System / Light / Dark** selector. System follows
  the device's dark-mode setting and updates when the device switches.
- Focusing the **artist** or **album** field in the metadata editor now scrolls it to the
  top of the form, so its autocomplete suggestions are visible above the keyboard
  instead of being hidden below it.
- Tags in the metadata editor are now **saved by Add/Remove themselves**. The sticky
  Save button also commits any tag still sitting in the Add field, so typing a tag and
  saving works in one step without pressing Add first.

### Changed

- Pulling down at the top of the **Edit song** form now performs the same action as the
  top-left back arrow.
- The Now Playing **scrubber now sits directly on the lower edge of the cover art**, with
  a dark scrim keeping the white waveform and time labels readable. The like/dislike
  controls no longer have a panel background and sit directly on the page.
- The bottom navigation now mirrors completely with the **dominant hand**. Right-handed:
  **Dashboard, Playlists, Songs, Menu**. Left-handed: **Menu, Songs, Playlists,
  Dashboard**.
- Scanning progress no longer appears as a separate row in the top bar. The **Rescan**
  button in the Songs library summary now shows a spinner, pulses, and is disabled while
  a scan runs.

## [0.5.0] - 2026-11-22

### Added

- **Waveform seek bar** on the Now Playing screen: the progress control is now the
  song's loudness envelope, with the played side highlighted and a playhead. Tap or drag
  anywhere on it to seek. Long tracks are sampled in short windows and envelopes are
  cached in memory, so the control stays responsive.
- **Delete** and **Compress audio** shortcuts on the Now Playing screen for songs with a
  negative score. Delete opens the existing file-deletion confirmation; Compress opens
  the bitrate picker for the current song. Both disappear again at neutral or positive
  scores.

### Changed

- Metadata edits to the currently playing song are now **deferred**: the app shows the
  new values immediately, but rewrites the file (and applies an optional rename) only
  after the track is paused, replaced, or the app is reopened. This removes the audible
  gaps caused by rewriting a file while the player is reading it.
- The Dashboard now opens with a **Songs / Files / Albums / Artists** browse grid, and
  the **Your library** summary moved from the Dashboard to the top of the Songs page.
- The Now Playing rating panel now sits directly under the header, above the artwork,
  instead of below the transport. For disliked songs, Delete and Compress audio stay
  directly above it.
- **Reorder contents** now files a folder's songs at the root of the Music Library that
  contains it, even when the folder is nested. A staging folder such as `Downloads`
  merges into the library's existing artist/album tree instead of building a second
  tree inside itself.

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

[Unreleased]: https://github.com/mel1s4/music-player-app/compare/v0.7.1...HEAD
[0.7.1]: https://github.com/mel1s4/music-player-app/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/mel1s4/music-player-app/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/mel1s4/music-player-app/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/mel1s4/music-player-app/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/mel1s4/music-player-app/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/mel1s4/music-player-app/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/mel1s4/music-player-app/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/mel1s4/music-player-app/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/mel1s4/music-player-app/releases/tag/v0.1.0
