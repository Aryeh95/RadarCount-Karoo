# RadarCount for Karoo

A Hammerhead Karoo extension that counts the vehicles that pass you and
estimates their approach speed from your ANT+ rear radar, then records it all
to the ride FIT file using the same developer fields as the Garmin
[My Bike Radar Traffic](https://github.com/kartoone/mybiketraffic) Connect IQ
field, so rides can be uploaded to [mybiketraffic.com](https://www.mybiketraffic.com/rides/import).

Runs on the Karoo 2 and Karoo 3, with any ANT+ rear radar the Karoo pairs with.
No alerts and no sounds. The radar plumbing started from
[eiRadar](https://github.com/yrkan/eiradar) with everything except counting and
recording removed.

RadarCount is an independent project. It is not affiliated with or endorsed by
MyBikeTraffic or Hammerhead; the names are used only to describe compatibility.

## Data fields

Add any of these to a ride page from the Karoo's data field picker under **RadarCount**.

| Data Field | Shows |
|------------|-------|
| **Radar** | Pass count, vehicle speed and distance side by side in one field, captioned COUNT, REL SPEED or ABS SPEED, and DIST |
| **Vehicles** | Vehicles that have passed you this ride |
| **Vehicles per Hour** | Pass count divided by recording time (paused time excluded). Shows `--` for the first two minutes of a ride |
| **Vehicle Speed** | Speed of the nearest vehicle, with its unit (for example `36mph` or `58km/h`) and a RELATIVE or ABSOLUTE tag underneath. Shows `--` for the first second, until there is enough range history to estimate a speed; a shown `0` means the vehicle is not gaining on you |
| **Vehicle Distance** | Distance to the nearest vehicle, with its unit (for example `148ft` or `45m`) |

Fields look like the Karoo's own: the standard header with icon and name at
the top, the value centred below it at the Karoo's font size, honouring the
field alignment setting (left, centre or right). Text shrinks to fit narrow
fields so nothing is cut off. Text colour follows the device theme and can be
forced in settings. Every field shows `NO RADAR` in grey while no radar is
connected, including when a ride is started without one, so that is never
confused with "no vehicles".

## Settings

Open the RadarCount app on the Karoo and tap Settings.

| Setting | Options |
|---------|---------|
| Units | Karoo profile (default), Metric, Imperial |
| Field colours | Match device (default), Light, Dark |
| Vehicle speed shown | Relative to you (default), or Absolute (relative plus your own speed). Applies to the Vehicle Speed field and the Radar field. |
| Count sensitivity | Strict, Normal (default) or Relaxed. A car counts once it has come within 12 / 20 / 30 m of you, or if it was still closing in when it dropped off the radar inside 40 / 60 / 90 m. The settings screen spells out the rule for the selected option. |
| Reset count when a ride starts | On by default. Off keeps a running total across rides; use Reset count on the status screen to clear it. |

The extension is idle at boot. It only opens the radar, speed and heading
streams while a ride is recording, one of its data fields is on screen, or its
status screen is open, and closes them again afterwards.

Each radar target is tracked individually across packets. A target counts as a
pass when it drops off the radar after either coming within 20 m, or closing in
and being last seen within 60 m, and only if it was seen more than once (the
Karoo's target list jitters, so one-packet blips are ignored). A car that has
come within 20 m is counted on the first packet it is missing from, since the
radar cannot see a car alongside you; it lingers as a ghost for two seconds so
a range that reappears right where it vanished (a radar dropout) re-attaches
without counting again. A car that vanishes farther out waits the full two
seconds, because there a dropout and a pass look alike.

Two kinds of target are deliberately not counted:

- **Followers.** A car that sits behind you at your speed disappears from the
  radar without passing, because the radar only reports targets that are
  closing. A track has to have looked like a pass at some point: the radar
  flagged it as approaching fast, it was closing at 2.5 m/s (about 5.5 mph)
  or more when last seen, or it was seen alongside at 3 m.
- **Turns.** Using the rider's heading, a car that was behind you before you
  turned off a road and vanishes as you turn is taken to have gone straight
  on, and a brief target first seen within 30 m just after a turn is a car
  crossing the radar cone on the road you left. A car on the new road that
  appears mid-turn and passes you is counted normally.

The ride count resets when a ride starts recording, and does not advance
while the ride is paused (including auto-pause), because nothing is written
to the FIT file then and the site would never see those cars.

### Why the device and mybiketraffic.com can disagree

The site counts cars from the file with the same simple rule the Garmin app
uses: any run of radar readings that ends under 10 m is a car. RadarCount is
stricter, so the two can differ by a car or two on a ride:

- A follower that got within 10 m before dropping off is a car to the site
  but not to RadarCount.
- Two cars that overlap on the radar share one range in the file; RadarCount
  writes a marker so the site still sees both, but a car that only ever
  appeared as the second target can be merged.
- Cars that pass while the ride is paused are counted by neither.

In side-by-side recordings the device count has matched a head count where
the Garmin app's did not, so treat the device number as the better one.

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
allows one value per field, the writer makes sure every counted pass produces
exactly one such run:

- A car counted while its run is still open and already inside 10 m gets a
  single 0 on that record, even if the next car is already the nearest target.
- A car whose run already closed below 10 m before the count arrived needs
  nothing; the site has it.
- A car that dropped off the radar farther out gets the Garmin marker: one
  record at 3 m, then one at 0.

(An experiment confirmed the Karoo keeps only the last value when several are
written to one field, so true arrays are not possible.)

Two differences from the Garmin file, both imposed by the Karoo SDK:

- `radar_ranges` and `radar_speeds` hold a single value (the nearest target)
  instead of an 8-element array, because the SDK cannot write arrays.
- `radar_lap` (field 4, lap message) is not written, because the SDK has no
  lap-message API.

The Karoo SDK also does not expose radar target speed, so speeds are estimated
from how fast the range shrinks, as a least-squares slope over the last 3
seconds of samples. The estimate freezes once the car is inside 10 m, because
the last few samples before a pass are the noisiest, so the recorded passing
speed is the approach speed. Treat them as ballpark figures: a car reads `--`
for its first second on the radar, and a car still accelerating toward you
reads a little low.

## Install

RadarCount is not in Hammerhead's extension library yet. The manifest the
library reads is at
`https://raw.githubusercontent.com/Aryeh95/RadarCount-Karoo/main/manifest.json`
and is kept in step with each release by a CI check. Until it is listed:

Download the APK from [Releases](../../releases) and install with
`adb install radarcount-karoo-<version>.apk`, or share it to the Hammerhead companion
app.

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
