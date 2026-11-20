# Releasing

Maintainer checklist for a public release. The app uses [Semantic Versioning](https://semver.org/):
`MAJOR.MINOR.PATCH`, currently in the `0.x` series.

## 1. Prepare

- [ ] Make sure `main` is green in CI.
- [ ] Update `CHANGELOG.md`: move entries from `Unreleased` under a new version and date.
- [ ] Bump `versionCode` and `versionName` in
      [`app/build.gradle.kts`](../app/build.gradle.kts). `versionCode` must increase for
      every published APK; `versionName` is what the About screen shows via
      `BuildConfig.VERSION_NAME`.
- [ ] Review `README.md`, `PRIVACY.md` and `docs/DESIGN.md` for stale screenshots or
      claims.

## 2. Verify

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:assembleRelease
```

Install the debug APK on a clean device or emulator and smoke-test:

- add and remove a library folder;
- scan and play, including background playback and lock-screen controls;
- edit metadata for the currently playing track;
- star/unstar a folder and relaunch;
- compress one track and check free-space reporting;
- switch theme and language.

R8 minification can surface reflection issues that debug builds hide, so install the
release build too before publishing.

## 3. Sign

The repository does not contain a signing key. The build reads an optional, git-ignored
`keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/release.jks
keyAlias=music-player
storePasswordFile=/absolute/path/to/password.txt
keyPasswordFile=/absolute/path/to/password.txt
```

`storePassword` / `keyPassword` can be used instead of the `*File` variant, and relative
paths are resolved from the project root. If the file or any value is missing, the
release build is left unsigned, which is what CI does.

Generate a key once:

```bash
keytool -genkeypair -v \
  -keystore release.jks -storetype PKCS12 \
  -alias music-player -keyalg RSA -keysize 4096 -validity 10000
```

Never commit the keystore, the password file or `keystore.properties`. Keep at least two
offline backups: Android will not accept an update signed with a different key. The
release APK is written to `app/build/outputs/apk/release/app-release.apk`; verify it with
`apksigner verify --print-certs` before attaching it to a release.

## 4. Publish

```bash
git tag -a vX.Y.Z -m "Music Player X.Y.Z"
git push origin main
git push origin vX.Y.Z
```

Create a GitHub Release for the tag:

- title: `Music Player X.Y.Z`;
- attach the signed APK (and AAB if publishing to a store);
- use the matching `CHANGELOG.md` section as the release notes;
- mention any known issues and the minimum Android version.

After the release, verify the F-Droid or store listing (if applicable), then start a new
`Unreleased` section in the changelog.

## 5. Post-release

- [ ] Confirm the release is installable on a clean device.
- [ ] Close the milestone and triage new issues.
- [ ] Security reports follow [SECURITY.md](../SECURITY.md), not the public tracker.
