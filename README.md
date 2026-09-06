# RadarCount for Karoo

A Hammerhead Karoo extension that counts the vehicles that pass you and
estimates their approach speed from your ANT+ rear radar, then records it all
to the ride FIT file using the same developer fields as the Garmin
[My Bike Radar Traffic](https://github.com/kartoone/mybiketraffic) Connect IQ
field, so rides can be uploaded to [mybiketraffic.com](https://www.mybiketraffic.com/rides/import).

No alerts, no sounds, no settings. The radar plumbing started from [eiRadar](https://github.com/yrkan/eiradar)
with everything except counting and recording removed.

RadarCount is an independent project. It is not affiliated with or endorsed by
MyBikeTraffic or Hammerhead; the names are used only to describe compatibility.

## Data fields

Add any of these to a ride page from the Karoo's data field picker under **RadarCount**.

| Data Field | Shows |
|------------|-------|
| **Vehicle Count** | Vehicles that have passed you this ride, with the current lap count underneath |
| **Approach Speed** | Relative speed of the nearest vehicle, with its absolute speed (relative + your speed) underneath |
| **Closest Vehicle** | Distance to the nearest vehicle, with how many vehicles are behind you |

Units follow your Karoo profile (km/h and metres, or mph and feet).

The extension is idle at boot. It only opens the radar and speed streams while
a ride is recording, one of its data fields is on screen, or its status screen
is open, and closes them again afterwards.

A vehicle is counted when it drops off the radar after having come within 20 m,
the same rule the Garmin field uses. The ride count resets when a ride starts
recording; the lap count resets on every Karoo lap.

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

Two differences from the Garmin file, both imposed by the Karoo SDK:

- `radar_ranges` and `radar_speeds` hold a single value (the nearest target)
  instead of an 8-element array, because the SDK cannot write arrays.
- `radar_lap` (field 4, lap message) is not written, because the SDK has no
  lap-message API.

The Karoo SDK also does not expose radar target speed, so speeds are estimated
from consecutive range samples. Treat them as ballpark figures.

## Install

Download the APK from [Releases](../../releases) and install with
`adb install radarcount-karoo.apk`, or share it to the Hammerhead companion
app. Works on Karoo 2 and Karoo 3 with any ANT+ radar the Karoo pairs with.

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

## License

MIT, same as eiRadar. See [LICENSE](LICENSE).
