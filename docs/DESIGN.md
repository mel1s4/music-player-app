# Design notes — Music Player

[Back to README](../README.md)

## Cyberpunk Green UI redesign

A full visual and interaction redesign of the local music player (`dev.melisa.musicplayer`),
Jetpack Compose, Material 3. Kept as one shell around the existing playback, scanning,
tagging and playlist logic, plus a local database and best-effort file mirroring for the
data the user creates inside it.

![Overview](screenshots/00-overview.png)

---

## 1. The concept: "Grid runner"

The old UI was a clean but generic blue-and-white Material app. The new one is a
terminal you carry: layered green-black panels, mono readouts, and one electric green
that only ever means **"this is live"** or **"this is the action."**

Three rules keep it from turning into neon soup:

1. **Green is a state, not decoration.** It marks the playing track, the selected tab,
   the primary button, the live dot. Everything structural is desaturated green-grey,
   so the accent still reads at a glance.
2. **Mono is the machine voice; sans is the human voice.** Kickers, counters, timecodes
   and section labels are monospaced and wide-tracked. Titles, artists and body copy
   stay in the system sans face.
3. **Motion is physical.** Anything a finger touches squashes and springs. Anything the
   eye follows fades and slides in under 400 ms. Nothing loops on screen unless audio
   is actually moving.
4. **Chrome stays quiet.** Separation comes from surface tone and space, not from
   outlines: no 1dp neon frame around cards, no accent tick before every heading, no
   decorative glow behind panels. The only glow left is the transport button, because
   it marks the primary action.

---

## 2. Palette

### Dark — the default ("Night City")

| Role | Hex | Used for |
|---|---|---|
| Background / surface | `#060B09` | app canvas |
| Surface container | `#0D1915` | cards, panels |
| Surface container high | `#12231C` | mini player, raised cards |
| Primary (neon green) | `#45F5A0` | live state, primary action, focus |
| On primary | `#00210F` | text on green |
| Secondary (acid lime) | `#C9FF57` | ranks, playlist icons, song sections |
| Tertiary (cyan) | `#5CE1FF` | albums, artist sections |
| Error (magenta) | `#FF6B8B` | disliked, destructive, delete |
| On surface | `#E6FFF2` | primary text |
| On surface variant | `#A6C4B4` | secondary text |
| Outline variant | `#22382E` | hairlines |

Measured contrasts (WCAG 2.1):

| Pair | Ratio |
|---|---|
| Text `#E6FFF2` on `#060B09` | **18.8 : 1** (AAA) |
| Secondary text `#A6C4B4` on `#060B09` | **10.6 : 1** (AAA) |
| Green `#45F5A0` on `#060B09` | **14.0 : 1** (AAA) |
| `#00210F` on green | **12.1 : 1** (AAA) |
| Magenta `#FF6B8B` on `#060B09` | **7.3 : 1** (AAA) |

### Light — "Daylight"

The theme switch still works, because a neon panel becomes a mirror in a bright kitchen.
It is the same system in daylight: deep green ink on mint paper, no glow effects.

| Pair | Ratio |
|---|---|
| Text `#07130D` on `#F1FBF5` | **17.9 : 1** |
| Secondary `#3B5346` on `#F1FBF5` | **7.9 : 1** |
| Green `#007A4C` on `#F1FBF5` | **5.1 : 1** |
| White on `#007A4C` | **5.4 : 1** |
| Magenta `#B3264A` on `#F1FBF5` | **6.0 : 1** |

---

## 3. Typography — sized for older eyes

Material's baseline (12sp captions, 14sp body) is the single most common complaint from
users over 50. Every size moved up one step, and the *smallest* text on screen is a
13sp monospaced label.

| Style | Size / line | Usage |
|---|---|---|
| Display small | 38 / 44 | the big library count |
| Headline small | 26 / 32 | screen title, Now Playing title |
| Title large | 23 / 30 | empty-state titles, settings labels |
| Body large | **17 / 26** | song titles, list content |
| Body medium | **16 / 24** | supporting copy |
| Body small | **14.5 / 21** | artist / album subtitles |
| Label large | 15 / 20 | buttons, nav labels |
| Label medium | 14 / 19, mono, +0.1 tracking | section labels, readouts |
| Label small | 13.5 / 18, mono, +0.2 tracking | counters, timecodes |

