#!/usr/bin/env python3
"""Serve the Kotlin/Wasm distribution with correct MIME types.

`python3 -m http.server` does not know about `.wasm` / `.otf`, which makes the
browser fall back to slow (or broken) WebAssembly instantiation and font loading.
This little server sends the right Content-Type, disables caching for the wasm
payload and reuses connections.

Usage:
    python3 tools/serve_wasm.py                     # 127.0.0.1:8080
    python3 tools/serve_wasm.py --port 9000 --host 0.0.0.0
    python3 tools/serve_wasm.py --dir path/to/productionExecutable

The default directory is the output of `./gradlew :composeApp:wasmJsBrowserDistribution`.
"""

from __future__ import annotations

import argparse
import functools
import http.server
import os
import socket
import socketserver
import sys

DEFAULT_DIR = "composeApp/build/dist/wasmJs/productionExecutable"

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
        super().end_headers()

    def log_message(self, fmt: str, *args) -> None:  # quieter, one line per hit
        sys.stderr.write("[wasm] %s %s\n" % (self.address_string(), fmt % args))


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
        print(f"LUMICODE wasm dist: {root}", flush=True)
        print(f"serving on http://{display}:{port}/  (Ctrl+C to stop)", flush=True)
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
