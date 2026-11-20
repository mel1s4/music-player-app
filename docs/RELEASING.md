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

The repository does not contain a signing key. Build a signed APK/AAB locally in Android
Studio (**Build → Generate Signed App Bundle / APK**) or with a local, git-ignored
`keystore.properties`. Never commit the keystore or its passwords.

Keep the signing key backed up: Android will not accept an update signed with a different
key.

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
