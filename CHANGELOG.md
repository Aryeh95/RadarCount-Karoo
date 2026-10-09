# Changelog

Release notes for RadarCount for Karoo. Each GitHub release carries the
matching section below as its description.

## 0.4.0 — 2026-10-09

Every field is redrawn so it looks the same on the Karoo 2 and the Karoo 3,
the Vehicle Speed and Radar fields can keep a passed car's speed on screen,
and each field gets its own settings card with a live preview.

### Added
- **Speed after a pass** (#1). The Vehicle Speed and Radar fields can each
  keep the approach speed of the car that just passed on screen for 3, 5 or
  10 seconds (off by default, set on each field's card). The held speed is
  drawn in grey and always marked PASSED, whatever the caption and tag
  settings, so it cannot be mistaken for a car still coming. A car whose speed
  is measured replaces it at once, and a later pass with no measured speed
  ends it. A car seen for under two seconds has no measured speed, so nothing
  is held for it. The Karoo's page-editor preview shows the passed frame too
  while the hold is on.
- **A settings card per field.** The Field tab has a card for each of the five
  fields. Opening one shows the field as it looks on your ride pages, drawn by
  the field's own code at the size it last had there, and that field's
  settings.
- **Header on or off for every field.** Off gives the value the whole field.
- **Updates for sideloaded installs.** From this version on the app carries
  the address of its update manifest, so the Karoo offers new versions, with
  screenshots and release notes, even when RadarCount was installed with adb.
  Updating from 0.3.x to 0.4.0 still needs one manual install.

### Changed
- **Single fields draw their own one-line header.** Vehicles, Vehicle Speed,
  Vehicle Distance and Vehicles per Hour draw the field's icon and name
  themselves, in the Karoo's header style, instead of using the Karoo's
  header strip. A long name such as VEHICLES PER HOUR is set a little smaller
  to stay on one line instead of wrapping onto a second, and the value gets
  that line's height.
- **New icons.** Vehicle Speed has a speedometer like the Karoo's own Speed
  icon, and Vehicle Distance an icon for the range to the car behind. Both
  show in the field header, on the settings card and in the Karoo's field
  picker.
- **Vehicle Speed shows absolute speed by default**, the vehicle's road speed,
  and the RELATIVE / ABSOLUTE line under it is gone, so the speed is drawn at
  full size. If you choose relative speed, the field's header reads VEHICLE
  REL SPEED instead.
- **Speed shown is set per field**, on the Vehicle Speed and Radar cards
  instead of the Setup tab, so each card's preview shows the choice at once
  and the two fields can differ. Both start from the setting you had.
- **Screens in the Karoo's style.** A neutral grey background with white
  cards, and the Karoo's slate back button with a thin arrow.
- Digits no longer have a white or black box behind them; the Karoo's own
  field colour shows through, as with its built-in fields.

### Fixed
- **Radar field digits cut off at the bottom on the Karoo 2.** Android 8 on
  the Karoo 2 lays text out differently from Android 12 on the Karoo 3, so the
  bottom of the digits ran past the field. Every field is now drawn as an
  image from exact glyph measurements, so the digits are placed to the pixel
  and always fit, identically on both devices.
- **Vehicle Speed drawn small in mph.** The p of mph used to cost the digits
  its height twice; the speed now uses the room the text really needs.
- The Radar field's active layout ends two seconds after the last vehicle
  even when nothing else changes; before, it could stay until the next
  update.

## 0.3.1 — 2026-10-02

### Fixed
- **Speed flashed far too high when a car first appeared.** A car that showed
  up at 68 m read 80 mph for its first second, then settled to the 50s. The
  closing speed is a slope fitted to the track's range history, and it was
  shown after one second of history. In that first second a new track has a
  handful of samples, quantised to 3 m bins and delivered in bursts, and two
  bin steps landing close together fit a slope of 20 m/s. The speed is now
  shown only once a track has two seconds of history, by which time the slope
  has settled. The Vehicle Speed field and the Radar field show the dash a
  second longer when a car first appears.

## 0.3.0 — 2026-10-01

The Radar combo field is redrawn for legibility in the smallest field size,
and the app's own screens now look like the Karoo's.

### Radar field
- **Two layouts by state.** With nothing on the radar the field shows the
  vehicle count alone, large. When a vehicle is on the radar it shows speed
  and distance large with the count small beside them, and holds that for two
  seconds after the vehicle is gone so a pass ends with the new count showing.
  The old three-cell layout is still available for either state.
- **Draws its own header.** The field no longer uses the Karoo's caption strip;
  it draws a matching RADAR header itself and can turn it off, which gives the
  numbers the whole tile. Captions (VEHICLES, MPH, FT) can be turned off too.
  With both off the numbers are about twice the size of 0.2.17's.
- **Units as captions.** MPH or KPH and FT or M sit above the digits instead of
  being glued to them, so the digits get the width. A toggle restores the old
  style.
- **Sized to the tile.** The field reads the exact size the Karoo gives it and
  fits the digits to both width and height, so every field size from half
  width up gets the largest digits that fit. The page-editor preview cycles
  between the two states and uses wide sample digits (45 mph, 570 ft) so it
  shows the worst case.

### App
- **Tabbed screens** in the style other Karoo extensions use: Status, Setup,
  Field and, once unlocked, Dev. Dropdowns for the choice settings, switches on
  the left, floating back button.
- **Field tab** holds the Radar field options with a live preview drawn at the
  field's true size and with the field's own sizing code.
- **Light theme** matching the Karoo, replacing the orange-on-black left over
  from the project this one started from.
- **Dev tab** shows the field size the Karoo reports and can tint the field's
  real bounds, for layout work.

### Unchanged
- Counting is exactly as in 0.2.17. The single-value fields (Vehicles, Vehicle
  Speed, Vehicle Distance, Vehicles per Hour) are unchanged.

## 0.2.17 — 2026-09-18

Counting is tightened to the rule mybiketraffic.com uses, after a side-by-side
video comparison against the Garmin app showed where the extra counts came
from. Expect a few fewer counts per ride than 0.2.16, all of them cars that
never came alongside.

### Changed
- **A car must come within 9 m to count.** Previously a car counted once it
  had been within 20 m, or if it was still closing when it dropped off the
  radar inside 60 m. Both rules counted cars that never passed: a car that
  stopped in the queue behind you at 15 m, one that followed at 30 m for a
  while and then turned off, and one lost at 37 m because you turned. Nine
  metres is where the rear beam loses a car as it draws level with your back
  wheel, and it is the distance the site uses, so device and site now agree
  on the rule.
- **Count sensitivity** options are now Strict 6 m, Normal 9 m and Relaxed
  12 m, one radar range bin apart. Normal is the default and matches the site.
  The old "still closing within N m" half of the setting is gone.

### Fixed
- **Long vehicles counted twice.** A semi or trailer's radar reading wobbles
  between the 3 m and 6 m bins as its body goes by. The guard added in 0.2.15
  to stop a counted car's ghost swallowing the next car in the queue refused
  the reading one bin farther back, so the rest of the vehicle started a new
  track and counted again. The ghost now takes back a reading one bin farther
  out when it arrives within half a second of the last echo; after a longer
  gap it is still treated as the next car.
- **A ghost could steal the final reading of the car behind it.** When two
  cars pass in a line, the counted car's ghost at 3 m matched the second car's
  own 3 m reading in preference to that car's live track, so the second car
  looked as if it never came alongside. Live tracks are now matched before
  ghosts. Under the old 20 m rule this was masked; under the 9 m rule it
  would have lost the second car.

## 0.2.16 — 2026-09-15

### Fixed
- **Turn detection was dead in 0.2.15.** The change that ignored turns while
  the heading history filled at ride start compared against a window that is
  trimmed on every update, so it could never fill. No turn was detected on any
  ride, and the rules that stop a car behind you being counted as you turn off
  the road never fired. Turns are now judged once six seconds have passed since
  the first heading of the ride.
- A car whose first echo lands just outside the alongside range and whose next
  echo lands inside it, then leaves the beam, was rejected for having too few
  samples. It now counts, as it does on the Garmin.

### Changed
- The per-track trace file is off by default. Tap the version line in Settings
  five times to reveal a **Developer** section with the switch, and a way to
  hide the section again.

## 0.2.15 — 2026-09-11

Counting now follows the same rule as mybiketraffic.com, so the device and the
site agree: a car that comes within 10 m and then drops off the radar is a
pass. This trades a rare false positive (a car that settles under 10 m back
and goes quiet) for a class of misses that was common in traffic.

### Fixed
- **The lead car of a queue was not counted.** A car following at your speed is
  invisible to a Doppler radar. When it finally pulls out it is visible for a
  fraction of a second at 3–6 m before it leaves the beam, and the two-sample
  minimum discarded it while the car behind it was counted. A car that appears
  inside the alongside range now counts on a single sample.
- After a count, the next car in a tight queue could be absorbed as a "dropout"
  of the car just counted. A counted car's ghost now only re-attaches a target
  at or nearer its last range.

### Changed
- Alongside is 9 m rather than 6 m, matching the site's under-10 m rule.

### Added
- Diagnostic fields in the FIT file (`radar_dbg_*`): tracker resets, heading
  as fed, packets per second, turns detected, and rejections by rule.
- A per-track trace file, one line per decision, in the app's private storage.

## 0.2.14 — 2026-09-11

### Fixed
- **A slow pass was discarded as a follower.** The rule that stops a car
  sitting behind you from being counted rejected a car that crept up at your
  own speed and then overtook, because it never closed fast enough and the
  radar never flagged it. On a Doppler radar a genuine slow pass carries no
  speed and no threat flag; the one thing it cannot avoid is getting close.
  A car seen within 6 m now counts whatever its speed. Six metres is where the
  rear beam loses an overtaking car; 3 m was a guess.

## 0.2.13 — 2026-09-09

### Fixed
- The Vehicle Speed field showed **0** for the first second a car was on the
  radar, before any speed could be estimated. It now shows **--** until there
  is enough range history; a shown 0 always means the car is holding station.
- Absolute speed read 0 for a car matching your own speed. It now adds your
  speed whenever the relative speed is known.

### Added
- Extension manifest, icon and screenshots for Hammerhead's extension library,
  with a CI check that the manifest matches the build.
- README: why the device count and mybiketraffic.com can disagree.

Verified against a Garmin Edge running the MyBikeTraffic field on the same
ride: both devices counted 8, and the estimated speed was within 1.2 mph of
the true range rate on every clean approach.

## 0.2.12 — 2026-09-08

### Counting
- A car that has come within the close threshold is counted on the first
  packet it is missing from, about a second sooner than before, and lingers as
  a ghost so a radar dropout does not count it twice.
- A car that sits behind you at your speed and then drops off the radar is no
  longer counted. A pass must have looked like one: the radar flagged it as
  approaching fast, it was closing at 2.5 m/s or more when last seen, or it
  was seen alongside.
- Your heading is used to tell a turn from a pass: a car behind you before a
  turn that vanishes as you turn went straight on, and a brief target first
  seen close right after a turn is a car crossing the beam on the road you
  left. A car on the new road that passes as you merge is counted.
- Passes are not counted while the ride is paused, matching the Garmin field
  and the FIT file, which records nothing during a pause.

### Speed
- The closing-speed estimate freezes once the car is inside 10 m, so the
  recorded passing speed is the approach speed rather than the noisy last
  sample.
- Speed values carry their unit (36mph, 58km/h), and the Vehicle Speed field
  and Radar combo say whether they are relative or absolute.

### Fields
- New **Vehicles per Hour** field: passes over recording time.
- Every field shows **NO RADAR** while no radar is connected, including a
  ride started without one.
- Text shrinks to fit narrow fields instead of being cut off.

### FIT recording
- The record writer is now a standalone, unit-tested class. Three cases that
  made mybiketraffic.com miss cars are fixed: a count arriving right after a
  marker, two cars counted in the same second, and a count arriving one second
  after a forced closing 0.

### Settings
- The sensitivity setting spells out its rule in plain words for the chosen
  option. The status screen scrolls. The lap count and its settings are gone.

## 0.2.11 — 2026-09-08

### Changed
- Data fields are styled like the Karoo's built-in ones: the standard header,
  the Karoo's font size, and the profile's alignment setting.
- Vehicle speed is estimated with a least-squares slope over the last three
  seconds of range, rather than the radar's own 3 m/s-quantised value. Mean
  error against the true range rate on test rides: 0.0 mph.
- New **Speed** setting: show a vehicle's speed relative to you, or its road
  speed.

### Fixed
- The FIT pass marker is skipped whenever the car's run of ranges already
  closed under 10 m, so the site does not count the car twice.

### Housekeeping
- License notice in the app, unused WAKE_LOCK permission dropped, README
  privacy section and credits.

## 0.2.10 — 2026-09-07

- Field layout follows the Karoo's own data fields more closely: content
  anchored at the top and sized to the space under the header.
- Release APKs are named with the version number.

## 0.2.9 — 2026-09-07

- **Speed** setting (relative or absolute) for the speed fields; values shown at
  the Karoo's own size.
- The FIT pass marker is not written when the car's run already closed under
  10 m.
- Radar combo field captions the speed column SPEED instead of the unit.

## 0.2.7 — 2026-09-07

- Radar combo field captions the speed column SPEED instead of the unit.
- Public release housekeeping: license notice, unused WAKE_LOCK permission
  dropped, README privacy section and credits.

## 0.2.6 — 2026-09-07

- Radar combo field always shows its captions and shrinks values to fit.
- Field text is sized from the space left under the Karoo header.

## 0.2.5 — 2026-09-07

- Fields renamed to **Vehicles**, **Vehicle Speed** and **Vehicle Distance**.

## 0.2.4 — 2026-09-07

- Shorter field names so headers fit on one line; values centred under the
  header.

## 0.2.3 — 2026-09-07

First release signed with the permanent key. Installs update in place from
here on.

- Data fields use the Karoo's standard header, with the value below it, and
  text sized from the cell's real dimensions.
- FIT: each counted pass is written with the Garmin field's pass signature
  (3 m then 0) so mybiketraffic.com counts it.
- Releases are built and signed in CI from a keystore held in secrets.