Line heights are generous (≈1.5) so two-line titles never collide.

---

## 4. Layout, shape, touch

- 8dp spacing base; 16–20dp screen gutters; 18–24dp between sections.
- Radii: 12 / 16 / 20 / 26dp. Panels use 20dp, artwork 16dp, chips 16dp.
- **Every interactive row is at least 68–78dp tall**; icon buttons are 44–52dp; the
  play/pause target is 76dp. Tags and filter chips are 44–48dp.
- Slider uses a custom **22dp disc thumb** with a soft glow instead of Material's 4dp
  hairline — the one control people drag with a thumb has to be hittable.
- Long lists keep a left rail + equalizer + colour + text to mark the current track, so
  state is never communicated by colour alone.

---

## 5. Motion inventory

| Interaction | Motion | Timing |
|---|---|---|
| Tab switch | 1/10-width slide + cross-fade | 260 ms in, 150 ms out |
| Album / artist / editor page | slide-over + fade, animated in *and* out | 260 / 150 ms |
| Drawer | slide from the holding hand's side + scrim fade | 260 / 150 ms |
| Now Playing | rises from the bottom, drag-down to dismiss | 400 ms spring |
| Mini player | expands up from the nav bar when a track starts | 260 ms |
| Artwork swipe | cover tracks the finger and rotates; direction pill slides out | follow + spring back |
| Play / pause | glyph morphs (scale + fade), button glows when playing | spring ≈ 300 ms |
| Buttons / rows / tabs | squash to 94–98% on press | spring 1200 stiffness |
| Nav tab | icon scales 1.1×, tint + pill animate, label follows | 150–260 ms |
| Track art | equalizer bars dance only while playing | 360–720 ms loop |
| Live dot | slow breathing alpha | 1.2 s loop |
| Scanning | green sweep across a rail | 900 ms loop |
| Score meters | fill animates from 0 on first draw | 400 ms |
| List reorder | LazyColumn placement animation | framework default |
| Cards / headers | staggered fade-and-rise on first display | 40 ms steps |

Reduced-motion note: all loops are tied to state (`playing`, `scanning`) and stop when
that state is false; no decorative animation runs forever on a static screen.

---

## 6. UX improvements

**Navigation**
- Album and artist pages open **inside the tab area** — under the top bar and above the
  mini player and bottom nav — so browsing a collection never hides playback controls or
  navigation. Only the full-screen editors (metadata, cover art) cover the chrome.
- Back from such a page returns to the index it was opened from, with the tabs intact.

**Player**
- Persistent mini player with artwork, title, live equalizer, progress LED bar, play and
  next — controls mirror to the holding hand (`Left` / `Right` in Settings).
- Swipe the mini player up, or drag the Now Playing header down, to open / close.
- Swipe the Now Playing artwork to change song: left is next, right is previous. The
  cover tracks the finger (slight rotation and shrink) and the direction pill — “Next” or
  “Previous” — slides out from behind it. Past a short threshold the adjacent item plays;
  otherwise the cover springs back. Swipe always moves to the adjacent item, whereas the
  transport's previous button keeps the usual “restart if past 3 s” behaviour.
- Haptics on play, favourite and shuffle.
- Ratings start at **0**, move in **±1** steps and can go **negative**: the player shows
  the signed score between the thumbs, and the dashboard rows show only the tinted thumb.
  Negative scores are the disliked side; the weighted shuffle floors every non-positive
  score at the same minimum weight. Playlist and tag management live in the song lists,
  where there is a row to act on, not on the player.
- Ratings are **icon-only** everywhere (dashboard lists and the Now Playing score
  panel): a green thumb-up or magenta thumb-down, no "Liked"/"Disliked" words. The
  active side of the score panel fills with its colour; the inactive side stays muted.
  The phrase survives only as the icon's accessibility description. The player shows
  the score once, in that panel — there is no duplicate badge beside the artist; that
  slot belongs to the metadata pencil instead.

