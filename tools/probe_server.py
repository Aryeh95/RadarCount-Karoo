#!/usr/bin/env python3
"""
Stand-in for mybiketraffic.com while the Karoo upload feature is probed.

Answers the endpoints the extension's "Connection test" screen exercises:
  GET  /ping                      liveness, logs query string (ride-end pings land here)
  POST /api/addride               accepts raw or multipart bodies, gzip or not; echoes size + md5
  GET  /oauth/authorize           fake login page with an Approve button (auth-code + PKCE)
  POST /oauth/device              device authorisation grant: issues a user code
  GET  /activate                  where the user types the device code on their phone
  POST /oauth/token               exchanges auth code (verifying PKCE) or polls a device code
  GET  /log                       everything seen so far, newest last

Standard library only. Run:  python3 tools/probe_server.py [--port 8080] [--auto-approve]
Expose to the Karoo with:     cloudflared tunnel --url http://localhost:8080
"""
import argparse, base64, gzip, hashlib, json, secrets, sys, time, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = []
AUTH_CODES = {}     # code -> {challenge, redirect_uri, used}
DEVICE_CODES = {}   # device_code -> {user_code, approved, created}
TOKENS = set()
AUTO_APPROVE = False


def log(kind, **fields):
    entry = {"t": time.strftime("%H:%M:%S"), "kind": kind, **fields}
    LOG.append(entry)
    print(json.dumps(entry), flush=True)


