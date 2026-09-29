"""Local workspace filesystem API for LumiCode Wasm / web server.

All paths are confined under a configured workspace root. State is persisted in
~/.config/lumicode/workspace-root.txt so the server remembers the last folder.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import uuid
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
    ".lumicode",
}

CACHE_DIR = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "lumicode" / "snapshots"

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

    def snapshot_create(self) -> dict[str, Any]:
        """Copy current text files into a cache snapshot for later diff/restore."""
        base = self._require_root()
        snap_id = "snap-" + uuid.uuid4().hex[:12]
        snap_root = self._snap_dir(base, snap_id)
        files_dir = snap_root / "files"
        files_dir.mkdir(parents=True, exist_ok=True)
        files = self._list_files()
        copied = 0
        for rel in files:
            src = self._safe_join(base, rel)
            dst = files_dir / rel
            try:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
                copied += 1
            except OSError:
                continue
        manifest = {"id": snap_id, "root": base, "files": files}
        (snap_root / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False),
            encoding="utf-8",
        )
        return {"ok": True, "snapshotId": snap_id, "fileCount": copied}

    def snapshot_diff(self, snap_id: str) -> dict[str, Any]:
        base = self._require_root()
        try:
            snap_root = self._snap_dir(base, snap_id)
        except ValueError as error:
            return {"ok": False, "error": str(error)}
        manifest_path = snap_root / "manifest.json"
        if not manifest_path.is_file():
            return {"ok": False, "error": "snapshot not found"}
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            return {"ok": False, "error": str(error)}
        before = set(manifest.get("files") or [])
        after = set(self._list_files())
        files_dir = snap_root / "files"
        changes: list[dict[str, str]] = []
        for rel in sorted(after - before):
            changes.append({"path": rel, "kind": "added"})
        for rel in sorted(before - after):
            changes.append({"path": rel, "kind": "deleted"})
        for rel in sorted(before & after):
            snap_file = files_dir / rel
            cur = self._safe_join(base, rel)
            try:
                if not snap_file.is_file():
                    changes.append({"path": rel, "kind": "modified"})
                    continue
                with open(snap_file, "rb") as a, open(cur, "rb") as b:
                    if a.read() != b.read():
                        changes.append({"path": rel, "kind": "modified"})
            except OSError:
                changes.append({"path": rel, "kind": "modified"})
        return {
            "ok": True,
            "snapshotId": snap_id,
            "changes": changes,
            "changeCount": len(changes),
        }

    def snapshot_restore(self, snap_id: str) -> dict[str, Any]:
        base = self._require_root()
        try:
            snap_root = self._snap_dir(base, snap_id)
        except ValueError as error:
            return {"ok": False, "error": str(error)}
        manifest_path = snap_root / "manifest.json"
        if not manifest_path.is_file():
            return {"ok": False, "error": "snapshot not found"}
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            return {"ok": False, "error": str(error)}
        before = list(manifest.get("files") or [])
        before_set = set(before)
        after = set(self._list_files())
        files_dir = snap_root / "files"
        restored = 0
        deleted = 0
        # Remove files agent added
        for rel in sorted(after - before_set):
            target = self._safe_join(base, rel)
            try:
                if os.path.isfile(target):
                    os.remove(target)
                    deleted += 1
                    # prune empty parents up to base
                    parent = os.path.dirname(target)
                    while parent.startswith(base + os.sep):
                        try:
                            os.rmdir(parent)
                        except OSError:
                            break
                        parent = os.path.dirname(parent)
            except OSError:
                continue
        # Restore snapshotted files
        for rel in before:
            src = files_dir / rel
            dst = self._safe_join(base, rel)
            try:
                if not src.is_file():
                    continue
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                restored += 1
            except OSError:
                continue
        return {
            "ok": True,
            "snapshotId": snap_id,
            "restored": restored,
            "deleted": deleted,
        }

    def snapshot_forget(self, snap_id: str) -> dict[str, Any]:
        base = self._require_root()
        try:
            snap_root = self._snap_dir(base, snap_id)
        except ValueError as error:
            return {"ok": False, "error": str(error)}
        if not snap_root.exists():
            return {"ok": True, "snapshotId": snap_id, "forgotten": False}
        try:
            shutil.rmtree(snap_root)
        except OSError as error:
            return {"ok": False, "error": str(error)}
        return {"ok": True, "snapshotId": snap_id, "forgotten": True}

    def snapshot_file_diff(self, snap_id: str, rel_path: str) -> dict[str, Any]:
        """Return before/after text for one path vs a snapshot."""
        base = self._require_root()
        try:
            snap_root = self._snap_dir(base, snap_id)
        except ValueError as error:
            return {"ok": False, "error": str(error)}
        manifest_path = snap_root / "manifest.json"
        if not manifest_path.is_file():
            return {"ok": False, "error": "snapshot not found"}
        rel = rel_path.replace("\\", "/").lstrip("/")
        before_path = snap_root / "files" / rel
        after_path = Path(self._safe_join(base, rel))
        before = None
        after = None
        try:
            if before_path.is_file():
                before = before_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            before = None
        try:
            if after_path.is_file():
                after = after_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            after = None
        kind = "modified"
        if before is None and after is not None:
            kind = "added"
        elif before is not None and after is None:
            kind = "deleted"
        elif before == after:
            kind = "same"
        return {
            "ok": True,
            "snapshotId": snap_id,
            "path": rel,
            "kind": kind,
            "before": before,
            "after": after,
        }

    def git_status(self) -> dict[str, Any]:
        base = self._require_root()
        if not (Path(base) / ".git").exists():
            return {"ok": False, "error": "not a git repository", "files": []}
        result = self.exec_cmd("git status --porcelain=v1 -uall && echo '---' && git rev-parse --abbrev-ref HEAD && git log -1 --oneline")
        if not result.get("ok") and result.get("exitCode", 1) not in (0,):
            # status may still print useful stdout
            pass
        out = (result.get("stdout") or "") + (result.get("stderr") or "")
        parts = out.split("---", 1)
        porcelain = parts[0]
        meta = parts[1].strip().splitlines() if len(parts) > 1 else []
        branch = meta[0].strip() if meta else ""
        head = meta[1].strip() if len(meta) > 1 else ""
        files: list[dict[str, str]] = []
        for line in porcelain.splitlines():
            if len(line) < 4:
                continue
            code = line[:2]
            path = line[3:].strip()
            if " -> " in path:
                path = path.split(" -> ", 1)[-1]
            kind = "modified"
            if code.strip() == "??":
                kind = "untracked"
            elif code[0] == "A" or code[1] == "A":
                kind = "added"
            elif code[0] == "D" or code[1] == "D":
                kind = "deleted"
            elif "R" in code:
                kind = "renamed"
            files.append({"path": path.replace("\\", "/"), "kind": kind, "code": code})
        return {
            "ok": True,
            "branch": branch,
            "head": head,
            "files": files,
            "clean": len(files) == 0,
        }

    def git_diff(self, rel_path: str = "") -> dict[str, Any]:
        base = self._require_root()
        if not (Path(base) / ".git").exists():
            return {"ok": False, "error": "not a git repository"}
        path = rel_path.replace("\\", "/").lstrip("/")
        if path:
            # validate path stays in workspace
            self._safe_join(base, path)
            cmd = f"git diff --no-color -- {path!s} ; git diff --no-color --cached -- {path!s}"
        else:
            cmd = "git diff --no-color ; git diff --no-color --cached"
        result = self.exec_cmd(cmd)
        text = (result.get("stdout") or "")[:200_000]
        return {"ok": True, "path": path, "diff": text}

    def git_commit(self, message: str) -> dict[str, Any]:
        base = self._require_root()
        if not (Path(base) / ".git").exists():
            return {"ok": False, "error": "not a git repository"}
        msg = (message or "").strip()
        if not msg:
            return {"ok": False, "error": "empty commit message"}
        # Escape for shell single quotes
        safe = msg.replace("'", "'\\''")
        add = self.exec_cmd("git add -A")
        if not add.get("ok") and add.get("exitCode", 1) != 0:
            return {"ok": False, "error": add.get("stderr") or add.get("error") or "git add failed"}
        commit = self.exec_cmd(f"git commit -m '{safe}'")
        if not commit.get("ok"):
            err = (commit.get("stderr") or commit.get("stdout") or commit.get("error") or "commit failed")
            return {"ok": False, "error": err.strip()[:500]}
        return {"ok": True, "message": msg, "stdout": (commit.get("stdout") or "")[:500]}

    def _snap_dir(self, workspace_root: str, snap_id: str) -> Path:
        sid = (snap_id or "").strip()
        if not re.fullmatch(r"snap-[a-f0-9]{12}", sid):
            raise ValueError("invalid snapshot id")
        key = hashlib.sha1(os.path.realpath(workspace_root).encode("utf-8")).hexdigest()[:16]
        return CACHE_DIR / key / sid

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
        if path == "/api/workspace/snapshot/create" and method == "POST":
            return 200, json_response(store.snapshot_create())
        if path.startswith("/api/workspace/snapshot/diff") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            return 200, json_response(store.snapshot_diff(str(qs.get("id", [""])[0])))
        if path == "/api/workspace/snapshot/restore" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.snapshot_restore(str(data.get("id", ""))))
        if path == "/api/workspace/snapshot/forget" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.snapshot_forget(str(data.get("id", ""))))
        if path.startswith("/api/workspace/snapshot/file-diff") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            return 200, json_response(
                store.snapshot_file_diff(str(qs.get("id", [""])[0]), str(qs.get("path", [""])[0]))
            )
        if path == "/api/workspace/git/status" and method == "GET":
            return 200, json_response(store.git_status())
        if path.startswith("/api/workspace/git/diff") and method == "GET":
            from urllib.parse import parse_qs, urlparse

            qs = parse_qs(urlparse(path).query)
            return 200, json_response(store.git_diff(str(qs.get("path", [""])[0])))
        if path == "/api/workspace/git/commit" and method == "POST":
            data = json.loads(body.decode("utf-8") if body else "{}")
            return 200, json_response(store.git_commit(str(data.get("message", ""))))
    except ValueError as error:
        return 400, json_response({"ok": False, "error": str(error)})
    except json.JSONDecodeError:
        return 400, json_response({"ok": False, "error": "invalid json"})
    return 404, json_response({"ok": False, "error": "not found"})
