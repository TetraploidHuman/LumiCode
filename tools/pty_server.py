"""Minimal WebSocket PTY for LumiCode (stdlib only).

Protocol (text JSON frames, except raw output as binary/text data frames):
  client -> {"type":"open","cols":80,"rows":24,"cwd":""}
  client -> {"type":"input","data":"..."}
  client -> {"type":"resize","cols":80,"rows":24}
  client -> {"type":"close"}
  server -> {"type":"ready","pid":123,"cwd":"/path"}
  server -> {"type":"out","data":"..."}   (also raw text frames for throughput)
  server -> {"type":"exit","code":0}
  server -> {"type":"error","message":"..."}
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import pty
import select
import struct
import termios
import threading
import time
from typing import Callable


GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def _ws_accept(key: str) -> str:
    digest = hashlib.sha1((key + GUID).encode("utf-8")).digest()
    return base64.b64encode(digest).decode("ascii")


def _read_frame(rfile) -> tuple[int, bytes] | None:
    hdr = rfile.read(2)
    if not hdr or len(hdr) < 2:
        return None
    b1, b2 = hdr[0], hdr[1]
    opcode = b1 & 0x0F
    masked = (b2 & 0x80) != 0
    length = b2 & 0x7F
    if length == 126:
        ext = rfile.read(2)
        if len(ext) < 2:
            return None
        length = struct.unpack("!H", ext)[0]
    elif length == 127:
        ext = rfile.read(8)
        if len(ext) < 8:
            return None
        length = struct.unpack("!Q", ext)[0]
    mask = rfile.read(4) if masked else b""
    if masked and len(mask) < 4:
        return None
    payload = rfile.read(length) if length else b""
    if len(payload) < length:
        return None
    if masked:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    return opcode, payload


def _write_frame(wfile, opcode: int, payload: bytes) -> None:
    header = bytearray()
    header.append(0x80 | (opcode & 0x0F))
    n = len(payload)
    if n < 126:
        header.append(n)
    elif n < 65536:
        header.append(126)
        header.extend(struct.pack("!H", n))
    else:
        header.append(127)
        header.extend(struct.pack("!Q", n))
    wfile.write(header + payload)
    wfile.flush()


def _set_winsize(fd: int, rows: int, cols: int) -> None:
    try:
        winsize = struct.pack("HHHH", rows, cols, 0, 0)
        import fcntl

        fcntl.ioctl(fd, termios.TIOCSWINSZ, winsize)
    except OSError:
        pass


class PtySession:
    def __init__(self, root: str, cwd_rel: str, cols: int, rows: int) -> None:
        self.root = os.path.realpath(root)
        work = self.root
        if cwd_rel.strip():
            candidate = os.path.realpath(os.path.join(self.root, cwd_rel.strip().lstrip("/")))
            if candidate == self.root or candidate.startswith(self.root + os.sep):
                if os.path.isdir(candidate):
                    work = candidate
        self.cwd = work
        self.cols = max(20, min(cols, 300))
        self.rows = max(5, min(rows, 120))
        self.master = -1
        self.pid = -1
        self.alive = False

    def start(self) -> None:
        shell = os.environ.get("SHELL") or "/bin/bash"
        pid, master = pty.fork()
        if pid == 0:
            os.chdir(self.cwd)
            env = os.environ.copy()
            env["TERM"] = "xterm-256color"
            env["COLORTERM"] = "truecolor"
            os.execvpe(shell, [shell, "-l"], env)
        self.pid = pid
        self.master = master
        self.alive = True
        _set_winsize(master, self.rows, self.cols)

    def write(self, data: bytes) -> None:
        if self.alive and self.master >= 0 and data:
            try:
                os.write(self.master, data)
            except OSError:
                self.alive = False

    def resize(self, cols: int, rows: int) -> None:
        self.cols = max(20, min(cols, 300))
        self.rows = max(5, min(rows, 120))
        if self.master >= 0:
            _set_winsize(self.master, self.rows, self.cols)

    def close(self) -> None:
        self.alive = False
        if self.master >= 0:
            try:
                os.close(self.master)
            except OSError:
                pass
            self.master = -1
        if self.pid > 0:
            try:
                os.waitpid(self.pid, os.WNOHANG)
            except OSError:
                pass


def handle_pty_websocket(handler, workspace_root: str | None) -> None:
    """Take over an upgraded HTTP connection as a PTY WebSocket."""
    key = handler.headers.get("Sec-WebSocket-Key")
    if not key:
        handler.send_error(400, "Missing Sec-WebSocket-Key")
        return
    handler.send_response(101, "Switching Protocols")
    handler.send_header("Upgrade", "websocket")
    handler.send_header("Connection", "Upgrade")
    handler.send_header("Sec-WebSocket-Accept", _ws_accept(key))
    handler.send_header("Cross-Origin-Resource-Policy", "same-origin")
    handler.end_headers()

    rfile = handler.rfile
    wfile = handler.wfile
    lock = threading.Lock()
    session: PtySession | None = None
    stop = threading.Event()

    def send_json(obj: dict) -> None:
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        with lock:
            try:
                _write_frame(wfile, 0x1, raw)
            except OSError:
                stop.set()

    def send_text(text: str) -> None:
        # Prefer JSON out frames for client simplicity
        send_json({"type": "out", "data": text})

    def reader_loop(sess: PtySession) -> None:
        while not stop.is_set() and sess.alive:
            try:
                ready, _, _ = select.select([sess.master], [], [], 0.2)
            except (OSError, ValueError):
                break
            if not ready:
                # check child
                if sess.pid > 0:
                    try:
                        finished, status = os.waitpid(sess.pid, os.WNOHANG)
                        if finished:
                            code = os.waitstatus_to_exitcode(status) if hasattr(os, "waitstatus_to_exitcode") else 0
                            send_json({"type": "exit", "code": code})
                            sess.alive = False
                            break
                    except OSError:
                        pass
                continue
            try:
                chunk = os.read(sess.master, 4096)
            except OSError:
                break
            if not chunk:
                break
            try:
                text = chunk.decode("utf-8", errors="replace")
            except Exception:
                text = chunk.decode("latin-1", errors="replace")
            send_text(text)
        sess.close()
        stop.set()

    try:
        while not stop.is_set():
            frame = _read_frame(rfile)
            if frame is None:
                break
            opcode, payload = frame
            if opcode == 0x8:  # close
                break
            if opcode == 0x9:  # ping
                with lock:
                    _write_frame(wfile, 0xA, payload)
                continue
            if opcode != 0x1 and opcode != 0x2:
                continue
            try:
                msg = json.loads(payload.decode("utf-8"))
            except Exception:
                continue
            mtype = msg.get("type")
            if mtype == "open":
                if not workspace_root or not os.path.isdir(workspace_root):
                    send_json({"type": "error", "message": "请先打开工作区"})
                    continue
                if session is not None:
                    session.close()
                session = PtySession(
                    root=workspace_root,
                    cwd_rel=str(msg.get("cwd") or ""),
                    cols=int(msg.get("cols") or 80),
                    rows=int(msg.get("rows") or 24),
                )
                try:
                    session.start()
                except OSError as error:
                    send_json({"type": "error", "message": str(error)})
                    session = None
                    continue
                send_json({"type": "ready", "pid": session.pid, "cwd": session.cwd})
                threading.Thread(target=reader_loop, args=(session,), daemon=True).start()
            elif mtype == "input" and session is not None:
                data = str(msg.get("data") or "")
                session.write(data.encode("utf-8", errors="replace"))
            elif mtype == "resize" and session is not None:
                session.resize(int(msg.get("cols") or 80), int(msg.get("rows") or 24))
            elif mtype == "close":
                break
    finally:
        stop.set()
        if session is not None:
            session.close()
        try:
            with lock:
                _write_frame(wfile, 0x8, b"")
        except OSError:
            pass
