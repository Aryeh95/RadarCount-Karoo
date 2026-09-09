# Upload probe

Before the direct-upload feature (0.3.0) can be designed, a few things
about the Karoo itself need answering: whether an extension can make
its own network connections, how big a body it can send, whether it can
read the ride files, whether it has a browser for OAuth, and whether it
can reach the network from the service right after a ride ends.

The `0.3.0-probe` builds answer those against a stand-in server. Nothing
in them is meant to ship.

## Setting up

On a laptop with Python 3:

    python3 tools/probe_server.py

In a second terminal, expose it with a Cloudflare quick tunnel (no
account needed):

    cloudflared tunnel --url http://localhost:8080

It prints a `https://<random>.trycloudflare.com` URL. That is the server
URL to enter on the Karoo. The tunnel gives the Karoo a real HTTPS
endpoint whether it is on wifi or tethered through the companion app.

If the Karoo and laptop are on the same wifi, `http://<laptop-ip>:8080`
also works and skips the tunnel.

## On the Karoo

Sideload the probe APK, open RadarCount → Settings → **Connection test
(upload probe)**, check the server URL, and tap **Run checks**. To avoid
typing on the device, set the URL over adb:

    adb shell am start -d "radarcount://probe?url=https://<your-tunnel>.trycloudflare.com"

(Probe builds also carry a default URL baked in at build time.) Grant
storage access if asked. Each check reports ✓ / ✗ with details.

| Check | What it tells us |
|---|---|
| Environment | Android version and which network the Karoo is using (wifi, bluetooth tether, …) |
| Direct GET /ping | Can the extension make its own HTTP connection, bypassing karoo-ext |
| karoo-ext POST 50 KB | The SDK's HTTP path works at all |
| karoo-ext POST 150 KB | Expected ✗ — confirms the SDK's 100 KB cap is enforced client-side |
| Direct POST 512 KB raw / gzip | A full-size ride file can go through the direct path, and how fast |
| FIT files on /sdcard | The extension can read `/sdcard/FitFiles/` and find the newest ride |
| Upload newest FIT | End-to-end: real ride file, multipart, MD5 verified by the server |
| Browser available | Something on the Karoo handles `https://` links |
| Ride-end ping | See below |

Then two separate buttons:

- **Browser login** — opens the fake login page in whatever browser the
  Karoo has. Tap *Approve* there. RadarCount should come back to the
  front on its own and the check should turn ✓ ("redirect received,
  token exchange HTTP 200"). If the Karoo has no browser, this fails at
  the first step and the device-code flow is the only option.
- **Device code** — shows a code like `WFB-7SR`. On a phone, open the
  `/activate` URL shown (the tunnel URL + `/activate`), type the code,
  tap Approve. The Karoo polls and turns ✓ within a few seconds.

**Ride-end ping** cannot be triggered from the screen. With the server
URL saved, record a short ride and end it. The service fires a GET at
`/ping?event=ride_end` the moment the ride goes idle, and the result
shows up the next time the checks are run (and in the server log). This
answers whether uploads can happen automatically at ride end or only
when the app is opened.

## Reading the results

Everything the server sees is printed to its terminal as JSON lines and
also available at `<server-url>/log`. Copy that log along with a photo
or note of the Karoo's ✓/✗ list.

## Removing it

Uninstall and reinstall the release build from the extension library.
The probe build uses the same signing key, so installing the next
release over it also works.
