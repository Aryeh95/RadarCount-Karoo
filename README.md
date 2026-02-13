# eiRadar

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform: Karoo](https://img.shields.io/badge/Platform-Karoo%202%2F3-blue.svg)](https://www.hammerhead.io/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg)](https://kotlinlang.org/)
[![AGP](https://img.shields.io/badge/AGP-8.7.3-3DDC84.svg)](https://developer.android.com/build)

Rear radar alert extension for Hammerhead Karoo 2/3. Displays vehicle proximity data from Garmin Varia radar with real-time visual, sound, and haptic alerts.

<p align="center">
  <a href="#installation">Installation</a> &bull;
  <a href="#quick-start">Quick Start</a> &bull;
  <a href="#data-fields">Data Fields</a> &bull;
  <a href="#alerts">Alerts</a> &bull;
  <a href="#settings">Settings</a> &bull;
  <a href="#development">Development</a>
</p>

---

## Overview

eiRadar provides safety-focused rear vehicle alerts on Karoo ride screens. The extension processes ANT+ radar data and delivers immediate feedback through configurable alert channels — designed to keep your eyes on the road, not the screen.

### Key Features

| Feature | Description |
|---------|-------------|
| **3 Data Layouts** | Small (1x1), Medium (2x1), Large (2x2) with adaptive content |
| **Multi-channel Alerts** | Visual banner, sound patterns, haptic vibration |
| **Threat Levels** | Approaching, Warning, Critical — color-coded |
| **Speed Gate** | Suppress minor alerts below minimum speed (critical alerts always fire) |
| **Imperial/Metric** | Automatic unit detection from Karoo profile |
| **Fully Offline** | No accounts, no cloud, no internet required |

---

## Quick Start

### 1. Pair Radar

Connect your Garmin Varia radar in Karoo **Settings > Sensors**.

### 2. Add Data Field

1. Open **Profiles** on Karoo
2. Edit your profile > Add data page
3. Select **More Data** > **eiRadar**
4. Choose a layout size

### 3. Ride

Alerts fire automatically when vehicles are detected behind you.

---

## Data Fields

Three layout sizes built with Jetpack Glance for stable, crash-free 1Hz updates:

| Layout | Size | Content |
|--------|:----:|---------|
| **Radar (S)** | 1x1 | Vehicle count, background color indicates threat level |
| **Radar (M)** | 2x1 | Vehicle count + distance or status text |
| **Radar (L)** | 2x2 | Threat label + vehicle count + distance + status message |

### Threat Colors

| Color | Level | Meaning |
|:-----:|-------|---------|
| Grey | Not connected | No radar paired or connection lost |
| Green | Clear | Road is clear |
| Orange | Approaching / Warning | Vehicle detected, closing distance |
| Red | Critical | Vehicle very close — take action |

---

## Alerts

Three independent alert channels, all configurable:

| Channel | Description | Default |
|---------|-------------|:-------:|
| **Banner** | Karoo in-ride visual alert with auto-dismiss | On |
| **Sound** | Beep patterns (4 sound sets: Classic, Subtle, Urgent, Bell) | On |
| **Vibration** | Haptic feedback — most reliable while riding | On |

### Safety Design

- **Critical alerts always fire** regardless of speed gate setting
- Haptic alerts enabled by default — vibration works even at high speed and wind noise
- All-clear sound confirms when the road is clear again
- Repeat delay prevents alert fatigue (configurable: 3s / 5s / 8s / 12s)

---

## Settings

Tap any value to cycle through options. Settings are accessible from the eiRadar app.

### Alert Channels

| Setting | Options | Default |
|---------|---------|:-------:|
| Enable alerts | On / Off | On |
| Banner | On / Off | On |
| Sound | On / Off + sound set | On, Classic |
| Vibration | On / Off | On |
| All-clear sound | On / Off | On |

### Detection Thresholds

| Setting | Options | Default |
|---------|---------|:-------:|
| Approaching | 100 / 125 / 150 / 175 / 200m | 100m |
| Warning | 30 / 40 / 50 / 60 / 70m | 50m |
| Critical | 10 / 15 / 20 / 25 / 30m | 20m |

### Behavior

| Setting | Options | Default |
|---------|---------|:-------:|
| Repeat delay | 3s / 5s / 8s / 12s | 5s |
| Min speed | Off / 3 / 5 / 8 km/h | Off |
| Wake screen | Never / Critical only / Always | Critical only |

> **Note:** Min speed only suppresses Approaching alerts. Warning and Critical alerts always fire — a stopped cyclist at a traffic light is the most vulnerable target.

---

## Compatible Radars

Any ANT+ cycling radar paired through Karoo sensor settings:

| Radar | Type |
|-------|------|
| Garmin Varia RTL515 | Tail light + radar |
| Garmin Varia RTL516 | Tail light + radar |
| Garmin Varia RVR315 | Radar only |
| Bryton Gardia R300L | Tail light + radar |
| Magene L508 | Tail light + radar |

---

## Installation

### From APK

```bash
# ADB
adb install eiradar.apk

# Or via Hammerhead Companion app:
# 1. Download APK on your phone
# 2. Share to Hammerhead Companion
# 3. Install on Karoo
```

### Build from Source

**Prerequisites:**
- Android Studio (JDK 17 bundled)
- GitHub PAT with `read:packages` scope (for Karoo SDK)

**Setup:**

```bash
# Add to ~/.gradle/gradle.properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

**Build:**

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/eiradar.apk`

---

## Development

### Architecture

```
io/github/ykn/variaradarpro/
├── VariaRadarExtension.kt       # KarooExtension entry point
├── MainActivity.kt              # Compose UI (settings, onboarding, dashboard)
│
├── engine/                      # Core logic
│   ├── RadarEngine.kt           # ANT+ radar data → StateFlow<WidgetState>
│   ├── AlertManager.kt          # Threat evaluation + alert dispatch
│   ├── AlertThrottler.kt        # Per-level cooldown management
│   ├── SoundEngine.kt           # Karoo PlayBeepPattern integration
│   ├── HapticEngine.kt          # Android Vibrator patterns
│   ├── NightModeManager.kt      # Day/night detection
│   └── StatisticsCollector.kt   # Ride statistics aggregation
│
├── datatypes/glance/            # Karoo data fields (Jetpack Glance)
│   ├── GlanceDataType.kt        # Base: fresh RemoteViews per update
│   ├── SmallWidgetGlanceDataType.kt
│   ├── MediumWidgetGlanceDataType.kt
│   └── LargeWidgetGlanceDataType.kt
│
├── data/                        # Persistence
│   ├── PreferencesRepository.kt # DataStore settings
│   ├── models/                  # AlertSettings, PresetSettings, ThreatLevel, WidgetState
│   └── database/                # Room (ride statistics)
│
└── ui/                          # Jetpack Compose
    ├── screens/                 # Dashboard, Settings, Onboarding
    ├── components/              # SettingsSection, SettingsSwitch, SettingsItem
    └── theme/                   # Dark theme, radar color palette
```

### Tech Stack

| Component | Version |
|-----------|:-------:|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.7.3 |
| Jetpack Compose | BOM 2025.01.00 |
| Jetpack Glance | 1.1.1 |
| Karoo SDK | 1.1.7 |
| Room | 2.7.0 |
| DataStore | 1.1.2 |
| Target SDK | 35 |
| Min SDK | 26 |

### Build Commands

```bash
./gradlew assembleDebug         # Debug APK
./gradlew assembleRelease       # Release APK (R8 minified)
./gradlew test                  # Unit tests
./gradlew compileDebugKotlin    # Fast compile check
```

---

## Requirements

| Requirement | Specification |
|-------------|---------------|
| **Device** | Hammerhead Karoo 2 or Karoo 3 |
| **Android** | API 26+ |
| **Radar** | ANT+ compatible cycling radar |

---

## License

MIT
