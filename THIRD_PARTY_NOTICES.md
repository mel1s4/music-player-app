# Third-party notices

Music Player itself is released under the [MIT License](LICENSE). It depends on the
following third-party components, which remain under their own licenses. This file is a
courtesy summary, not legal advice; when redistributing a binary, verify the current
license texts in the dependency artifacts.

| Component | Used for | License |
|---|---|---|
| [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/androidx) | UI, lifecycle, activity integration | Apache License 2.0 |
| [AndroidX Media3 (ExoPlayer + MediaSession)](https://github.com/androidx/media) | Playback, foreground service, media session | Apache License 2.0 |
| [Kotlin standard library](https://kotlinlang.org/) | Language runtime | Apache License 2.0 |
| [Material Icons Extended](https://fonts.google.com/icons) | Icons | Apache License 2.0 |
| [jaudiotagger](https://github.com/jthink/jaudiotagger) 3.0.1 | Reading and writing ID3/Vorbis/MP4 tags | GNU Lesser General Public License (LGPL) |
| [androidx.documentfile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile) | SAF fallback | Apache License 2.0 |
| [Contributor Covenant](https://www.contributor-covenant.org/) 2.1 | Code of conduct text | Creative Commons Attribution 4.0 |

## jaudiotagger and the LGPL

jaudiotagger is used **unmodified** as a Gradle dependency
(`net.jthink:jaudiotagger:3.0.1`) and is compiled into the APK. Its source and license
text are available from the upstream project:
<https://github.com/jthink/jaudiotagger>.

If you redistribute a built APK or AAB, the LGPL requires that recipients can obtain the
corresponding source of the LGPL component and can relink it. This repository contains
the full application source, the dependency is declared openly in
[app/build.gradle.kts](app/build.gradle.kts), and the upstream source is linked above.
Keep those materials available and do not add restrictions that prevent replacing the
library.

The app's own source is MIT-licensed; the LGPL applies to jaudiotagger, not to the rest
of the app.