**Dashboard**
- Hero panel answers "what do I have?" (songs / storage used / hours) and "what do I do?"
  (Rescan / Free up space) in one card. Storage is the sum of track file sizes, shown in MB
  or GB; if a provider reports no sizes it falls back to the album count.
- **"Recently added" is the first section under the hero**: the five newest tracks with
  cover tile, artist and a locale-aware relative stamp ("hace 40 min", "11 sept").
  It is a preview, so the section carries no count badge.
- The first time a track URI is seen it is stamped in `LibraryExtras.addedAt`, seeded
  with the file's own modification time when the provider exposes it, and persisted.
  Rescans and tag edits never move an existing stamp; renames follow the file.
- "Continue listening" card appears only when something is queued.
- Top-rated and disliked lists now show rank + a tinted thumb, so the rating direction
  is visible instead of a phrase.
- Empty library becomes a real empty state with a route to Settings.

**Songs**
- Count badge, **sort menu (date added / title / artist / duration)** defaulting to newest
  added first, shuffle-all, and three large
  browse tiles (Files, Albums, Artists) that replaced three small outlined pills.

**Search**
- Auto-focus, clear button, animated focus ring, result count, sectioned results with
  album/artist accent colours, and **recent searches** that can be replayed or cleared.

**Navigation**
- Bottom bar: **Home, Songs, Playlists, Menu** — Playlists replaced Search, which is a
  utility, not a destination people return to.
- Search now lives in the top bar (and in the drawer). On the search screen the same
  slot becomes a close button and returns to the tab the search was opened from.
- Drawer opens from the side of the holding hand, marked with a live dot.
- Back from any side tab returns to Home instead of quitting the app; back from Search
  returns to where Search was opened.
- Switching tabs, opening Search, or picking a drawer destination closes any album or
  artist page open on top of the old tab, so the new content is visible immediately.
  Opening an album from the player closes an open artist page and vice versa: only one
  collection page is ever on top.
- Top bar shows only the section title: the mono eyebrow above it was removed, so the
  header is a single line and the content starts higher.

**App icon**
- Five adaptive icons ship with the app: **Equalizer** (the default), **Vinyl**, **Note**,
  **Wave** and **Headphones**. All use the same dark-green canvas as the player, with the
  palette's green / cyan / lime / magenta accents so they are distinguishable at 48dp.
- Settings → Appearance → **App icon** shows them as a three-column grid with the live
  vector previews from the launcher icon and a ring on the current choice.
- Each icon is a manifest `activity-alias`; selecting one enables its alias and disables
  the other four. Disabling the alias that hosts the current task can make Android tear
  the task down, so the picker warns first and the dialog says the app may close. The
  choice is committed to preferences before the aliases change and is reconciled on the
  next launch.

![App icon picker](screenshots/22-app-icons.png)

**Cover art**
- Every song surface (list row, recent list, mini player, full player) resolves its
  image the same way: **album cover → artist portrait → music glyph**. Covers come from
  `cover.jpg` beside the tracks or embedded artwork; artists fall back to the portrait
  saved from the artist editor.
- Decoding is sampled (192px for rows, 640px for the player) and cached per album folder
  / artist, so a 20-song album decodes its cover once. A missing-art result is cached
  too, and the cache is invalidated when artwork is edited so open lists refresh.
- Settings → "Show album art" disables the whole lookup and shows the music glyph,
  which is also the cheapest mode on slow devices.

**Files**
- The folder path is a breadcrumb, not a single line of text: every ancestor is a link
  back to that level, the strip scrolls horizontally when the path does not fit, and it
  parks at the end so the folder you are inside is always the readable one. Edge fades
  show that there is more path in that direction, and each separator travels with the
  crumb it follows so a clipped ancestor never leaves an orphan "/" behind.
- Every folder row has a star toggle, and the breadcrumb carries one for the folder
  currently open. At the root, starred folders are pinned into a **Starred** section
  above the normal list; a starred top-level folder moves out of the `Folders` list so
  it never appears twice, while deeper stars carry their full path as a subtitle to
  disambiguate same-named folders. Stars are stored by path, resolved against the live
  tree on every draw, so a folder that was moved or deleted simply drops out.
