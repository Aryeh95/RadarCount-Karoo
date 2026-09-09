#!/usr/bin/env python3
"""Check manifest.json against the version in app/build.gradle.kts.

The manifest is what Hammerhead's extension library serves to riders, so it
tracks the latest *stable* release. On a pre-release build (0.2.13-beta1)
the manifest is expected to still point at the previous stable version, and
this check only verifies that it does.
"""
import json, re, sys

g = open("app/build.gradle.kts").read()
name = re.search(r'versionName = "([^"]+)"', g).group(1)
code = int(re.search(r'versionCode = (\d+)', g).group(1))
m = json.load(open("manifest.json"))

if "-" in name:
    if m["latestVersion"] == name or m["latestVersionCode"] == code:
        print(f"manifest.json points at the pre-release {name}; it must stay on the last stable release")
        sys.exit(1)
    print(f"build is pre-release {name}; manifest stays on {m['latestVersion']}")
    sys.exit(0)

errors = []
if m["latestVersion"] != name:
    errors.append(f"latestVersion {m['latestVersion']} != {name}")
if m["latestVersionCode"] != code:
    errors.append(f"latestVersionCode {m['latestVersionCode']} != {code}")
want = f"/releases/download/v{name}/radarcount-karoo-{name}.apk"
if not m["latestApkUrl"].endswith(want):
    errors.append(f"latestApkUrl does not end with {want}")
if errors:
    print("manifest.json is out of date:\n  " + "\n  ".join(errors))
    sys.exit(1)
print(f"manifest.json matches {name} ({code})")