def parse_multipart(body, content_type):
    boundary = content_type.split("boundary=")[1].strip().encode()
    parts = {}
    for chunk in body.split(b"--" + boundary)[1:]:
        if chunk.strip() in (b"", b"--"):
            continue
        head, _, data = chunk.partition(b"\r\n\r\n")
        data = data[:-2] if data.endswith(b"\r\n") else data
        disp = [h for h in head.decode(errors="replace").split("\r\n") if h.lower().startswith("content-disposition")]
        name, filename = None, None
        if disp:
            for kv in disp[0].split(";")[1:]:
                k, _, v = kv.strip().partition("=")
                v = v.strip('"')
                if k == "name": name = v
                if k == "filename": filename = v
        parts[name] = {"filename": filename, "data": data}
    return parts


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send(self, status, body, ctype="application/json", headers=None):
        data = body if isinstance(body, bytes) else (json.dumps(body) if ctype == "application/json" else body).encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        wire = len(raw)
        if self.headers.get("Content-Encoding", "").lower() == "gzip":
            raw = gzip.decompress(raw)
        return raw, wire

    def log_message(self, *a):  # quiet the default access log
        pass

    # ---- GET ----------------------------------------------------------------
    def do_GET(self):
        url = urllib.parse.urlsplit(self.path)
        q = dict(urllib.parse.parse_qsl(url.query))
        if url.path == "/ping":
            log("ping", ua=self.headers.get("User-Agent"), **q)
            return self._send(200, {"ok": True, "server_time": time.time(), "echo": q})
        if url.path == "/oauth/authorize":
            log("authorize", **{k: v for k, v in q.items() if k != "code_challenge"})
            code = secrets.token_urlsafe(24)
            AUTH_CODES[code] = {"challenge": q.get("code_challenge"), "redirect_uri": q.get("redirect_uri"), "used": False}
            target = f"{q.get('redirect_uri')}?code={code}&state={urllib.parse.quote(q.get('state', ''))}"
            page = f"""<html><body style="font-family:sans-serif;font-size:22px;padding:20px">
<h2>Probe login</h2><p>Pretend this is mybiketraffic.com asking you to sign in.</p>
<p>Client: <b>{q.get('client_id')}</b><br>PKCE: <b>{'yes' if q.get('code_challenge') else 'no'}</b></p>
<p><a href="{target}" style="display:inline-block;padding:16px 28px;background:#2a7;color:#fff;text-decoration:none;border-radius:8px">Approve</a></p>
<p style="color:#888;font-size:14px">Redirects to {q.get('redirect_uri')}</p></body></html>"""
            return self._send(200, page, "text/html")
        if url.path == "/activate":
            msg = ""
            if "user_code" in q:
                code = q["user_code"].strip().upper()
                hit = [d for d in DEVICE_CODES.values() if d["user_code"] == code]
                if hit:
                    hit[0]["approved"] = True
                    msg = f"<p style='color:#2a7'>Code {code} approved. Go back to the Karoo.</p>"
                    log("device_approved", user_code=code)
                else:
                    msg = f"<p style='color:#c33'>Unknown code {code}.</p>"
            page = f"""<html><body style="font-family:sans-serif;font-size:22px;padding:20px">
<h2>Activate device</h2>{msg}<form><input name="user_code" placeholder="CODE" style="font-size:28px;width:8em;text-transform:uppercase">
<button style="font-size:22px">Approve</button></form></body></html>"""
            return self._send(200, page, "text/html")
        if url.path == "/log":
            return self._send(200, "\n".join(json.dumps(e) for e in LOG) + "\n", "text/plain")
        self._send(404, {"error": "not_found", "path": url.path})

    # ---- POST ---------------------------------------------------------------
    def do_POST(self):
        url = urllib.parse.urlsplit(self.path)
        q = dict(urllib.parse.parse_qsl(url.query))
        ctype = self.headers.get("Content-Type", "")
        started = time.time()
        raw, wire = self._body()
        secs = round(time.time() - started, 3)

        if url.path == "/api/addride":
            auth = self.headers.get("Authorization")
            if ctype.startswith("multipart/form-data"):
                parts = parse_multipart(raw, ctype)
                files = {k: v for k, v in parts.items() if v["filename"]}
                fields = {k: v["data"].decode(errors="replace") for k, v in parts.items() if not v["filename"]}
                content = next(iter(files.values()))["data"] if files else b""
                fname = next(iter(files.values()))["filename"] if files else None
            else:
                content, fname, fields = raw, None, {}
            md5 = hashlib.md5(content).hexdigest()
            is_fit = content[8:12] == b".FIT"
            log("addride", via=q.get("via"), bytes=len(content), wire_bytes=wire, gzip=wire != len(content),
                secs=secs, kbps=round(wire * 8 / 1000 / max(secs, 0.001)), multipart=ctype.startswith("multipart"),
                filename=fname, fields=fields, fit=is_fit, md5=md5, auth=auth, ua=self.headers.get("User-Agent"))
            return self._send(200, {"ok": True, "bytes": len(content), "wire_bytes": wire, "md5": md5,
                                    "fit": is_fit, "filename": fname, "secs": secs})

        form = dict(urllib.parse.parse_qsl(raw.decode(errors="replace")))
        if url.path == "/oauth/device":
            device_code = secrets.token_urlsafe(24)
            user_code = "".join(secrets.choice("BCDFGHJKLMNPQRSTVWXZ23456789") for _ in range(6))
            user_code = user_code[:3] + "-" + user_code[3:]
            DEVICE_CODES[device_code] = {"user_code": user_code, "approved": AUTO_APPROVE, "created": time.time()}
            host = self.headers.get("X-Forwarded-Host") or self.headers.get("Host")
            scheme = self.headers.get("X-Forwarded-Proto", "http")
            log("device_start", user_code=user_code, client_id=form.get("client_id"))
            print(f"\n   >>> DEVICE CODE {user_code} — open {scheme}://{host}/activate on your phone <<<\n", flush=True)
            return self._send(200, {"device_code": device_code, "user_code": user_code,
                                    "verification_uri": f"{scheme}://{host}/activate",
                                    "expires_in": 600, "interval": 5})

        if url.path == "/oauth/token":
            grant = form.get("grant_type")
            if grant == "authorization_code":
                entry = AUTH_CODES.get(form.get("code"))
                if not entry or entry["used"]:
                    log("token_error", reason="unknown or reused code")
                    return self._send(400, {"error": "invalid_grant"})
                if entry["challenge"]:
                    expect = base64.urlsafe_b64encode(hashlib.sha256(form.get("code_verifier", "").encode()).digest()).rstrip(b"=").decode()
                    if expect != entry["challenge"]:
                        log("token_error", reason="PKCE mismatch")
                        return self._send(400, {"error": "invalid_grant", "error_description": "pkce"})
                entry["used"] = True
                tok = secrets.token_urlsafe(32); TOKENS.add(tok)
                log("token_issued", grant="authorization_code", pkce=bool(entry["challenge"]))
                return self._send(200, {"access_token": tok, "token_type": "Bearer", "expires_in": 3600,
                                        "refresh_token": secrets.token_urlsafe(32)})
            if grant == "urn:ietf:params:oauth:grant-type:device_code":
                entry = DEVICE_CODES.get(form.get("device_code"))
                if not entry:
                    return self._send(400, {"error": "invalid_grant"})
                if not entry["approved"]:
                    log("device_poll", user_code=entry["user_code"], status="pending")
                    return self._send(400, {"error": "authorization_pending"})
                tok = secrets.token_urlsafe(32); TOKENS.add(tok)
                log("token_issued", grant="device_code", user_code=entry["user_code"])
                return self._send(200, {"access_token": tok, "token_type": "Bearer", "expires_in": 3600,
                                        "refresh_token": secrets.token_urlsafe(32)})
            return self._send(400, {"error": "unsupported_grant_type"})

        self._send(404, {"error": "not_found", "path": url.path})


def main():
    global AUTO_APPROVE
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--auto-approve", action="store_true", help="approve device codes without visiting /activate")
    a = ap.parse_args()
    AUTO_APPROVE = a.auto_approve
    srv = ThreadingHTTPServer(("0.0.0.0", a.port), Handler)
    print(f"probe server on http://0.0.0.0:{a.port}  (log at /log)", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