- In the metadata editor, the artist and album fields keep free text but gain a
  dropdown of existing values: it offers the other values when the field already
  matches one, narrows down while typing, and always allows a brand-new name.

**Edit song**
- The metadata editor opens with a **file summary**: size in MB, codec and bitrate
  (“MP3 · 192 kbps”), duration, and a lossy/lossless badge. The size comes from the
  document (falling back to the scan), the bitrate/codec from the container's audio
  track with a metadata-retriever and extension fallback.
- The form is grouped into **File** (name, folder) and **Details** (title, artist, album,
  album artist, year, track, disc, genre, comment). The three short numeric fields share
  one row, the comment is multi-line, and long-text fields keep the full width. Artist
  and album keep the existing-value dropdown.

**Collections, playlists & tags**
- Album and artist indices get section counters and real cover art; artists without a
  portrait show a monogram disc.
- Playlists get a tinted "New playlist" action tile and real cards; empty playlists show
  a proper empty state.
- The Listas screen now lists **every tag** as a tinted `#tag` pill with its live song
  count. Tapping a pill **plays every song carrying that tag** and opens the tag's queue
  (with Play all / Shuffle and the song list), so a tag is a one-tap mix, not a filter.
- Tags are indexed by inverting the per-track store, merged case-insensitively
  ("Rock" / "rock"), sorted alphabetically, and only shown while at least one current
  track carries them. The section explains itself when empty, with a route to a song's
  ⋮ menu.

**Dialogs** inherit the new shape and type scale automatically through the theme.

---

## 7. Accessibility checklist

- [x] AA contrast for all text pairings (most are AAA); accents chosen per theme, never
  a fixed bright green on a light background.
- [x] Minimum 13sp text; body copy 14–17sp; no text below 13sp anywhere.
- [x] Touch targets ≥ 44dp; primary transport 76dp; rows 64–72dp.
- [x] State is never colour-only (rail + equalizer + wording + position).
- [x] Content descriptions on every icon-only control; decorative icons are null.
- [x] Layout verified at **130% font scale**: CTAs stack vertically instead of truncating
  ("Reproducir todo" no longer becomes "Reproduc").
- [x] Haptic feedback confirms destructive/primary taps.
- [x] Light and dark themes both hand-audited; the light theme is not an inversion of
  the dark one.

---

## 8. Files

New:

- `ui/theme/Motion.kt` — timing tokens, press-squash, staggered entrance, glow/border
  modifiers, scanlines, pulse.
- `ui/components/CyberComponents.kt` — section kicker, neon panel, badge, live dot,
  equalizer, song cover, divider.

Rewritten:

- `ui/theme/Theme.kt` — palette, type scale, shapes.
- `ui/MusicPlayerApp.kt` — shell, top bar, bottom nav, mini player, drawer, animated
  screen and page transitions, back handling.
- `ui/screens/NowPlayingScreen.kt`, `DashboardScreen.kt`, `SongsScreen.kt`,
  `SearchScreen.kt`, `CollectionsBrowserScreen.kt`, `CollectionScreen.kt`,
  `ArtistScreen.kt`, `FilesScreen.kt`, `PlaylistsScreen.kt`, `SettingsScreen.kt`.
- `ui/components/SongRow.kt`, `PlayPauseGlyph.kt`, `ListStates.kt`.
- `res/values*/strings.xml` — new strings translated to EN / ES / PT / FR.

Unchanged: playback service, ID3 reader/writer, playlist resolution. The scanner now
also reports each file's modification time and size; `LibraryExtras` persists first-seen
stamps for the Recently added section and now writes through SQLite.

---

## 9. Data & file sync

### Database

Library data lives in its own SQLite database, `library.db`, through
[LibraryStore](../app/src/main/java/dev/melisa/musicplayer/data/LibraryStore.kt):

