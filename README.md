<div align="center">

# ⚡ VoltHalt

### Smart Battery Charge Alarm for Android

**Stop overcharging. Set your target. VoltHalt does the rest.**

[![Release](https://img.shields.io/github/v/release/YOUR_USERNAME/VoltHalt?color=F5A623&label=Download&logo=android)](https://github.com/YOUR_USERNAME/VoltHalt/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024-blue?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Stars](https://img.shields.io/github/stars/YOUR_USERNAME/VoltHalt?style=social)](https://github.com/YOUR_USERNAME/VoltHalt/stargazers)

</div>

---

## What is VoltHalt?

**VoltHalt** is a free, open-source Android app that monitors your battery level in the background and plays a loud alarm the moment your phone reaches your chosen charge percentage. Unlike manufacturer battery limits, VoltHalt works on **any Android 8.0+ device** without root.

> Battery health experts recommend keeping lithium-ion batteries between **20% and 80%** for maximum lifespan. VoltHalt makes this effortless.

---

## Features

| Feature | Description |
|---------|-------------|
| **Custom Charge Target** | Set any percentage from 1–99% as your alarm trigger |
| **Max Volume Alarm** | Forces maximum speaker volume so you always hear it |
| **Full-Screen Alert** | Alarm screen wakes your phone even when locked |
| **Quick Settings Tile** | Toggle the alarm directly from your notification shade |
| **Auto-Dismiss** | Alarm screen closes automatically when you unplug |
| **Boot Persistence** | Service restarts automatically after device reboot |
| **Battery Optimized** | Requests battery optimization exemption for reliability |
| **Share APK** | Share the app directly with friends from within the app |
| **Material 3 UI** | Clean, modern dark-mode interface built with Jetpack Compose |

---

## Download

<div align="center">

### [Download Latest APK](https://github.com/YOUR_USERNAME/VoltHalt/releases/latest)

</div>

> **Note:** Since this APK is not from the Play Store, you need to enable **"Install from unknown sources"** in your Android settings before installing.
>
> **Settings → Security → Install unknown apps** → Allow for your file manager

---

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) + [Material 3](https://m3.material.io/)
- **Architecture**: Single-Activity with Compose Navigation
- **Persistence**: [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- **Background Service**: Android Foreground Service with `BroadcastReceiver`
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

---

## Build from Source

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 8+
- Android SDK 34

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/VoltHalt.git

# 2. Open in Android Studio
# File → Open → select the VoltHalt folder

# 3. Let Gradle sync complete, then build
# Build → Make Project   (Ctrl+F9)

# 4. Run on your device or emulator
# Run → Run 'app'   (Shift+F10)
```

### Build release APK
```bash
# In Android Studio:
# Build → Generate Signed Bundle / APK → APK → follow the wizard
```

---

## Project Structure

```
VoltHalt/
├── app/
│   └── src/main/
│       ├── java/com/example/batteryalarm/
│       │   ├── MainActivity.kt          # App entry point
│       │   ├── AlarmActivity.kt         # Full-screen alarm overlay
│       │   ├── BatteryService.kt        # Background monitoring service
│       │   ├── AlarmPlayer.kt           # Audio playback at max volume
│       │   ├── AlarmTileService.kt      # Quick Settings tile
│       │   ├── PreferencesManager.kt    # DataStore persistence
│       │   └── ui/
│       │       ├── screens/
│       │       │   ├── MainScreen.kt    # Home UI
│       │       │   ├── SettingsScreen.kt
│       │       │   └── SetupScreen.kt
│       │       └── theme/
│       │           └── Theme.kt
│       ├── res/                         # Icons, strings, themes
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Permissions Explained

VoltHalt requests only what it truly needs:

| Permission | Why it's needed |
|-----------|----------------|
| `FOREGROUND_SERVICE` | Run monitoring service without Android killing it |
| `VIBRATE` | Vibrate alongside the alarm sound |
| `POST_NOTIFICATIONS` | Show a persistent notification while monitoring |
| `RECEIVE_BOOT_COMPLETED` | Restart the service after a device reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from suspending the service |
| `WAKE_LOCK` | Wake the CPU to fire the alarm reliably |
| `USE_FULL_SCREEN_INTENT` | Show alarm screen over the lock screen (Android 14+) |

> ⚠️VoltHalt does **not** access the internet, your contacts, location, camera, or microphone.

---

## Contributing

Contributions are always welcome! Here's how to get started:

1. **Fork** this repository
2. Create a new branch: `git checkout -b feature/your-feature-name`
3. Make your changes and commit: `git commit -m 'feat: add some feature'`
4. Push to your fork: `git push origin feature/your-feature-name`
5. Open a **Pull Request**

### Ideas for contributions
- [ ] Scheduled alarm (e.g., "alarm only between 10 PM and 6 AM")
- [ ] Multiple charge targets with different alarm sounds
- [ ] Wear OS companion app
- [ ] Translations / localization
- [ ] Widget for home screen

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.

---

## Found a Bug?

[Open an issue](https://github.com/YOUR_USERNAME/VoltHalt/issues/new) and include:
- Your Android version
- Device model
- Steps to reproduce the bug
- What you expected vs. what happened

---

## License

```
MIT License

Copyright (c) 2026 YOUR NAME

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**If VoltHalt saved your battery, give it a ⭐ — it really helps!**

</div>
