![Findroid banner](images/findroid-banner.png)

# Findroid - Beta
![GitHub release (with filter)](https://img.shields.io/github/v/release/jarnedemeulemeester/findroid?style=for-the-badge)
![GitHub repo stars](https://img.shields.io/github/stars/jarnedemeulemeester/findroid?style=for-the-badge)
![GitHub issues](https://img.shields.io/github/issues/jarnedemeulemeester/findroid?style=for-the-badge)
![GitHub pull requests](https://img.shields.io/github/issues-pr/jarnedemeulemeester/findroid?style=for-the-badge)
![GitHub all releases](https://img.shields.io/github/downloads/jarnedemeulemeester/findroid/total?style=for-the-badge)
![GitHub](https://img.shields.io/github/license/jarnedemeulemeester/findroid?style=for-the-badge)

> [!IMPORTANT]
> **Beta Release & Notice:**
> This repository contains a Beta release designed to test potential new features, updated user interfaces, and architectural redesigns for Findroid.
>
> 🤖 **AI Assistance Disclaimer:**
> Artificial Intelligence tools were utilized during the development, refactoring, and documentation of these new features. While all code has undergone static analysis, formatting checks (`ktfmt`), and automated build verifications, unexpected issues may arise. If you encounter any bugs, please report them directly to the [GitHub repository issue tracker](https://github.com/jarnedemeulemeester/findroid/issues).

Findroid is third-party Android application for Jellyfin that provides a native user interface to browse and play movies and series.

I am developing this application in my spare time.

**This project is in its early stages so expect bugs.**

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=dev.jdtech.jellyfin)
[<img src="https://user-images.githubusercontent.com/32322857/219019331-027a6775-7362-44bb-a026-281f71e9b37b.png" alt="Available at Amazon Appstore" height="80">](https://www.amazon.com/gp/product/B0BTWC8DNZ)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/dev.jdtech.jellyfin)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/dev.jdtech.jellyfin)

## Screenshots
| Home                                | Library                             | Movie                           | Season                            | Episode                             |
|-------------------------------------|-------------------------------------|---------------------------------|-----------------------------------|-------------------------------------|
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Library](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Movie](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Season](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) | ![Episode](fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png) |

## Features
- Completely native interface
- Supported media items: movies, series, seasons, episodes 
  - Direct play only, (no transcoding)
- Offline playback / downloads
- ExoPlayer
  - Video codecs: H.263, H.264, H.265, VP8, VP9, AV1 
    - Support depends on Android device
  - Audio codecs: Vorbis, Opus, FLAC, ALAC, PCM, MP3, AAC, AC-3, E-AC-3, DTS, DTS-HD, TrueHD 
    - Support provided by ExoPlayer FFmpeg extension
  - Subtitle codecs: SRT, VTT, SSA/ASS, PGSSUB
    - SSA/ASS has limited styling support see [this issue](https://github.com/google/ExoPlayer/issues/8435)
- mpv
  - Container formats: mkv, mov, mp4, avi
  - Video codecs: H.264, H.265, H.266, VP8, VP9, AV1
  - Audio codecs: Opus, FLAC, MP3, AAC, AC-3, E-AC-3, TrueHD, DTS, DTS-HD
  - Subtitle codecs: SRT, VTT, SSA/ASS, DVDSUB
  - Optionally force software decoding when hardware decoding has issues.
- Picture-in-picture mode
- Media chapters
  - Timeline markers
  - Chapter navigation gestures
- Trickplay (requires Jellyfin 10.9 or higher)
- Media segments (requires Jellyfin 10.10 or higher)
  - Skip button
  - Auto skip

### ✨ New - Beta only

- **Phone Player Redesign**
  - Modern Jetpack Compose player UI with custom progress bar
  - Sleep timer picker
  - Playback speed selector
  - Aspect ratio selector
  - Chapters list
- **Android TV**
  - Redesigned TV home carousel and main screen navigation
  - Dedicated TV video player controls layout with D-Pad scrubbing, chapter pills, and settings panel
  - TV screensaver
  - Quick connect screen as default login
- **Advanced Search & Filtering**
  - Added Search screen
  - Filter chips and bottom sheet
- **Anti-Spoiler Mode**
  - Particle mask over episode titles, thumbnails, and descriptions to prevent spoilers
  - Interactive press-and-hold reveal gestures
  - Phone and TV anti-spoiler mode
- **Offline Playback & Download Presets**
  - Redesigned Downloads page
  - Resumable direct-stream downloads powered by OkHttp 3
  - Foreground download service with persistent progress notifications
  - Multi-storage volume management (internal storage & SD card)
  - **Alpha:**
    - Smart Downloads
    - Transcode downloads
- **Google Cast (Chromecast)**
  - Cast Button and mini player
  - Expanded Cast player with:
    - scrubbing timeline
    - chapter dialog
    - track/subtitle selector
    - volume controls
- **Media Segment & Skip Configuration**
  - Custom segment skip actions
  - Configurable color for segments
- **Multi-Part Video support**
- **Blur hash Image placeholders**
  - blur hash image placeholders
  - exact dimension image requests from the Jellyfin server
- **Account & Connection Management**
  - User account menu with Quick Connect authentication input support

## Planned features
- Android TV - Search feature
- Websocket connection (Syncplay)

## Translating
[JDTech Weblate](https://weblate.jdtech.dev) is a self-hosted instance of Weblate where you can translate this project and future projects of mine.

## Questions?
We have a [Discord server](https://discord.gg/tg5VvTFwTV) to discuss future development or ask general questions.

## License
This project is licensed under [GPLv3](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot.

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

Android is a trademark of Google LLC.

Google Play and the Google Play logo are trademarks of Google LLC.
