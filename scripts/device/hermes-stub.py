#!/usr/bin/env python3
"""A scripted stand-in for the Hermes API Server's Runs API (docs/hermes.md), for device checks only.

    nix shell nixpkgs#python3 -c python3 scripts/device/hermes-stub.py [port] [key]

Run 1 asks a question, run 2 asks to send an email to 6 people, run 3 reports what was sent (it echoes the
decision Klean sent). Every request needs `Authorization: Bearer <key>` (default "stub-key"). Logs requests
to stdout as JSON lines.
"""
import json, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8642
KEY = sys.argv[2] if len(sys.argv) > 2 else "stub-key"
lock = threading.Lock()
runs = {}  # run_id -> input
counter = [0]
SIX = [f"person{i}@example.com" for i in range(1, 7)]


def script(n, last_input):
    if n == 1:
        return "I can do that. " + json.dumps({"klean": "question", "text": "Which date is the offsite?"})
    if n == 2:
        return "Draft ready. " + json.dumps({"klean": "approval", "action": "send_email",
                                              "draft": "Hi all, the team offsite is on the 20th. Details to follow.",
                                              "recipients": SIX})
    try:
        d = json.loads(last_input)
    except Exception:
        d = {}
    if d.get("decision") == "REJECTED":
        return "Understood, nothing was sent."
    return f"Sent the invitation to {len(d.get('recipients', []))} people: \"{d.get('draft', '')}\""


class H(BaseHTTPRequestHandler):
    def _auth(self):
        if self.headers.get("Authorization") != f"Bearer {KEY}":
            self.send_response(401); self.end_headers(); self.wfile.write(b'{"error":"unauthorized"}'); return False
        return True

    def _json(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)

    def log_message(self, *a):
        pass

    def do_GET(self):
        print(json.dumps({"method": "GET", "path": self.path}), flush=True)
        if not self._auth(): return
        if self.path == "/v1/health":
            return self._json(200, {"status": "ok"})
        if self.path.startswith("/v1/runs/") and self.path.endswith("/events"):
            run_id = self.path.split("/")[3]
            with lock:
                n, inp = runs.get(run_id, (0, ""))
            text = script(n, inp)
            self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.end_headers()
            for i, ev in enumerate([{"event": "run.started"}, {"event": "message.delta", "delta": text}, {"event": "run.completed"}]):
                ev.update(run_id=run_id, seq=i)
                self.wfile.write(f"data: {json.dumps(ev)}\n\n".encode()); self.wfile.flush()
            return
        self._json(404, {"error": "not found"})

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode()
        print(json.dumps({"method": "POST", "path": self.path, "body": body}), flush=True)
        if not self._auth(): return
        if self.path == "/v1/runs":
            d = json.loads(body or "{}")
            with lock:
                counter[0] += 1
                run_id = f"run_{counter[0]}"
                runs[run_id] = (counter[0], d.get("input", ""))
            return self._json(202, {"run_id": run_id, "status": "started"})
        self._json(200, {"ok": True})


ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
