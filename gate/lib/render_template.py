#!/usr/bin/env python3
"""Deterministic Jinja2 template renderer for single-source rendering.

Implements the rendering half of Rule G-13 (Single-Source Rendering
Coherence). Reads a `*.md.j2` template + a render-context yaml file,
produces the rendered `.md` output. Determinism contract:

- Sorted dict iteration (`sort_keys` filter available + Jinja env loader).
- `LC_ALL=C` enforced via env-var at process entry.
- No `datetime.now()`, no `time.time()`, no `uuid`, no `random` reachable
  from the template namespace.
- Fixed-precision float formatter (`%.3f`); raw `{{ float_value }}` is
  discouraged but not blocked at this layer (templates can call
  `{{ value | fp }}`).
- Newline-ending: LF; `keep_trailing_newline=True`.

Invocation:

    python -m gate.lib.render_template <template.md.j2> --data <ctx.yaml> \
        [--output <out.md>] [--check]

In `--check` mode the renderer compares the produced buffer to the on-disk
`--output` file (or `<template-without-.j2>` if `--output` is omitted) and
exits non-zero on mismatch with a diff snippet.

Authority: ADR-0119; rule card docs/governance/rules/rule-G-13.md.
"""

from __future__ import annotations

import argparse
import difflib
import os
import sys
from pathlib import Path
from typing import Any

import yaml


def _enforce_determinism_env() -> None:
    os.environ.setdefault("LC_ALL", "C")
    os.environ.setdefault("PYTHONHASHSEED", "0")


def repo_default() -> Path:
    return Path(__file__).resolve().parents[2]


def _load_yaml(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _fixed_precision_filter(value: Any, places: int = 3) -> str:
    try:
        return f"{float(value):.{places}f}"
    except (TypeError, ValueError):
        return str(value)


def _sorted_filter(value: Any) -> list[Any]:
    if isinstance(value, dict):
        return sorted(value.keys())
    if isinstance(value, (list, set, tuple)):
        return sorted(value)
    return [value]


def _build_environment() -> Any:
    try:
        import jinja2
    except ImportError as exc:  # pragma: no cover - clearer error
        raise SystemExit(
            "Jinja2 is required for render_template.py. Install via "
            "`pip install -r gate/requirements.txt`."
        ) from exc

    env = jinja2.Environment(
        loader=jinja2.BaseLoader(),
        autoescape=False,
        trim_blocks=True,
        lstrip_blocks=True,
        keep_trailing_newline=True,
        undefined=jinja2.StrictUndefined,
    )
    env.filters["fp"] = _fixed_precision_filter
    env.filters["sorted"] = _sorted_filter
    return env


def render(template_text: str, context: dict[str, Any]) -> str:
    env = _build_environment()
    template = env.from_string(template_text)
    return template.render(**context)


def _render_from_paths(template_path: Path, data_path: Path | None) -> str:
    template_text = template_path.read_text(encoding="utf-8")
    context: dict[str, Any]
    if data_path is None:
        context = {}
    else:
        loaded = _load_yaml(data_path)
        if not isinstance(loaded, dict):
            raise SystemExit(
                f"render context yaml must be a mapping at the root: {data_path}"
            )
        context = loaded
    return render(template_text, context)


def _default_output_for(template_path: Path) -> Path:
    name = template_path.name
    if name.endswith(".md.j2"):
        return template_path.with_name(name[: -len(".j2")])
    if name.endswith(".j2"):
        return template_path.with_name(name[: -len(".j2")])
    raise SystemExit(
        f"template file must end with .j2 to derive default output: {template_path}"
    )


def _diff_snippet(expected: str, actual: str, limit: int = 40) -> str:
    diff = list(
        difflib.unified_diff(
            expected.splitlines(keepends=True),
            actual.splitlines(keepends=True),
            fromfile="committed",
            tofile="rendered",
            n=2,
        )
    )
    if not diff:
        return "(no textual diff — likely trailing whitespace or encoding)"
    return "".join(diff[:limit])


def _self_test(repo: Path) -> int:
    """Smoke test: render a tiny template through every code path."""
    template = "Hello {{ name | upper }}; pi={{ pi | fp(2) }}; items={{ items | sorted | join(',') }}\n"
    ctx = {"name": "world", "pi": 3.14159, "items": ["b", "a", "c"]}
    out = render(template, ctx)
    expected = "Hello WORLD; pi=3.14; items=a,b,c\n"
    if out != expected:
        sys.stderr.write(
            f"render_template self-test FAILED\n  expected: {expected!r}\n  actual:   {out!r}\n"
        )
        return 1
    sys.stderr.write("render_template self-test OK\n")
    return 0


def main(argv: list[str]) -> int:
    _enforce_determinism_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("template", nargs="?", help="Path to <name>.md.j2 template")
    parser.add_argument("--data", help="Path to render-context yaml")
    parser.add_argument("--output", help="Path to write the rendered .md")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Render to a buffer and diff against the on-disk output; non-zero on mismatch",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run a minimal smoke test of the renderer and exit",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        return _self_test(repo_default())

    if not args.template:
        parser.error("template positional argument is required")

    template_path = Path(args.template)
    data_path = Path(args.data) if args.data else None
    rendered = _render_from_paths(template_path, data_path)

    output_path = Path(args.output) if args.output else _default_output_for(template_path)

    if args.check:
        if not output_path.is_file():
            sys.stderr.write(
                f"check mode: rendered output missing on disk: {output_path}\n"
            )
            return 2
        existing = output_path.read_text(encoding="utf-8")
        if existing != rendered:
            sys.stderr.write(
                f"check mode: render drift for {output_path}\n"
                f"{_diff_snippet(existing, rendered)}\n"
            )
            return 3
        sys.stderr.write(f"check mode: {output_path} byte-identical -- OK\n")
        return 0

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(rendered, encoding="utf-8")
    sys.stderr.write(f"wrote {output_path}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
