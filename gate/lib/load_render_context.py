#!/usr/bin/env python3
"""Plugin-dispatched render-context loader for single-source rendering.

Reads `docs/governance/templates/surface-classification.yaml`, picks the
appropriate `context_loader` plugin for the requested template, and emits
the render-context yaml that `gate/lib/render_template.py` consumes.

Plugin map (extended wave by wave):

    release_note           - W3 (this module). Currently delegates to
                             gate/lib/build_release_evidence.py for the
                             baseline/live/comparison block; release-note-
                             specific narrative blocks (forward claims,
                             family closures, authority refresh rows) are
                             read from a per-rc seed yaml under
                             gate/release-ci-evidence/.
    recurring_families     - W4. Reads docs/governance/recurring-defect-families.yaml.
    contract_catalog       - W5. Reads module-metadata + Java SPI scan.
    root_architecture      - W6. Reads pom.xml + module-metadata + architecture-status.
    l1_architecture        - W7. Reads <module>/src/main/java tree + spi_packages.
    readme_root            - W8. Reads architecture-status baseline_metrics.
    readme_gate            - W8. Reads architecture-status baseline_metrics.
    claude_md_index        - W9. Reads rule-card frontmatter.
    phase_contract_table   - W9. Reads rule-card frontmatter.

CLI:

    python -m gate.lib.load_render_context <plugin> [--output ctx.yaml] [args...]

Authority: ADR-0119; rule card docs/governance/rules/rule-G-13.md.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Callable

import yaml


REPO_DEFAULT = Path(__file__).resolve().parents[2]


def _load_yaml(path: Path) -> Any:
    if not path.is_file():
        return None
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


# ---------------------------------------------------------------------------
# Plugin: release_note (W3)
#
# Returns a render context shaped like the release-note template expects.
# Delegates the baseline + live + comparison block to
# gate/lib/build_release_evidence.py so we have a single metric-extraction
# implementation.
# ---------------------------------------------------------------------------
def plugin_release_note(repo: Path, args: argparse.Namespace) -> dict[str, Any]:
    sys.path.insert(0, str(repo / "gate" / "lib"))
    try:
        import build_release_evidence as bre  # type: ignore[import-not-found]
    finally:
        sys.path.pop(0)

    evidence = bre.build_evidence(
        repo,
        run_self_tests=bool(getattr(args, "run_self_tests", False)),
        include_maven_reports=bool(getattr(args, "include_maven_reports", False)),
    )

    seed: dict[str, Any] = {}
    if getattr(args, "seed", None):
        loaded = _load_yaml(Path(args.seed))
        if isinstance(loaded, dict):
            seed = loaded

    # Determinism: volatile fields (commit SHA, branch, generated_at_utc) MUST
    # be pinned in the seed for a release note that is checked into git. The
    # seed's `release.frozen_commit_sha` / `release.frozen_branch` /
    # `release.frozen_at_utc` override the live-derived values from
    # build_release_evidence so the rendered output is reproducible.
    release_seed = seed.get("release", {}) or {}
    repository = dict(evidence.get("repository", {}) or {})
    if release_seed.get("frozen_commit_sha"):
        repository["commit_sha"] = release_seed["frozen_commit_sha"]
    if release_seed.get("frozen_branch"):
        repository["branch"] = release_seed["frozen_branch"]
    # Dirty bit is build-time noise; pin to False for committed release notes.
    if release_seed.get("frozen_commit_sha"):
        repository["dirty"] = False

    generated_at = (
        release_seed.get("frozen_at_utc")
        or evidence.get("generated_at_utc")
        or ""
    )

    context: dict[str, Any] = {
        "schema_version": evidence.get("schema_version"),
        "generated_at_utc": generated_at,
        "repository": repository,
        "latest_release": evidence.get("latest_release", {}),
        "baseline_metrics": evidence.get("baseline_metrics", {}),
        "live_metrics": evidence.get("live_metrics", {}),
        "baseline_comparison": evidence.get("baseline_comparison", {}),
        "release_transaction": evidence.get("release_transaction", {}),
        # Narrative sections (seeded; templates iterate these lists)
        "release": release_seed,
        "fixes_completed": seed.get("fixes_completed", []),
        "pillar_status": seed.get("pillar_status", {}),
        "current_forward_claims": seed.get("current_forward_claims", []),
        "family_closures": seed.get("family_closures", []),
        "authority_refresh_rows": seed.get("authority_refresh_rows", []),
    }
    return context


# ---------------------------------------------------------------------------
# Plugin: recurring_families (W4 — implemented now so the test harness has
# a non-trivial plugin to verify)
# ---------------------------------------------------------------------------
def plugin_recurring_families(repo: Path, args: argparse.Namespace) -> dict[str, Any]:
    data = _load_yaml(repo / "docs" / "governance" / "recurring-defect-families.yaml") or {}
    if not isinstance(data, dict):
        raise SystemExit("recurring-defect-families.yaml must be a mapping at root")
    families = data.get("families") or []
    sorted_families = sorted(
        (f for f in families if isinstance(f, dict)),
        key=lambda f: (str(f.get("id", "")),),
    )
    return {
        "schema_version": data.get("schema_version"),
        "last_updated": str(data.get("last_updated") or ""),
        "families": sorted_families,
        "family_count": len(sorted_families),
    }


# ---------------------------------------------------------------------------
# Plugin registry. Entries land as their waves ship.
# ---------------------------------------------------------------------------
Plugin = Callable[[Path, argparse.Namespace], dict[str, Any]]

def plugin_noop(repo: Path, args: argparse.Namespace) -> dict[str, Any]:
    """Verbatim plugin: returns empty context. For .md.j2 templates that have
    no `{{ slot }}` placeholders (initial migration waves where the template
    is a verbatim seed of the existing authored .md). Byte-identical regen
    is trivially satisfied. Future sub-waves replace this with a real plugin
    once data extraction lands."""
    return {}


PLUGINS: dict[str, Plugin] = {
    "release_note": plugin_release_note,
    "recurring_families": plugin_recurring_families,
    "contract_catalog": plugin_noop,        # W5 initial; W5-extended swaps in SPI scan plugin
    "root_architecture": plugin_noop,       # W6 initial; W6-extended swaps in pom + status loader
    "l1_architecture": plugin_noop,         # W7 initial; W7-extended swaps in per-module tree scan
    "readme_root": plugin_noop,             # W8 initial; W8-extended swaps in baseline_metrics loader
    "readme_gate": plugin_noop,             # W8 initial; W8-extended swaps in rule-count loader
    "claude_md_index": plugin_noop,         # W9 initial; W9-extended swaps in rule-card frontmatter loader
    "phase_contract_table": plugin_noop,    # W9 initial; W9-extended swaps in rule-allocation loader
}


def load_context(plugin_name: str, repo: Path, args: argparse.Namespace) -> dict[str, Any]:
    if plugin_name not in PLUGINS:
        raise SystemExit(
            f"unknown context_loader plugin: {plugin_name!r}; "
            f"known: {sorted(PLUGINS.keys())}"
        )
    return PLUGINS[plugin_name](repo, args)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plugin", help=f"Plugin name; one of {sorted(PLUGINS.keys())}")
    parser.add_argument("--root", default=str(REPO_DEFAULT), help="Repository root")
    parser.add_argument("--output", help="Write context yaml to this file (default: stdout)")
    parser.add_argument(
        "--seed",
        help="Per-template narrative seed yaml (e.g. release-note hand-authored sections)",
    )
    parser.add_argument(
        "--run-self-tests",
        action="store_true",
        help="release_note plugin: run gate/test_architecture_sync_gate.sh for live count",
    )
    parser.add_argument(
        "--include-maven-reports",
        action="store_true",
        help="release_note plugin: extract maven_tests_green from Surefire/Failsafe XML",
    )
    args = parser.parse_args(argv)

    repo = Path(args.root).resolve()
    context = load_context(args.plugin, repo, args)
    rendered = yaml.safe_dump(context, sort_keys=True, allow_unicode=False)

    if args.output:
        out_path = Path(args.output)
        if not out_path.is_absolute():
            out_path = repo / out_path
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