| Table | Holds |
|---|---|
| `favorites` | favourite URIs |
| `ratings` | per-track signed score (absent = 0) |
| `tags` | `(uri, tag)` pairs |
| `playlists` | playlist id, name, optional dynamic tag rule |
| `playlist_tracks` | ordered URIs per playlist |
| `tracks` | first-seen timestamp per URI ("Recently added") |
| `starred_folders` | File Explorer folder paths pinned by the user |

`LibraryExtras` keeps its existing public API and mirrors the database into Compose
state, writing through on every mutation, so screens did not need to change. On first
run the database is created and the old `extras` preferences blob is imported in one
transaction (favourites, ratings, tags, playlists, first-seen stamps); the old file is
left untouched as a backup. Renames now move ratings as well — previously they were
silently dropped. The schema upgrade rebases legacy 48-centred ratings onto the signed
scale (`(score - 48) / 2`), so an old “liked twice” stays two likes.

### Mirroring into the audio files

The database is the source of truth, and changes with a file-level equivalent are
copied back into the audio file by
[SongFileSync](../app/src/main/java/dev/melisa/musicplayer/data/SongFileSync.kt):

| App data | Written as |
|---|---|
| Rating | `RATING` (ID3 POPM, Vorbis RATING, MP4 freeform where supported; negative clamps to 0/unrated) |
| App tags | `CUSTOM1`, joined with `; ` |
| Favourite | `CUSTOM2` = `1` |

Writes are debounced per track (1.5 s) because each one is a copy → tag → copy-back of
the whole file, so five quick likes rewrite the file once. Failures (read-only folder,
unsupported container, SAF hiccup) are swallowed: the database remains correct and the
next edit retries. Standard metadata edits (title, artist, album, genre, comment…)
already go through `TagEditor` and keep writing to the file as before.

**Not mirrored:** playlists have no per-file representation and stay database-only.
The behaviour is controlled by Settings → "Update audio files"; turning it off cancels
queued writes and skips future ones.

Because every one of these writes is a copy → tag → copy-back of the whole file, the
player must not keep reading the range it opened before the write. In practice ExoPlayer
can go silent while still reporting `PLAYING`, so the UI timer advances over silence. The
same applies to a metadata save or a rename. `MusicPlayerApp` therefore re-opens the
current media item (same or renamed URI) at the last known position after any of those
writes, and `SongFileSync` reports successful writes back to the UI through
`LibraryExtras.onFileRewritten`. A player error also clears the transport state, so the
UI can never claim to be playing after a failed source.

### Scanning

The scanner asks the provider for **one cursor per directory** (document id, name, type,
size, mtime) instead of the four per-file queries `DocumentFile` performs, then parses
only files whose size or mtime changed. Parsed fields live in `track_meta` (DB v3), keyed
by URI, so a cold scan of 42 tracks takes ~1.1 s on the emulator and a warm refresh ~0.5 s;
a library of a few thousand files no longer reopens every file on every tag edit. Cache
misses are parsed with four-way parallelism.

`track_meta` doubles as the **launch snapshot**: `LibraryStore.cachedTracks()` rebuilds
the last completed scan during composition, so the first frame after launch already shows
the real library instead of a full-screen "Scanning" state. The scan then runs behind it
and replaces the list in place; a completed scan prunes cache rows for files it no longer
saw, so deleted tracks never linger in the snapshot. While that refresh runs, a slim amber
sweep in the top bar reports progress without covering any content — the full scanning
state only appears on a first run, when there is genuinely nothing cached to paint. A
rescan keeps the previous list on screen for the same reason, so edits and conversions no
longer blank the list, search results or the free-space view. Search normalizes each
track's fields once per library change instead of on every keystroke.

### Footprint & hardening

- `assembleRelease` enables R8 and resource shrinking with jaudiotagger keep rules:
  **3.2 MB** release APK versus 25 MB debug. The signed release build was smoke-tested
  (scan, tag read/write, compression) on the emulator.
- `android:allowBackup="false"`: the database and preferences are not copied off-device.
- The ID3 reader refuses tags larger than 8 MB (the synchsafe size can claim 256 MB, and
  a malicious file should not be able to allocate that).
- Rename input strips separators and control characters and rejects `.`/`..`; conversion
  output goes through the same unique-name validation.

