# Contributing to Music Player

Thanks for taking the time to contribute. This project is a local-first Android music
player written in Kotlin and Jetpack Compose, and it gets better with bug reports,
translations, documentation and focused pull requests.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** with the bug report template. Include the app version, Android
  version, device/emulator, and the smallest reproduction you can manage.
- **Suggest a feature** with the feature request template. Explain the problem before
  the solution.
- **Improve translations** for English, Spanish, French or Portuguese.
- **Improve docs** in `README.md`, `docs/DESIGN.md` or the templates.
- **Send code** — small, focused pull requests are easiest to review.
- **Add tests** around scanning, tag I/O, database migrations or rating weights.
- **Review** other people's pull requests if you know the area.

## Development setup

Requirements:

- Android Studio (latest stable) or a command-line Android SDK.
- JDK 17 or newer. Android Studio's bundled JDK works; set `JAVA_HOME` if you build
  from a terminal.
- Android SDK Platform 36 and Build Tools 36.
- A device or emulator on Android 8.0 (API 26) or newer.

```bash
git clone https://github.com/mel1s4/music-player-app.git
cd music-player-app

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the folder in Android Studio if you prefer; `local.properties` is generated there
and is git-ignored.

Before opening a pull request, run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

The CI workflow runs the same two checks and also builds the minified release APK to catch R8 issues.

## Project conventions

- **Kotlin and Compose style.** Four-space indentation, trailing commas, one top-level
  class per file where practical. Follow the existing naming and formatting.
- **Comments explain *why*.** Avoid comments that restate the code. The existing code
  documents tradeoffs, provider quirks and platform behaviour; keep that habit.
- **Keep layers separate.** File-system and database work belongs in `data/`, playback
  ownership in `playback/`, and UI/state in `ui/`. Screens should not talk to SQL.
- **No new permissions or network access without discussion.** The app's promise is
  local-only storage access through the Storage Access Framework. Opening a network
  connection or asking for broad storage permissions is a product decision, not a drive-by
  change.
- **Do not commit secrets or binaries.** Keystores, `local.properties`, built APKs and
  `dist/` are git-ignored. Use Android Studio or a local `keystore.properties` for
  signing.
- **UI changes need a screenshot** in the pull request. Include large-text and light-theme
  screens when relevant.

## Branching and commits

- Branch from `main`: `feat/short-description`, `fix/short-description`,
  `docs/short-description`, `chore/short-description`.
- Keep commits small and imperative: `Fix playback after a tag rewrite`, not
  `fixed stuff`.
- Reference the issue in the pull request body (`Closes #123`), not necessarily in the
  commit subject.
- Rebase or merge `main` before requesting review; resolve conflicts locally.

## Pull request checklist

- [ ] The change is focused and the motivation is explained.
- [ ] `./gradlew :app:assembleDebug :app:lintDebug` passes.
- [ ] New strings are added to **all four** locales (`values`, `values-es`, `values-fr`,
      `values-pt`), or marked `translatable="false"` when they are not language content.
- [ ] UI changes include before/after screenshots, including large text where relevant.
- [ ] Data or architecture changes update `docs/DESIGN.md` and `CHANGELOG.md`.
- [ ] No keystore, SDK path, personal data or generated APK is included.

## Translations

Strings live in:

```text
app/src/main/res/values/strings.xml       English
app/src/main/res/values-es/strings.xml    Spanish
app/src/main/res/values-fr/strings.xml    French
app/src/main/res/values-pt/strings.xml    Portuguese
```

Keep placeholders (`%1$d`, `%1$s`) exactly as they are and preserve XML escaping. If a
translation is intentionally identical to English, that is fine; do not delete the
resource. Add a new locale as `values-<tag>/strings.xml` and register it in
`app/src/main/res/xml/locales_config.xml`.

## Licensing of contributions

By submitting a pull request you agree that your contribution is licensed under the
project's [MIT License](LICENSE). There is no separate CLA.

## Security

Please do not report security vulnerabilities in public issues. See
[SECURITY.md](SECURITY.md).

## Maintainers

The release checklist lives in [docs/RELEASING.md](docs/RELEASING.md). It covers version
bumps, the smoke-test list, signing and creating the GitHub Release.
