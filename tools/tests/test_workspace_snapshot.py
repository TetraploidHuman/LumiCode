"""Automated tests for workspace snapshot / diff / restore and HTTP routes."""

from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

from workspace_api import WorkspaceStore, handle_workspace  # noqa: E402


class SnapshotStoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.mkdtemp(prefix="lumicode-snap-")
        self.root = Path(self._tmpdir)
        (self.root / "Main.kt").write_text("fun main() {}\n", encoding="utf-8")
        (self.root / "README.md").write_text("# demo\n", encoding="utf-8")
        (self.root / "nested").mkdir()
        (self.root / "nested" / "Util.kt").write_text("object Util\n", encoding="utf-8")
        self.store = WorkspaceStore()
        opened = self.store.open_root(str(self.root))
        self.assertTrue(opened["ok"], opened)

    def tearDown(self) -> None:
        shutil.rmtree(self._tmpdir, ignore_errors=True)

    def test_create_diff_restore_roundtrip(self) -> None:
        created = self.store.snapshot_create()
        self.assertTrue(created["ok"], created)
        sid = created["snapshotId"]
        self.assertTrue(sid.startswith("snap-"))
        self.assertGreaterEqual(created["fileCount"], 3)

        (self.root / "Main.kt").write_text("fun main() { println(1) }\n", encoding="utf-8")
        (self.root / "New.kt").write_text("class New\n", encoding="utf-8")
        (self.root / "README.md").unlink()

        diff = self.store.snapshot_diff(sid)
        self.assertTrue(diff["ok"], diff)
        kinds = {c["path"]: c["kind"] for c in diff["changes"]}
        self.assertEqual(kinds.get("Main.kt"), "modified")
        self.assertEqual(kinds.get("New.kt"), "added")
        self.assertEqual(kinds.get("README.md"), "deleted")
        self.assertEqual(diff["changeCount"], 3)

        restored = self.store.snapshot_restore(sid)
        self.assertTrue(restored["ok"], restored)
        self.assertGreaterEqual(restored["restored"], 2)
        self.assertGreaterEqual(restored["deleted"], 1)

        self.assertEqual((self.root / "Main.kt").read_text(encoding="utf-8"), "fun main() {}\n")
        self.assertTrue((self.root / "README.md").is_file())
        self.assertFalse((self.root / "New.kt").exists())

        forgotten = self.store.snapshot_forget(sid)
        self.assertTrue(forgotten["ok"], forgotten)
        missing = self.store.snapshot_diff(sid)
        self.assertFalse(missing["ok"])

    def test_unchanged_workspace_has_empty_diff(self) -> None:
        sid = self.store.snapshot_create()["snapshotId"]
        diff = self.store.snapshot_diff(sid)
        self.assertTrue(diff["ok"])
        self.assertEqual(diff["changes"], [])
        self.assertEqual(diff["changeCount"], 0)

    def test_invalid_snapshot_id_rejected(self) -> None:
        with self.assertRaises(ValueError):
            self.store._snap_dir(str(self.root), "../evil")
        bad = self.store.snapshot_diff("not-a-snap")
        self.assertFalse(bad["ok"])

    def test_skips_dot_and_build_dirs(self) -> None:
        (self.root / ".hidden.kt").write_text("x", encoding="utf-8")
        build = self.root / "build"
        build.mkdir()
        (build / "Out.kt").write_text("x", encoding="utf-8")
        files = self.store._list_files()
        self.assertNotIn(".hidden.kt", files)
        self.assertTrue(all(not f.startswith("build/") for f in files))


class SnapshotHttpTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.mkdtemp(prefix="lumicode-http-")
        self.root = Path(self._tmpdir)
        (self.root / "a.kt").write_text("a\n", encoding="utf-8")
        self.store = WorkspaceStore()
        self.store.open_root(str(self.root))

    def tearDown(self) -> None:
        shutil.rmtree(self._tmpdir, ignore_errors=True)

    def _post(self, path: str, body: dict | None = None) -> dict:
        raw = json.dumps(body or {}).encode("utf-8")
        status, payload = handle_workspace(self.store, "POST", path, raw)
        self.assertEqual(status, 200)
        return json.loads(payload.decode("utf-8"))

    def _get(self, path: str) -> dict:
        status, payload = handle_workspace(self.store, "GET", path, None)
        self.assertEqual(status, 200)
        return json.loads(payload.decode("utf-8"))

    def test_http_snapshot_endpoints(self) -> None:
        created = self._post("/api/workspace/snapshot/create")
        self.assertTrue(created["ok"])
        sid = created["snapshotId"]

        (self.root / "a.kt").write_text("b\n", encoding="utf-8")
        diff = self._get(f"/api/workspace/snapshot/diff?id={sid}")
        self.assertTrue(diff["ok"])
        self.assertEqual(diff["changes"][0]["kind"], "modified")

        restored = self._post("/api/workspace/snapshot/restore", {"id": sid})
        self.assertTrue(restored["ok"])
        self.assertEqual((self.root / "a.kt").read_text(encoding="utf-8"), "a\n")

        forgotten = self._post("/api/workspace/snapshot/forget", {"id": sid})
        self.assertTrue(forgotten["ok"])


class BasicWorkspaceHttpTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.mkdtemp(prefix="lumicode-basic-")
        self.root = Path(self._tmpdir)
        (self.root / "Hi.kt").write_text("hi\n", encoding="utf-8")
        self.store = WorkspaceStore()

    def tearDown(self) -> None:
        shutil.rmtree(self._tmpdir, ignore_errors=True)

    def test_open_tree_read_write(self) -> None:
        status, payload = handle_workspace(
            self.store,
            "POST",
            "/api/workspace/open",
            json.dumps({"path": str(self.root)}).encode("utf-8"),
        )
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(payload)["ok"])

        status, payload = handle_workspace(self.store, "GET", "/api/workspace/tree", None)
        tree = json.loads(payload)
        self.assertIn("Hi.kt", tree["files"])

        status, payload = handle_workspace(
            self.store, "GET", "/api/workspace/read?path=Hi.kt", None
        )
        self.assertEqual(json.loads(payload)["content"], "hi\n")

        status, payload = handle_workspace(
            self.store,
            "PUT",
            "/api/workspace/write",
            json.dumps({"path": "Hi.kt", "content": "bye\n"}).encode("utf-8"),
        )
        self.assertTrue(json.loads(payload)["ok"])
        self.assertEqual((self.root / "Hi.kt").read_text(encoding="utf-8"), "bye\n")


if __name__ == "__main__":
    unittest.main(verbosity=2)
