#!/usr/bin/env python3
"""Fail if manifest.json does not match the version in app/build.gradle.kts."""
import json, re, sys
g = open("app/build.gradle.kts").read()
name = re.search(r'versionName = "([^"]+)"', g).group(1)
code = int(re.search(r'versionCode = (\d+)', g).group(1))
m = json.load(open("manifest.json"))
errors = []
if m["latestVersion"] != name: errors.append(f"latestVersion {m['latestVersion']} != {name}")
if m["latestVersionCode"] != code: errors.append(f"latestVersionCode {m['latestVersionCode']} != {code}")
want = f"/releases/download/v{name}/radarcount-karoo-{name}.apk"
if not m["latestApkUrl"].endswith(want): errors.append(f"latestApkUrl does not end with {want}")
if errors:
    print("manifest.json is out of date:\n  " + "\n  ".join(errors)); sys.exit(1)
print(f"manifest.json matches {name} ({code})")
