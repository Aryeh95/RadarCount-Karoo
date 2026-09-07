# RadarCount for Karoo

A Hammerhead Karoo extension that counts the vehicles that pass you and
estimates their approach speed from your ANT+ rear radar, then records it all
to the ride FIT file using the same developer fields as the Garmin
[My Bike Radar Traffic](https://github.com/kartoone/mybiketraffic) Connect IQ
field, so rides can be uploaded to [mybiketraffic.com](https://www.mybiketraffic.com/rides/import).

No alerts and no sounds. The radar plumbing started from [eiRadar](https://github.com/yrkan/eiradar)
with everything except counting and recording removed.

RadarCount is an independent project. It is not affiliated with or endorsed by
MyBikeTraffic or Hammerhead; the names are used only to describe compatibility.

## Data fields

Add any of these to a ride page from the Karoo's data field picker under **RadarCount**.

| Data Field | Shows |
|------------|-------|
| **Radar** | Pass count, approach speed and closest distance in one field. The layout adapts to the field size: one row when small, count beside speed and distance at half height, everything including lap and status at full height |
| **Vehicles** | Vehicles that have passed you this ride, with the current lap count underneath |
| **Vehicle Speed** | Relative speed of the nearest vehicle, with its absolute speed (relative + your speed) underneath |
| **Vehicle Distance** | Distance to the nearest vehicle, with how many vehicles are behind you |

Fields use the Karoo's standard header (icon and name at the top) and draw no
background of their own, so they look like the built-in fields in both the
light and dark theme. Text colour follows the device theme automatically and
can be forced in settings.

## Settings

Open the RadarCount app on the Karoo and tap Settings.

| Setting | Options |
|---------|---------|
| Units | Karoo profile (default), Metric, Imperial |
| Field colours | Match device (default), Light, Dark |
| Vehicle speed shown | Relative to you (default), or Absolute (relative plus your own speed). Applies to the Vehicle Speed field and the Radar combo; the other value shows on the small line where there is room. |
| Count sensitivity | Strict (12 m / 40 m), Normal (20 m / 60 m), Relaxed (30 m / 90 m). The first number is how close a car must come to count outright; the second is how close a car that was still closing in may drop off the radar and still count. |
| Reset count when a ride starts | On by default. Off keeps a running total across rides; use Reset count on the status screen to clear it. |
| Reset lap count on each lap | On by default |
| Show lap count on fields | On by default |

The extension is idle at boot. It only opens the radar and speed streams while
a ride is recording, one of its data fields is on screen, or its status screen
is open, and closes them again afterwards.

Each radar target is tracked individually across packets. A target counts as a
pass when it drops off the radar after either coming within 20 m, or closing in
and being last seen within 60 m, and only if it was seen more than once (the
Karoo's target list jitters, so one-packet blips are ignored). The ride count
resets when a ride starts recording; the lap count resets on every Karoo lap.

## FIT recording

Written at 1 Hz while a ride is recording.

| # | Field | Message | Type | Value |
|:-:|-------|:-------:|:----:|-------|
| 0 | `radar_ranges` | record | sint16 | Range to the nearest vehicle in metres. `-1` while the radar is disconnected. |
| 1 | `radar_speeds` | record | uint8 | Estimated closing speed of the nearest vehicle in m/s. `255` while the radar is disconnected. |
| 2 | `radar_current` | record | uint16 | Running count of vehicles passed so far |
| 3 | `radar_total` | session | uint16 | Total vehicles passed for the ride |
| 5 | `passing_speed` | record | uint8 | Relative speed of the nearest vehicle in your units |
| 6 | `passing_speedabs` | record | uint8 | Absolute speed of the nearest vehicle in your units |
| 7 | `radar_threat_level` | record | enum | Karoo threat level 0-3 |
| 8 | `radar_vehicle_count` | record | uint8 | Vehicles currently detected |
| 9 | `radar_nearest_distance` | record | uint16 | Nearest vehicle in metres (omitted when none) |
| 10-12 | `radar_range_2` .. `radar_range_4` | record | uint16 | Ranges of the next three targets, when present |

mybiketraffic.com identifies a car as a run of consecutive non-zero `radar_ranges`
records that ends below 10 m and is followed by a 0. Because the Karoo SDK only
allows one value per field, our nearest-target value would already show the next
car by the time a pass is counted, so on each counted pass the extension writes
one record with the range at 3 m and then one record at 0 before resuming the
live value. That reproduces the Garmin signature the importer expects. (An
experiment confirmed the Karoo keeps only the last value when several are
written to one field, so true arrays are not possible.)

Two differences from the Garmin file, both imposed by the Karoo SDK:

- `radar_ranges` and `radar_speeds` hold a single value (the nearest target)
  instead of an 8-element array, because the SDK cannot write arrays.
- `radar_lap` (field 4, lap message) is not written, because the SDK has no
  lap-message API.

The Karoo SDK also does not expose radar target speed, so speeds are estimated
from consecutive range samples. Treat them as ballpark figures.

## Install

Download the APK from [Releases](../../releases) and install with
`adb install radarcount-karoo-<version>.apk`, or share it to the Hammerhead companion
app. Works on Karoo 2 and Karoo 3 with any ANT+ radar the Karoo pairs with.

The Karoo shows a "not verified by Hammerhead" warning for every sideloaded
extension; that is expected. Updates install over the previous version and
keep your settings and field placements.

## Privacy

RadarCount has no network access and no accounts. It reads radar, speed and
ride state from the Karoo and writes radar fields into the ride's FIT file on
the device. Nothing leaves the Karoo unless you upload the ride yourself.

## Feedback

Bugs, ideas and ride files that counted wrong are welcome as
[issues](../../issues). A FIT file from the ride plus what you saw on the road
is the most useful report.

## Release signing

Releases built by GitHub Actions are signed with a permanent keystore held in
the repository secrets `SIGNING_KEYSTORE_B64` (the `.jks` file, base64) and
`SIGNING_STORE_PASSWORD`, so each release installs over the previous one. A
build without those secrets is signed with a throwaway debug key and must be
installed after uninstalling the old version.

## Build

Needs Android Studio (or JDK 17 + the Android SDK) and access to the Karoo SDK
on GitHub Packages. Put a GitHub personal access token with `read:packages`
in `~/.gradle/gradle.properties`:

```
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Then:

```
./gradlew testDebugUnitTest assembleRelease
```

## Credits and license

Built on the radar and Karoo plumbing of [eiRadar](https://github.com/yrkan/eiradar)
by yrkan, and on the FIT field layout of [My Bike Radar Traffic](https://github.com/kartoone/mybiketraffic)
by Brian Toone. Not affiliated with Hammerhead or mybiketraffic.com.

MIT license, see [LICENSE](LICENSE).