## 10. Compressing audio

The player can now re-encode a track to a smaller AAC file, so a phone full of 320 kbps
MP3s is not a dead end: **Songs → ⋮ → Compress audio**, the **Audio file** panel in the
metadata editor, or **Settings → Storage → Free up space** for a size-sorted batch queue.

![Free up space](screenshots/20-free-space.png)

![Compress audio](screenshots/21-compress-sheet.png)

### What the conversion does

`AudioTranscoder` is a platform-only pipeline: `MediaExtractor` reads the source,
`MediaCodec` decodes it, a small PCM pipe downmixes to mono/stereo and resamples when the
source rate is outside what the AAC encoder accepts, a second `MediaCodec` encodes
AAC-LC, and `MediaMuxer` writes MPEG-4. No native library, no ffmpeg, no APK weight.
The cost of that choice is format coverage: output is always `.m4a`; MediaMuxer cannot
write MP3 or Ogg.

`CompressAudio` owns the transaction around it:

1. Check cache space and refuse files that cannot shrink.
2. Transcode to a private cache file, reporting progress from presentation time.
3. Re-open the result with `MediaMetadataRetriever` and compare its duration with the
   source (2%, at least 1.5 s). A file that fails is deleted and the original untouched.
4. Copy text tags and the first embedded picture with jaudiotagger. Tag copying is best
   effort, but the duration is checked again afterwards: if the tag commit damaged the
   container, the conversion fails before anything is published.
5. Publish through SAF: write a hidden `.<name>.part` document, then rename it into place
   (or copy under the final name when a provider refuses rename), and verify the size.
6. Only then delete the original, if the user turned "Keep original" off.

The source file is never overwritten in place, and every failure path leaves the library
exactly as it was.

### The numbers the user sees

The sheet shows source codec/bitrate/size, the four presets (96/128/160/192 kbps), the
estimated result size and the percentage saved before anything starts. Presets that
would not save at least 5% are disabled for a single track. In a batch they are skipped
rather than re-encoded into a bigger file, and the sheet says how many.

Lossy → lossy is generation loss, and the sheet says so in plain language. Lossless
sources (FLAC/WAV) get the opposite note: a large saving for very little audible
difference. "Keep original" defaults to on; the original is only removed when it is off.

### Data behaviour

- **Replaced track**: the new document URI takes over every table keyed by URI —
  ratings, favourites, tags, playlist memberships and first-seen stamp — through the
  same `rekey` path a rename uses. The queue also swaps the playing media item, so
  deleting the file underneath playback does not kill the current song.
- **Kept copy**: the new URI inherits the same state via `LibraryExtras.copyState`, while
  the original keeps its own. Both files stay in the library.
- A pending tag write is cancelled before conversion starts: the tag writer rewrites the
  whole file, and letting it race the decoder can hand it a half-written source.

### Limits

- AAC in MP4 only. Opus/OGG decoding depends on the platform (API 29+ for Opus in Ogg);
  unsupported containers fail with a readable message rather than a crash.
- The queue runs in the app process with a progress dialog. It survives navigation but
  not process death; a WorkManager/foreground-service version would be the next step for
  hour-long batch jobs.
- Conversion is CPU-bound: roughly realtime to a few times faster depending on the
  source codec and the device. A five-minute track is a one-off wait, not a background
  service.

---

## 11. Build & run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.melisa.musicplayer/.MainActivity
```

Screenshots used in this document were captured from the emulator (1080 × 2400,
420 dpi, Spanish locale). The tag flow is in `screenshots/08-playlists-dark.png`
(the Listas screen) and `screenshots/14-tag-detail.png` (one tag's queue).

---

## 12. Deliberate non-goals / next

- No new playback data: no "recently played" store, no waveform, no equaliser DSP.
- No native codec: MP3/Opus output would need ffmpeg or libopus, which is out of scope.
- Album/artist artwork editing screens keep their existing layout; they inherit the
  theme but were not re-composed.
- A future pass could persist the current queue across process death (today the mini
  player starts empty after a cold start) and add a letter fast-scroller to the song
  list for very large libraries.
