#!/usr/bin/env python3
"""Tests for the single-source rendering tooling (Rule G-13)."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parents[1]
GATE_LIB = REPO_ROOT / "gate" / "lib"
RENDER = GATE_LIB / "render_template.py"
LOADER = GATE_LIB / "load_render_context.py"
CHECKER = GATE_LIB / "check_template_render_idempotency.py"


def _import_module(name: str):
    sys.path.insert(0, str(GATE_LIB))
    try:
        return __import__(name)
    finally:
        sys.path.pop(0)


class RenderTemplateTests(unittest.TestCase):
    def setUp(self) -> None:
        os.environ.setdefault("LC_ALL", "C")
        self.render_template = _import_module("render_template")

    def test_self_test_passes(self) -> None:
        self.assertEqual(self.render_template._self_test(REPO_ROOT), 0)

    def test_deterministic_sort_filter(self) -> None:
        rendered = self.render_template.render(
            "{{ items | sorted | join(',') }}", {"items": ["c", "a", "b"]}
        )
        self.assertEqual(rendered, "a,b,c")

    def test_fp_filter_fixed_precision(self) -> None:
        rendered = self.render_template.render("{{ pi | fp(4) }}", {"pi": 3.14159})
        self.assertEqual(rendered, "3.1416")

    def test_strict_undefined_raises(self) -> None:
        with self.assertRaises(Exception):
            self.render_template.render("{{ never_set }}", {})

    def test_keep_trailing_newline(self) -> None:
        rendered = self.render_template.render("line\n", {})
        self.assertTrue(rendered.endswith("\n"))


class LoadRenderContextTests(unittest.TestCase):
    def setUp(self) -> None:
        self.loader = _import_module("load_render_context")

    def test_unknown_plugin_raises(self) -> None:
        with self.assertRaises(SystemExit):
            self.loader.load_context(
                "does-not-exist",
                REPO_ROOT,
                argparse.Namespace(),
            )

    def test_recurring_families_plugin_real_repo(self) -> None:
        ctx = self.loader.load_context(
            "recurring_families",
            REPO_ROOT,
            argparse.Namespace(),
        )
        self.assertIn("families", ctx)
        self.assertIsInstance(ctx["families"], list)
        self.assertGreater(ctx["family_count"], 0)
        # Sorted by id
        ids = [f.get("id", "") for f in ctx["families"]]
        self.assertEqual(ids, sorted(ids))

    def test_release_note_plugin_returns_baseline_keys(self) -> None:
        ctx = self.loader.load_context(
            "release_note",
            REPO_ROOT,
            argparse.Namespace(run_self_tests=False, include_maven_reports=False, seed=None),
        )
        for key in (
            "baseline_metrics",
            "live_metrics",
            "baseline_comparison",
            "repository",
        ):
            self.assertIn(key, ctx)


class CheckTemplateRenderIdempotencyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.checker = _import_module("check_template_render_idempotency")

    def test_vacuous_registry_passes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            registry = root / "docs" / "governance" / "templates" / "surface-classification.yaml"
            registry.parent.mkdir(parents=True)
            registry.write_text(
                yaml.safe_dump({"schema_version": 1, "templates": []}),
                encoding="utf-8",
            )
            rc = self.checker.check_all(root)
        self.assertEqual(rc, 0)

    def test_missing_registry_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            rc = self.checker.check_all(Path(td))
        self.assertEqual(rc, 1)

    def test_malformed_registry_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            registry = root / "docs" / "governance" / "templates" / "surface-classification.yaml"
            registry.parent.mkdir(parents=True)
            registry.write_text(
                yaml.safe_dump({"schema_version": 1}),  # missing templates
                encoding="utf-8",
            )
            rc = self.checker.check_all(root)
        self.assertEqual(rc, 1)

    def test_drift_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            tdir = root / "docs" / "governance" / "templates"
            tdir.mkdir(parents=True)
            template = tdir / "tiny.md.j2"
            template.write_text("HELLO {{ name }}\n", encoding="utf-8")
            output = tdir / "tiny.md"
            # Deliberately write a value that does not match render(template, ctx)
            output.write_text("HELLO drift\n", encoding="utf-8")
            # Register a one-off plugin that returns the right context
            loader = _import_module("load_render_context")

            def _tiny_plugin(repo, args):
                return {"name": "world"}

            loader.PLUGINS["__tiny__"] = _tiny_plugin
            try:
                registry = tdir / "surface-classification.yaml"
                registry.write_text(
                    yaml.safe_dump(
                        {
                            "schema_version": 1,
                            "templates": [
                                {
                                    "template": "docs/governance/templates/tiny.md.j2",
                                    "output": "docs/governance/templates/tiny.md",
                                    "context_loader": "__tiny__",
                                    "bucket": "templated",
                                }
                            ],
                        }
                    ),
                    encoding="utf-8",
                )
                rc = self.checker.check_all(root)
            finally:
                loader.PLUGINS.pop("__tiny__", None)
        self.assertEqual(rc, 2)

    def test_byte_identical_passes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            tdir = root / "docs" / "governance" / "templates"
            tdir.mkdir(parents=True)
            template = tdir / "tiny.md.j2"
            template.write_text("HELLO {{ name }}\n", encoding="utf-8")
            output = tdir / "tiny.md"
            output.write_text("HELLO world\n", encoding="utf-8")
            loader = _import_module("load_render_context")

            def _tiny_plugin(repo, args):
                return {"name": "world"}

            loader.PLUGINS["__tiny2__"] = _tiny_plugin
            try:
                registry = tdir / "surface-classification.yaml"
                registry.write_text(
                    yaml.safe_dump(
                        {
                            "schema_version": 1,
                            "templates": [
                                {
                                    "template": "docs/governance/templates/tiny.md.j2",
                                    "output": "docs/governance/templates/tiny.md",
                                    "context_loader": "__tiny2__",
                                    "bucket": "templated",
                                }
                            ],
                        }
                    ),
                    encoding="utf-8",
                )
                rc = self.checker.check_all(root)
            finally:
                loader.PLUGINS.pop("__tiny2__", None)
        self.assertEqual(rc, 0)


if __name__ == "__main__":
    unittest.main()
