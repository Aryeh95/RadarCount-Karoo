# eiRadar

[![License](https://img.shields.io/badge/License-MIT-0d1117?style=flat-square&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Karoo%202%2F3-0d1117?style=flat-square&logo=android&logoColor=white)](https://www.hammerhead.io/)
[![Downloads](https://img.shields.io/github/downloads/yrkan/eiradar/total?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/eiradar/releases)
[![Release](https://img.shields.io/github/v/release/yrkan/eiradar?style=flat-square&color=0d1117&logo=github&logoColor=white)](https://github.com/yrkan/eiradar/releases/latest)
[![Website](https://img.shields.io/badge/Web-eiradar.com-0d1117?style=flat-square&logo=google-chrome&logoColor=00E676)](https://eiradar.com)

Rear vehicle radar alert extension for Hammerhead Karoo. Reads ANT+ radar data and delivers real-time visual and sound alerts — designed to keep your eyes on the road, not the screen.

<p align="center">
  <a href="#features">Features</a> &bull;
  <a href="#installation">Installation</a> &bull;
  <a href="#quick-start">Quick Start</a> &bull;
  <a href="#data-fields">Data Fields</a> &bull;
  <a href="#alerts">Alerts</a> &bull;
  <a href="#settings">Settings</a> &bull;
  <a href="#night-mode">Night Mode</a> &bull;
  <a href="#fit-recording">FIT Recording</a> &bull;
  <a href="#development">Development</a>
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| **3 Data Fields** | Compact, Standard, Full — graphical widgets for any ride screen layout |
| **Multi-channel Alerts** | Visual banner + escalating sound patterns via Karoo speaker |
| **4 Sound Sets** | Classic, Subtle, Urgent, Bike Bell — choose what fits your riding style |
| **Threat Levels** | Approaching, Warning, Critical — color-coded with configurable distance thresholds |
| **Night Mode** | Automatic threshold increase after sunset (GPS-based detection) |
| **Speed Gate** | Suppress minor alerts when stopped or slow — critical alerts always fire |
| **Alert Cooldown** | Smart repeat delay prevents alert fatigue without missing new threats |
| **Escalation Bypass** | Higher threat levels fire immediately, ignoring cooldown |
| **FIT Recording** | Threat level, vehicle count, and nearest distance written to ride file at 1Hz |
| **Ride Statistics** | Per-ride session stats: alert counts, closest approach, max vehicles, threat time |
| **Quick Mute** | Toggle alerts via physical button/remote — data fields keep updating |
| **Screen Wake** | Wake Karoo screen on critical threats or any threat |
| **Imperial/Metric** | Automatic unit detection from Karoo profile |
| **Fully Offline** | No accounts, no cloud, no internet, no phone pairing |
| **Open Source** | MIT licensed, free forever |

---

## Installation

### Download APK

Get the latest release from [GitHub Releases](https://github.com/yrkan/eiradar/releases/latest) or [eiradar.com](https://eiradar.com).

```bash
# Install via ADB
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

## Quick Start

### 1. Pair Radar

Connect your ANT+ radar in Karoo **Settings > Sensors**.

### 2. Add Data Field

1. Open **Profiles** on Karoo
2. Edit a profile > Add data page
3. Select **More Data** > **eiRadar**
4. Choose a layout: Radar (S), Radar (M), or Radar (L)

### 3. Ride

Alerts fire automatically when vehicles approach from behind. The first launch shows a quick onboarding guide.

---

## Data Fields

Three graphical data types built with Jetpack Glance. Each updates at 1Hz with a fresh RemoteViews render per cycle.

| Data Field | Karoo Name | Content |
|------------|:----------:|---------|
| **Compact** | Radar (S) | Vehicle count, colored by threat level |
| **Standard** | Radar (M) | Vehicle count + nearest distance + status label |
| **Full** | Radar (L) | Threat label + vehicle count + distance + status message |

All widgets feature a colored status bar at the top (green/orange/red) and a rounded dark background. When alerts are muted, the status bar turns grey and the label shows "MUTED" — radar data continues to display normally.

### Widget States

| State | S display | M/L display |
|-------|:---------:|-------------|
| Not connected | `—` grey | "No radar" |
| Connecting | `...` grey | "Connecting" |
| Clear | `OK` green | "All clear" / "Road is clear" |
| Threat | count, colored | count + distance in meters/feet |
| Connection lost | `!` grey | "Connection lost" |

### Threat Colors

| Color | Level | Meaning |
|:-----:|-------|---------|
| Grey | Disconnected | Radar not paired, connecting, or connection lost |
| Green | Clear | No vehicles detected |
| Orange | Approaching / Warning | Vehicle detected, closing distance |
| Red | Critical | Vehicle very close |

---

## Alerts

Two independent alert channels. Each can be enabled or disabled separately with a master switch to control all at once.

### Channels

| Channel | What it does | Default |
|---------|-------------|:-------:|
| **Banner** | Karoo in-ride visual alert overlay with auto-dismiss | On |
| **Sound** | Beep patterns via Karoo speaker (4 sound sets) | On |

### Sound Sets

| Set | Character |
|-----|-----------|
| **Classic** | Traditional beep patterns — single, double, triple |
| **Subtle** | Quieter, gentler tones for less intrusive alerts |
| **Urgent** | Loud, rapid patterns that demand attention |
| **Bike Bell** | Musical tones using note frequencies |

### Alert Escalation

Alerts follow the threat level hierarchy. When a threat escalates (e.g. Approaching to Warning, or Warning to Critical), the new alert fires immediately — cooldown is bypassed. De-escalation respects the normal cooldown.

### Auto-Dismiss Timing

| Level | Banner duration |
|-------|:--------------:|
| Critical | 5 seconds |
| Warning | 4 seconds |
| Approaching | 2 seconds |
| Clear | 1.5 seconds |

### All-Clear Chime

Optional confirmation sound when all vehicles have passed. Useful as an "all safe" signal before lane changes or turns.

### Quick Mute (BonusAction)

Assign "Toggle Radar Alerts" to a physical button or remote in **Karoo Settings > Controls**. One press mutes all alert channels (sound, banner). Press again to re-enable. Ideal for group rides where nearby cyclists trigger false alerts.

- Data fields continue showing live radar data with a "MUTED" indicator
- FIT recording and statistics continue normally
- **Auto-unmutes when the ride ends** — the next ride always starts with alerts enabled

### Safety Design

- **Critical alerts always fire** regardless of speed gate or other suppression
- Repeat delay prevents alert fatigue without missing genuinely new threats
- Escalation bypass ensures worsening situations are immediately communicated
- Quick mute auto-resets on ride end — no risk of starting a ride with alerts off

---

## Settings

All settings are accessible from the eiRadar app on Karoo. Tap any value to cycle through options.

### Alert Channels

| Setting | Options | Default |
|---------|---------|:-------:|
| Enable alerts | On / Off | On |
| Banner | On / Off | On |
| Sound | On / Off | On |
| Sound set | Classic / Subtle / Urgent / Bell | Classic |
| All-clear sound | On / Off | On |

### Detection Thresholds

| Setting | Options | Default |
|---------|---------|:-------:|
| Approaching | 100 / 125 / 150 / 175 / 200m | 100m |
| Warning | 30 / 40 / 50 / 60 / 70m | 50m |
| Critical | 10 / 15 / 20 / 25 / 30m | 20m |

The radar detects vehicles up to ~140m. The default 100m approaching threshold leaves a 40m "silent awareness" zone where the widget updates but no alert fires — this reduces alert fatigue on busy roads.

### Behavior

| Setting | Options | Default |
|---------|---------|:-------:|
| Repeat delay | 3s / 5s / 8s / 12s | 5s |
| Min speed | Off / 3 / 5 / 8 km/h | Off |
| Wake screen | Never / Critical only / Always | Critical only |

> **Min speed (speed gate):** Only suppresses Approaching alerts below the set speed. Warning and Critical alerts always fire — a stopped cyclist at a traffic light is the most vulnerable.

### Reset to Defaults

Available in the settings screen. Clears all preferences and restores factory settings with a confirmation dialog.

---

## Night Mode

eiRadar automatically detects sunset and sunrise using Karoo's GPS-based time data. No manual configuration needed.

When night mode is active, all distance thresholds are increased by **33%** to provide earlier warnings in low-visibility conditions:

| Threshold | Day | Night (+33%) |
|-----------|:---:|:------------:|
| Approaching | 100m | 133m |
| Warning | 50m | 66m |
| Critical | 20m | 26m |

Night mode status is visible on the dashboard screen. The multiplier applies automatically — no settings to change.

---

## FIT Recording

eiRadar writes radar data to the Karoo ride FIT file at 1Hz using developer fields. This data appears alongside your standard ride metrics in any FIT-compatible analysis tool.

| Developer Field | Type | Description |
|----------------|:----:|-------------|
| `radar_threat_level` | uint8 | 0=Clear, 1=Approaching, 2=Warning, 3=Critical |
| `radar_vehicle_count` | uint8 | Number of vehicles currently detected (0–8) |
| `radar_nearest_distance` | uint16 | Distance to nearest vehicle in meters |

Recording starts and stops automatically with ride recording.

---

## Ride Statistics

eiRadar tracks per-ride session statistics, stored locally in a Room database:

| Metric | Description |
|--------|-------------|
| Session duration | Total ride time |
| Total alerts | Number of alerts fired across all levels |
| Approaching alerts | Count of approaching-level alerts |
| Warning alerts | Count of warning-level alerts |
| Critical alerts | Count of critical-level alerts |
| Max vehicles | Peak simultaneous vehicle count during the ride |
| Closest approach | Nearest vehicle distance recorded (meters) |
| Threat time | Cumulative time spent under any threat level |

Statistics are shown after ride completion (configurable). All data stays on-device.

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

The Karoo receives ANT+ radar data natively. eiRadar reads it from the Karoo SDK's sensor stream — up to 8 vehicles tracked simultaneously with per-vehicle distance data.

---

## Requirements

| Requirement | Details |
|-------------|---------|
| **Device** | Hammerhead Karoo 2 or Karoo 3 |
| **Radar** | ANT+ compatible cycling radar |
| **Internet** | Not required |
| **Account** | Not required |

---

## Development

### Architecture

```
io/github/ykn/variaradarpro/
├── VariaRadarExtension.kt       # KarooExtension entry point, FIT recording, BonusAction
├── MainActivity.kt              # Compose UI host (settings, onboarding, dashboard)
│
├── engine/                      # Core logic
│   ├── RadarEngine.kt           # ANT+ radar data → StateFlow<WidgetState>
│   ├── AlertManager.kt          # Threat evaluation + alert dispatch + night mode
│   ├── AlertThrottler.kt        # Per-level cooldown with escalation bypass
│   ├── SoundEngine.kt           # Karoo PlayBeepPattern integration (4 sound sets)
│   ├── NightModeManager.kt      # Sunrise/sunset detection via Karoo GPS data
│   └── StatisticsCollector.kt   # Per-ride session stats aggregation
│
├── datatypes/glance/            # Karoo data fields (Jetpack Glance)
│   ├── GlanceDataType.kt        # Base: fresh RemoteViews per 1Hz cycle, mute state
│   ├── GlanceComponents.kt     # Shared: DataFieldContainer, StatusBar, ValueText, LabelText
│   ├── SmallWidgetGlanceDataType.kt   # Radar (S) — compact
│   ├── MediumWidgetGlanceDataType.kt  # Radar (M) — count + distance
│   └── LargeWidgetGlanceDataType.kt   # Radar (L) — full info
│
├── data/                        # Persistence
│   ├── PreferencesRepository.kt # DataStore settings (singleton)
│   ├── models/                  # PresetSettings, AlertSettings, ThreatLevel,
│   │                            # WidgetState, BuiltInSoundSet, ScreenWakePolicy
│   └── database/                # Room database (ride statistics)
│
└── ui/                          # Jetpack Compose
    ├── screens/                 # DashboardScreen, SettingsScreen, OnboardingScreen
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
./gradlew assembleRelease       # Release APK (R8 minified + shrunk)
./gradlew test                  # Unit tests (JUnit 5)
./gradlew compileDebugKotlin    # Fast compile check
```

### Testing

Tests use JUnit 5 with `@DisplayName`, `@Nested`, and `@ParameterizedTest` patterns. Assertions use [Google Truth](https://truth.dev/). Mocking with [MockK](https://mockk.io/). Coroutine testing with [Turbine](https://github.com/cashapp/turbine).

```bash
./gradlew test
```

---

## Privacy

eiRadar does not collect, store, or transmit any personal data. Everything runs on-device. Full [privacy policy](https://eiradar.com/privacy.html).

---

## License

MIT
