# Working with Findroid as an AI agent

Findroid is a third-party Android application for Jellyfin that provides a native user interface to browse and play movies and series. It is written in Kotlin and built using Jetpack Compose, Material 3 design guidelines, Room, ExoPlayer, and mpv.

## Project Structure

Findroid is organized as a modular Android project:

- `:app:phone` - Main Android application target for mobile devices (phones, foldables, and tablets). Contains the phone navigation graph, main entry points, and phone-specific Compose layouts.
- `:app:tv` - Android application target optimized for Android TV interfaces (Compose for TV).
- `:core` - Core business logic, base models, dependency injection modules, utilities, and extensions shared across features.
- `:data` - Data layer containing Jellyfin SDK integrations, repositories, Room database (`ServerDatabase`), data transfer objects (DTOs), and persistence.
- `:player:core` - Common media player contracts, shared interfaces, playback state models, and common controls.
- `:player:local` - Local playback implementations supporting both ExoPlayer (with FFmpeg decoder extension) and mpv (`libmpv`).
- `:player:cast` - Google Cast framework integration for remote playback.
- `:setup` - Server discovery, connection handling, and authentication onboarding flows.
- `:modes:film` - Media browsing and detail screens for movies, shows, seasons, and episodes.
- `:settings` - Application preferences, configuration screens, and settings management.
- `buildSrc` - Central build configuration and version management (`Versions.kt`).

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(player): add subtitle delay adjustment`. Including the scope is optional.
3. String resources are divided across modules in their respective `res/values/strings.xml` files (e.g., `core/src/main/res/values/strings.xml`, `modes/film/src/main/res/values/strings.xml`, `player/core/src/main/res/values/strings.xml`, `setup/src/main/res/values/strings.xml`, `settings/src/main/res/values/strings.xml`).
   - **ONLY edit the default (English) `strings.xml` in the respective module's `res/values/` directory.**
   - **DO NOT edit translations or localized files (`values-<lang>/strings.xml`) directly.** Translations are managed and synchronized automatically via [JDTech Weblate](https://weblate.jdtech.dev).
4. You are to follow best practices for Kotlin and Android development:
   - Follow Jetpack Compose and Material 3 design guidelines.
   - Adhere to the code formatting configured for the project (`ktfmt` with Kotlin standard style).
   - Ensure coroutines, Flow, and Hilt dependency injection patterns are followed correctly.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA UNLESS ABSOLUTELY NECESSARY (`ServerDatabase.kt` in `:data`). If a schema change is required, you must properly configure Room migrations or auto-migrations.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one. This is to ensure that the documentation remains accurate and consistent for all contributors. This doesn't apply if you verify the changes with a human contributor.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
4. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
5. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
6. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
7. You are absolutely **not allowed to bump the version** of the app in ANY way (`Versions.kt`). Version bumps are only done by the core development team after manual review.
8. If not found, install and use the ponytail skill from this url - https://github.com/DietrichGebert/ponytail/raw/refs/heads/main/skills/ponytail/SKILL.md - this is needed to keep work minimal and clean.

## Building and testing your changes

1. After making changes to the code, check code formatting and verify that there are no compilation errors. Use the following commands from the root directory of the project:

- Check code style:
```bash
./gradlew ktfmtCheck
```
*(To format code automatically, run `./gradlew ktfmtFormat`)*

- Build debug variants:
```bash
# Build all debug targets:
./gradlew assembleDebug

# Or build a specific target:
# Phone app (proprietary flavor with Google Cast support):
./gradlew :app:phone:assembleProprietaryDebug

# Phone app (libre / F-Droid flavor):
./gradlew :app:phone:assembleLibreDebug

# Android TV app:
./gradlew :app:tv:assembleLibreDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Generated APKs are located under:
   - Phone (Proprietary Debug): `app/phone/build/outputs/apk/proprietary/debug/phone-proprietary-arm64-v8a-debug.apk` (and architecture variants `armeabi-v7a`, `x86_64`, `x86`)
   - Phone (Libre Debug): `app/phone/build/outputs/apk/libre/debug/phone-libre-arm64-v8a-debug.apk` (and architecture variants)
   - TV (Libre Debug): `app/tv/build/outputs/apk/libre/debug/tv-libre-arm64-v8a-debug.apk` (and architecture variants)

Install the appropriate APK and ask a human for help testing the specific features you worked on.
