"""Local workspace filesystem API for LumiCode Wasm / web server.

All paths are confined under a configured workspace root. State is persisted in
~/.config/lumicode/workspace-root.txt so the server remembers the last folder.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any

MAX_EXEC_SECONDS = 60
MAX_EXEC_OUTPUT = 200 * 1024

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "lumicode"
ROOT_FILE = CONFIG_DIR / "workspace-root.txt"

SKIP_DIR_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    "node_modules",
    "build",
    "dist",
    "out",
    ".cursor",
}

TEXT_EXTENSIONS = {
    ".kt",
    ".kts",
    ".java",
    ".gradle",
    ".json",
    ".md",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
    ".toml",
    ".properties",
    ".html",
    ".css",
    ".js",
    ".mjs",
    ".ts",
    ".tsx",
    ".jsx",
    ".py",
    ".sh",
    ".rs",
    ".go",
    ".c",
    ".h",
    ".cpp",
    ".hpp",
    ".sql",
    ".gitignore",
    ".env",
}

MAX_FILE_BYTES = 512 * 1024


def _ensure_config() -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)


def load_root() -> str | None:
    try:
        raw = ROOT_FILE.read_text(encoding="utf-8").strip()
        if raw and os.path.isdir(raw):
            return os.path.realpath(raw)
    except OSError:
        pass
    return None


def save_root(path: str) -> None:
    _ensure_config()
    ROOT_FILE.write_text(path + "\n", encoding="utf-8")


class WorkspaceStore:
    def __init__(self) -> None:
        self.root: str | None = load_root()

    def status(self) -> dict[str, Any]:
        if not self.root:
            return {"ok": False, "root": None, "fileCount": 0}
        files = self._list_files()
        return {"ok": True, "root": self.root, "fileCount": len(files)}

    def open_root(self, path: str) -> dict[str, Any]:
        abs_path = os.path.realpath(os.path.expanduser(path.strip()))
        if not os.path.isdir(abs_path):
            return {"ok": False, "error": f"not a directory: {abs_path}"}
        self.root = abs_path
        save_root(abs_path)
        files = self._list_files()
        return {"ok": True, "root": abs_path, "fileCount": len(files)}

    def browse(self, path: str = "") -> dict[str, Any]:
        """Browse an absolute directory (before workspace is mounted)."""
        raw = path.strip()
        target = os.path.realpath(os.path.expanduser(raw if raw else "~"))
        if not os.path.isdir(target):
            return {"ok": False, "error": f"not a directory: {target}"}
        entries: list[dict[str, str]] = []
        try:
            for name in sorted(os.listdir(target)):
                if name.startswith("."):
                    continue
                full = os.path.join(target, name)
                if os.path.isdir(full):
                    entries.append({"name": name, "path": full})
        except OSError as error:
            return {"ok": False, "error": str(error)}
        parent = os.path.dirname(target)
        parent_out = parent if parent != target else ""
        return {
            "ok": True,
            "current": target,
            "parent": parent_out,
            "entries": entries,
        }

    def list_dirs(self, path: str = "") -> dict[str, Any]:
        base = self._require_root()
        target = self._safe_join(base, path) if path else base
        if not os.path.isdir(target):
            return {"ok": False, "error": "not a directory"}
        entries: list[dict[str, str]] = []
        try:
            for name in sorted(os.listdir(target)):
                if name.startswith("."):
                    continue
                full = os.path.join(target, name)
                if os.path.isdir(full):
                    entries.append({"name": name, "path": self._rel(base, full)})
        except OSError as error:
            return {"ok": False, "error": str(error)}
        parent = self._rel(base, os.path.dirname(target)) if target != base else ""
        return {
            "ok": True,
            "current": self._rel(base, target),
            "parent": parent,
            "entries": entries,
        }

    def tree(self) -> dict[str, Any]:
        base = self._require_root()
        files = self._list_files()
        folders: set[str] = set()
        for rel in files:
            folder = os.path.dirname(rel)
            while folder:
                folders.add(folder.replace("\\", "/"))
                folder = os.path.dirname(folder)
        return {
            "ok": True,
            "root": base,
            "files": files,
            "folders": sorted(folders),
        }

    def read_file(self, rel_path: str) -> dict[str, Any]:
        base = self._require_root()
        target = self._safe_join(base, rel_path)
        if not os.path.isfile(target):
            return {"ok": False, "error": "not a file"}
        try:
            size = os.path.getsize(target)
            if size > MAX_FILE_BYTES:
                return {"ok": False, "error": f"file too large ({size} bytes)"}
            with open(target, "r", encoding="utf-8", errors="replace") as handle:
                content = handle.read()
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "path": rel_path.replace("\\", "/"), "content": content}

    def write_file(self, rel_path: str, content: str) -> dict[str, Any]:
        base = self._require_root()
        target = self._safe_join(base, rel_path)
        try:
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(content)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "path": rel_path.replace("\\", "/")}

    def mkdir(self, rel_path: str) -> dict[str, Any]:
        base = self._require_root()
        target = self._safe_join(base, rel_path)
        try:
            os.makedirs(target, exist_ok=True)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "path": rel_path.replace("\\", "/")}

    def mkdir_abs(self, parent: str, name: str) -> dict[str, Any]:
        """Create a folder while browsing (before/outside mounted workspace)."""
        folder = (name or "").strip()
        if not folder or folder in (".", "..") or "/" in folder or "\\" in folder:
            return {"ok": False, "error": "文件夹名无效"}
        parent_abs = os.path.realpath(os.path.expanduser((parent or "").strip() or "~"))
        if not os.path.isdir(parent_abs):
            return {"ok": False, "error": f"父目录不存在: {parent_abs}"}
        target = os.path.realpath(os.path.join(parent_abs, folder))
        # stay under parent (block .. tricks even if name was sanitized)
        if not (target == parent_abs or target.startswith(parent_abs + os.sep)):
            return {"ok": False, "error": "路径越界"}
        if os.path.exists(target):
            return {"ok": False, "error": "已存在同名项"}
        try:
            os.mkdir(target)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "path": target}

    def rename(self, old_path: str, new_path: str) -> dict[str, Any]:
        base = self._require_root()
        src = self._safe_join(base, old_path)
        dst = self._safe_join(base, new_path)
        if not os.path.exists(src):
            return {"ok": False, "error": "source missing"}
        if os.path.exists(dst):
            return {"ok": False, "error": "target exists"}
        try:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            os.rename(src, dst)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "from": old_path.replace("\\", "/"), "to": new_path.replace("\\", "/")}

    def delete(self, rel_path: str) -> dict[str, Any]:
        base = self._require_root()
        target = self._safe_join(base, rel_path)
        if not os.path.exists(target):
            return {"ok": False, "error": "not found"}
        try:
            if os.path.isdir(target):
                # only empty folders
                os.rmdir(target)
            else:
                os.remove(target)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "path": rel_path.replace("\\", "/")}

    def exec_cmd(self, command: str, cwd_rel: str = "") -> dict[str, Any]:
        """Run a shell command confined to the workspace root."""
        base = self._require_root()
        cmd = command.strip()
        if not cmd:
            return {"ok": False, "error": "empty command"}
        work = self._safe_join(base, cwd_rel) if cwd_rel.strip() else base
        if not os.path.isdir(work):
            return {"ok": False, "error": f"cwd not a directory: {cwd_rel}"}
        shell = os.environ.get("SHELL") or "/bin/bash"
        try:
            completed = subprocess.run(
                [shell, "-lc", cmd],
                cwd=work,
                capture_output=True,
                text=True,
                timeout=MAX_EXEC_SECONDS,
                env={**os.environ, "TERM": "dumb"},
            )
        except subprocess.TimeoutExpired as error:
            out = ((error.stdout or "") + (error.stderr or ""))[:MAX_EXEC_OUTPUT]
            return {
                "ok": False,
                "exitCode": -1,
                "stdout": out,
                "stderr": f"timeout after {MAX_EXEC_SECONDS}s",
                "cwd": self._rel(base, work),
                "error": f"timeout after {MAX_EXEC_SECONDS}s",
            }
        except OSError as error:
            return {"ok": False, "error": str(error), "cwd": self._rel(base, work)}

        stdout = (completed.stdout or "")[:MAX_EXEC_OUTPUT]
        stderr = (completed.stderr or "")[:MAX_EXEC_OUTPUT]
        return {
            "ok": completed.returncode == 0,
            "exitCode": completed.returncode,
            "stdout": stdout,
            "stderr": stderr,
            "cwd": self._rel(base, work),
        }

    def _require_root(self) -> str:
        if not self.root:
            raise ValueError("workspace not opened")
        return self.root

    def _rel(self, base: str, full: str) -> str:
        rel = os.path.relpath(full, base)
        return "" if rel == "." else rel.replace("\\", "/")

    def _safe_join(self, base: str, rel: str) -> str:
        rel = rel.strip().lstrip("/").replace("\\", "/")
        if ".." in rel.split("/"):
            raise ValueError("path escape")
        target = os.path.realpath(os.path.join(base, rel))
        base_real = os.path.realpath(base)
        if target != base_real and not target.startswith(base_real + os.sep):
            raise ValueError("path escape")
        return target

    def _list_files(self) -> list[str]:
        if not self.root:
            return []
        base = self.root
        out: list[str] = []
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIR_NAMES and not d.startswith(".")]
            for name in filenames:
                if name.startswith("."):
                    continue
                full = os.path.join(dirpath, name)
                rel = self._rel(base, full)
                ext = os.path.splitext(name)[1].lower()
                if ext not in TEXT_EXTENSIONS and ext != "":
                    continue
                try:
                    if os.path.getsize(full) > MAX_FILE_BYTES:
                        continue
                except OSError:
                    continue
                out.append(rel)
        out.sort()
        return out


def json_response(payload: dict[str, Any]) -> bytes:
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


def handle_workspace(store: WorkspaceStore, method: str, path: str, body: bytes | None) -> tuple[int, bytes]:
    try:
        if path == "/api/workspace/status" and method == "GET":
            return 200, json_response(store.status())
        if path == "/api/workspace/open" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.open_root(str(data.get("path", ""))))
        if path.startswith("/api/workspace/browse") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            sub = qs.get("path", [""])[0]
            return 200, json_response(store.browse(sub))
        if path.startswith("/api/workspace/list-dirs") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            sub = qs.get("path", [""])[0]
            return 200, json_response(store.list_dirs(sub))
        if path == "/api/workspace/tree" and method == "GET":
            return 200, json_response(store.tree())
        if path.startswith("/api/workspace/read") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            rel = qs.get("path", [""])[0]
            return 200, json_response(store.read_file(rel))
        if path == "/api/workspace/write" and method == "PUT":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.write_file(str(data.get("path", "")), str(data.get("content", ""))))
        if path == "/api/workspace/mkdir" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.mkdir(str(data.get("path", ""))))
        if path == "/api/workspace/mkdir-abs" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            parent = str(data.get("parent", ""))
            name = str(data.get("name", ""))
            result = store.mkdir_abs(parent, name)
            print(f"[workspace] mkdir-abs parent={parent!r} name={name!r} -> {result}", flush=True)
            return 200, json_response(result)
        if path == "/api/workspace/rename" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.rename(str(data.get("from", "")), str(data.get("to", ""))))
        if path.startswith("/api/workspace/delete") and method == "DELETE":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            rel = qs.get("path", [""])[0]
            return 200, json_response(store.delete(rel))
        if path == "/api/workspace/exec" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(
                store.exec_cmd(
                    str(data.get("command", "")),
                    str(data.get("cwd", "")),
                )
            )
    except ValueError as error:
        return 400, json_response({"ok": False, "error": str(error)})
    except json.JSONDecodeError:
        return 400, json_response({"ok": False, "error": "invalid json"})
    return 404, json_response({"ok": False, "error": "not found"})
