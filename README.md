# Lunmella

Lunmella is an open-source Android application for downloading public Facebook and Instagram videos, reels, and audio tracks using [yt-dlp](https://github.com/yt-dlp/yt-dlp), [FFmpeg](https://ffmpeg.org/), and [youtubedl-android](https://github.com/yausername/youtubedl-android).

## Features

- Paste video/reel links from the clipboard or share links directly to Lunmella via Android's Share sheet (`ACTION_SEND`).
- Fetch available quality options (Auto HD, 1080p, 720p, 480p, source formats, and Audio-only M4A).
- Merge video and audio streams automatically with FFmpeg.
- Track download progress with ETA and cancel active downloads anytime.
- Open downloaded media directly via `FileProvider`.
- Update the bundled `yt-dlp` extraction engine from the in-app Settings dialog.

## Building from Source

### Requirements

- JDK 17
- Android SDK (compileSdk 36, minSdk 24, targetSdk 36)

### Build Commands

```bash
# Build unsigned per-ABI release APKs (armeabi-v7a, arm64-v8a, x86, x86_64)
./gradlew assembleRelease
```

To sign release APKs locally, pass keystore properties via `gradle.properties`, `-P` flags, or environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). When no keystore is provided, `assembleRelease` produces unsigned release APKs suitable for F-Droid signing.

## Dependencies & Attribution

- [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense)
- [FFmpeg](https://ffmpeg.org/)
- AndroidX (`appcompat`, `core`, `activity`)

## License

Lunmella is licensed under the GNU General Public License v3.0 (`GPL-3.0-only`). See [LICENSE](LICENSE) for the full license text.
