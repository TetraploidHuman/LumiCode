#!/usr/bin/env python3
"""Serve the Kotlin/Wasm distribution with correct MIME types.

Also reverse-proxies `/api/dsh/*` to the local lumicode-dsh-bridge (8098),
so the Wasm UI can talk to the already-running dsh-web Host without CORS
gymnastics and without embedding DSH secrets in the browser.

Usage:
    python3 tools/serve_wasm.py                     # 127.0.0.1:8080
    python3 tools/serve_wasm.py --port 9000 --host 0.0.0.0
    python3 tools/serve_wasm.py --dir path/to/productionExecutable
"""

from __future__ import annotations

import argparse
import functools
import http.client
import http.server
import os
import socket
import socketserver
import sys
from urllib.parse import urlsplit

_TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if _TOOLS_DIR not in sys.path:
    sys.path.insert(0, _TOOLS_DIR)
from workspace_api import WorkspaceStore, handle_workspace, json_response
from pty_server import handle_pty_websocket

DEFAULT_DIR = "composeApp/build/dist/wasmJs/productionExecutable"
DSH_BRIDGE = os.environ.get("LUMICODE_DSH_BRIDGE", "127.0.0.1:8098")

EXTRA_TYPES = {
    ".wasm": "application/wasm",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".map": "application/json; charset=utf-8",
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".otf": "font/otf",
    ".ttf": "font/ttf",
    ".woff2": "font/woff2",
}


class ArchiveHandler(http.server.SimpleHTTPRequestHandler):
    """Static handler with wasm-friendly MIME types and light caching rules."""

    protocol_version = "HTTP/1.1"
    workspace_store = WorkspaceStore()

    def guess_type(self, path):  # noqa: D102 - inherited docstring is fine
        extension = os.path.splitext(str(path))[1].lower()
        if extension in EXTRA_TYPES:
            return EXTRA_TYPES[extension]
        return super().guess_type(path)

    def end_headers(self) -> None:
        # The shell itself must not be cached while iterating on a demo, but the
        # multi-megabyte wasm payload is content-hashed by the Kotlin toolchain.
        if self.path.endswith((".wasm", ".otf")):
            self.send_header("Cache-Control", "public, max-age=86400")
        else:
            self.send_header("Cache-Control", "no-store")
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
        super().end_headers()

    def log_message(self, fmt: str, *args) -> None:  # quieter, one line per hit
        sys.stderr.write("[wasm] %s %s\n" % (self.address_string(), fmt % args))

    def do_OPTIONS(self) -> None:  # noqa: N802
        if self._is_api():
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "content-type")
            self.send_header("Cross-Origin-Resource-Policy", "same-origin")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_error(404)

    def do_GET(self) -> None:  # noqa: N802
        if self._is_pty_upgrade():
            root = self.workspace_store.root
            handle_pty_websocket(self, root)
            return
        if self._is_dsh_api():
            self._proxy_dsh()
            return
        if self._is_workspace_api():
            self._handle_workspace(None)
            return
        super().do_GET()

    def _is_pty_upgrade(self) -> bool:
        path = urlsplit(self.path).path
        if path != "/api/pty":
            return False
        upgrade = (self.headers.get("Upgrade") or "").lower()
        connection = (self.headers.get("Connection") or "").lower()
        return upgrade == "websocket" and "upgrade" in connection
    def do_POST(self) -> None:  # noqa: N802
        if self._is_dsh_api():
            self._proxy_dsh()
            return
        if self._is_workspace_api():
            self._handle_workspace(self._read_body())
            return
        self.send_error(405, "Method Not Allowed")

    def do_PUT(self) -> None:  # noqa: N802
        if self._is_workspace_api():
            self._handle_workspace(self._read_body())
            return
        self.send_error(405, "Method Not Allowed")

    def do_DELETE(self) -> None:  # noqa: N802
        if self._is_workspace_api():
            self._handle_workspace(None)
            return
        self.send_error(405, "Method Not Allowed")

    def _read_body(self) -> bytes | None:
        length = int(self.headers.get("Content-Length") or "0")
        return self.rfile.read(length) if length > 0 else None

    def _is_api(self) -> bool:
        return self._is_dsh_api() or self._is_workspace_api()

    def _is_dsh_api(self) -> bool:
        path = urlsplit(self.path).path
        return path == "/api/dsh" or path.startswith("/api/dsh/")

    def _is_workspace_api(self) -> bool:
        path = urlsplit(self.path).path
        return path == "/api/workspace" or path.startswith("/api/workspace/")

    def _handle_workspace(self, body: bytes | None) -> None:
        parsed = urlsplit(self.path)
        path = parsed.path + (("?" + parsed.query) if parsed.query else "")
        try:
            status, payload = handle_workspace(self.workspace_store, self.command, path, body)
        except OSError as error:
            status, payload = 503, json_response({"ok": False, "error": str(error)})
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    def _proxy_dsh(self) -> None:
        parsed = urlsplit(self.path)
        # /api/dsh/v1/health -> /v1/health
        suffix = parsed.path[len("/api/dsh") :] or "/"
        if not suffix.startswith("/"):
            suffix = "/" + suffix
        target = suffix + (("?" + parsed.query) if parsed.query else "")

        length = int(self.headers.get("Content-Length") or "0")
        body = self.rfile.read(length) if length > 0 else None

        host, _, port_s = DSH_BRIDGE.partition(":")
        port = int(port_s or "8098")
        try:
            conn = http.client.HTTPConnection(host, port, timeout=120)
            headers = {
                "Accept": "application/json",
                "Connection": "close",
            }
            if body is not None:
                headers["Content-Type"] = self.headers.get("Content-Type") or "application/json"
                headers["Content-Length"] = str(len(body))
            conn.request(self.command, target, body=body, headers=headers)
            upstream = conn.getresponse()
            data = upstream.read()
            self.send_response(upstream.status)
            ctype = upstream.getheader("Content-Type") or "application/json; charset=utf-8"
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Cross-Origin-Resource-Policy", "same-origin")
            self.send_header("Cross-Origin-Opener-Policy", "same-origin")
            self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(data)
            conn.close()
        except OSError as error:
            payload = ('{"ok":false,"error":"dsh bridge unreachable: %s"}' % error).encode("utf-8")
            self.send_response(503)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Cross-Origin-Resource-Policy", "same-origin")
            self.end_headers()
            self.wfile.write(payload)


class ReusableServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", default=DEFAULT_DIR, help="distribution directory")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    root = os.path.abspath(args.dir)
    if not os.path.isfile(os.path.join(root, "index.html")):
        print(f"no index.html in {root}\nrun: ./gradlew :composeApp:wasmJsBrowserDistribution", file=sys.stderr)
        return 1

    handler = functools.partial(ArchiveHandler, directory=root)
    with ReusableServer((args.host, args.port), handler) as httpd:
        host, port = httpd.server_address[:2]
        display = "127.0.0.1" if host in ("0.0.0.0", "") else host
        print(f"LumiCode wasm dist: {root}", flush=True)
        print(f"serving on http://{display}:{port}/  (Ctrl+C to stop)", flush=True)
        print(f"dsh bridge proxy: /api/dsh/* → http://{DSH_BRIDGE}/", flush=True)
        print("workspace api: /api/workspace/* (local disk)", flush=True)
        print("pty websocket: /api/pty", flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nstopped", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except OSError as error:
        print(f"cannot bind: {error}", file=sys.stderr)
        raise SystemExit(1) from error
