# F-Droid Submission Guide for Lunmella

This repository has been prepared for F-Droid inclusion. Below is what has been configured and the final steps you need to complete to open the Merge Request on `fdroiddata`.

## What Was Prepared in This Repository

- **Application ID & Package**: Updated to `io.github.whoami018.lunmella` across `app/build.gradle.kts`, `settings.gradle.kts`, and `MainActivity.java`.
- **License**: Added GNU GPL v3.0 (`GPL-3.0-only`) in `LICENSE` (required for compatibility with `io.github.junkfood02.youtubedl-android`).
- **Gradle Release Configuration (`app/build.gradle.kts`)**:
  - Disabled `dependenciesInfo` (`includeInApk = false`, `includeInBundle = false`) so Google's encrypted dependency metadata block is not embedded.
  - Disabled `vcsInfo.include` in `release` for reproducible builds.
  - Removed hardcoded debug signing from `release` and wired optional keystore properties/env vars so `./gradlew assembleRelease` produces unsigned release APKs on F-Droid build servers.
  - Enabled per-ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) with deterministic `versionCodeOverride` (`baseVersionCode * 10 + abiCode`: `1` = `armeabi-v7a`, `2` = `arm64-v8a`, `3` = `x86`, `4` = `x86_64`) to keep each APK around ~50-60 MB instead of a ~203 MB universal APK.
- **Fastlane Store Metadata (`fastlane/metadata/android/en-US/`)**:
  - `title.txt`, `short_description.txt`, `full_description.txt`
  - `images/icon.png` (512x512) and `images/featureGraphic.png` (1024x500)
  - `changelogs/1.txt`, `11.txt`, `12.txt`, `13.txt`, `14.txt`
- **Documentation**: Added `README.md`.

## Before Tagging & Submitting

1. **Add Phone Screenshots (Recommended)**:
   Add at least 2 PNG or JPG screenshots of the app to:
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` (e.g., `01_home.png`, `02_formats.png`).
   *(Empty directories are not tracked by Git, so F-Droid will only see screenshots once files are added there.)*

2. **Commit, Push, and Create a Release Tag**:
   ```bash
   git add -A
   git commit -m "Prepare Lunmella v1.0.0 for F-Droid"
   git tag v1.0.0
   git push origin main --tags
   ```
   Then record the exact commit hash:
   ```bash
   git rev-parse v1.0.0^{commit}
   ```

## Submitting to `fdroiddata`

1. Fork [`https://gitlab.com/fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata) on GitLab and create a new branch (e.g., `add-lunmella`).
2. Create `metadata/io.github.whoami018.lunmella.yml` (using Unix `LF` line endings) with the template below, replacing `<COMMIT_HASH>` with the output of `git rev-parse v1.0.0^{commit}`:

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Connects to proprietary platforms (Facebook/Instagram) and uses the GitHub API to check and download yt-dlp updates.
Categories:
  - Internet
  - Multimedia
License: GPL-3.0-only
AuthorName: whoami018
SourceCode: https://github.com/whoami018/Lunmella
IssueTracker: https://github.com/whoami018/Lunmella/issues
Changelog: https://github.com/whoami018/Lunmella/releases

AutoName: Lunmella

RepoType: git
Repo: https://github.com/whoami018/Lunmella.git

Builds:
  - versionName: 1.0.0
    versionCode: 11
    commit: <COMMIT_HASH>
    subdir: app
    gradle:
      - yes
    output: build/outputs/apk/release/*-armeabi-v7a-*.apk
    scandelete:
      - gradle/wrapper/gradle-wrapper.jar

  - versionName: 1.0.0
    versionCode: 12
    commit: <COMMIT_HASH>
    subdir: app
    gradle:
      - yes
    output: build/outputs/apk/release/*-arm64-v8a-*.apk
    scandelete:
      - gradle/wrapper/gradle-wrapper.jar

  - versionName: 1.0.0
    versionCode: 13
    commit: <COMMIT_HASH>
    subdir: app
    gradle:
      - yes
    output: build/outputs/apk/release/*-x86-*.apk
    scandelete:
      - gradle/wrapper/gradle-wrapper.jar

  - versionName: 1.0.0
    versionCode: 14
    commit: <COMMIT_HASH>
    subdir: app
    gradle:
      - yes
    output: build/outputs/apk/release/*-x86_64-*.apk
    scandelete:
      - gradle/wrapper/gradle-wrapper.jar

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
VercodeOperation:
  - '%c * 10 + 1'
  - '%c * 10 + 2'
  - '%c * 10 + 3'
  - '%c * 10 + 4'
CurrentVersion: 1.0.0
CurrentVersionCode: 14
```

3. Open a Merge Request against `fdroid/fdroiddata` (`master`) and follow the MR template checklist. Monitor the GitLab CI pipeline (`fdroid lint` / build checks) and respond to F-Droid reviewer comments.
