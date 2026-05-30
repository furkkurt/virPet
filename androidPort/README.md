# VirPet Android Port

## Open in Android Studio

1. **File → Open** → select this `androidPort` folder.
2. Wait for Gradle sync.
3. **Run** on a device/emulator, or **Build → Build APK(s)**.

## Assets

Game assets are copied from `../assets/` into `app/src/main/assets/`. After changing desktop assets, re-copy:

```bash
cp -r ../assets/* app/src/main/assets/
```

Includes `bg.mp3`, `tap.mp3`, `death.mp3`, and `runaway.mp3` for in-game audio.

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer recommended
- JDK 17 for Gradle 8.x (Android Studio bundles this)
- Android SDK at `sdk.dir` in `local.properties` (default: `/home/furkan/Android/Sdk`)

If Gradle reports conflicting SDK paths (`ANDROID_HOME` vs `ANDROID_SDK_ROOT`), use only one:

```bash
export ANDROID_HOME=~/Android/Sdk
unset ANDROID_SDK_ROOT
./gradlew assembleDebug
```

Task name is **`assembleDebug`** (not `assemleDebug`).
