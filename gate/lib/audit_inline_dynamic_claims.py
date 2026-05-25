#!/usr/bin/env python3
"""W10 audit script — scan active corpus for residual inline dynamic claims.

Reports paths that contain hand-typed counts / paths / module names / SPI
FQNs / ADR ids that overlap with values declared in authority YAMLs or
rendered templates. Each finding is a candidate for:
  (a) move into a template slot (preferred — eliminates the drift class
      by construction)
  (b) grandfather in gate/inline-dynamic-claims-grandfathered.txt with a
      sunset_date annotation (acceptable when the surface is itself an
      authority source).

Authority: ADR-0119 W10 + Rule G-13.c.
"""

from __future__ import annotations

import argparse
import fnmatch
import re
import sys
from pathlib import Path
from typing import Iterable


REPO_DEFAULT = Path(__file__).resolve().parents[2]

# Patterns that look like "dynamic claims" worth flagging when they appear
# in markdown PROSE (not in code blocks, not in tables of authoritative
# baseline rows). The audit is intentionally lossy — it errs on the side
# of false positives that the grandfather list can dismiss.
DYNAMIC_PATTERNS = [
    (re.compile(r"\b(\d{2,4})\s+active\s+gate\s+rules\b"), "gate-rule-count"),
    (re.compile(r"\b(\d{2,4})\s+gate\s+self-tests?\b"), "self-test-count"),
    (re.compile(r"\b(\d{2,4})\s+enforcer\s+rows\b"), "enforcer-count"),
    (re.compile(r"\b(\d{2,4})\s+ADRs?\b"), "adr-count"),
    (re.compile(r"\b(\d{2,4})\s+§4\s+constraints\b"), "section-4-constraints"),
    (re.compile(r"\b(\d{2,4})\s+active\s+engineering\s+rules\b"), "engineering-rule-count"),
    (re.compile(r"\b(\d{2,4})\s+recurring\s+defect\s+families\b"), "family-count"),
    (re.compile(r"\b(\d{2,4})\s*-?\s*node\s*/\s*(\d{2,4})\s*-?\s*edge\b"), "graph-node-edge"),
]


def _load_grandfathered(repo: Path) -> list[str]:
    path = repo / "gate" / "inline-dynamic-claims-grandfathered.txt"
    if not path.is_file():
        return []
    patterns: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            patterns.append(line)
    return patterns


def _is_grandfathered(rel: str, patterns: Iterable[str]) -> bool:
    for pat in patterns:
        if fnmatch.fnmatch(rel, pat) or rel == pat:
            return True
    return False


def _walk_corpus(repo: Path) -> list[Path]:
    candidates: list[Path] = []
    for ext in ("*.md",):
        candidates.extend(repo.glob(f"**/{ext}"))
    return [
        p for p in candidates
        if not any(part in {".git", "node_modules", "target", "build", "dist"} for part in p.parts)
    ]


def audit(repo: Path, verbose: bool = False) -> int:
    grandfathered = _load_grandfathered(repo)
    findings = 0
    for path in sorted(_walk_corpus(repo)):
        rel = str(path.relative_to(repo)).replace("\\", "/")
        if _is_grandfathered(rel, grandfathered):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for pattern, label in DYNAMIC_PATTERNS:
            for match in pattern.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                snippet = text[max(0, match.start() - 20): match.end() + 20].replace("\n", " ")
                print(f"{rel}:{line_no}: {label}: ...{snippet.strip()}...")
                findings += 1
    if findings == 0:
        print("audit_inline_dynamic_claims: PASS (no inline dynamic claims outside grandfathered set)")
        return 0
    print(f"audit_inline_dynamic_claims: {findings} findings (each should be templated OR grandfathered)", file=sys.stderr)
    return 0  # Advisory-only in W10 phase; will become blocking after W10 retirement wave.


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(REPO_DEFAULT))
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)
    return audit(Path(args.root).resolve(), verbose=args.verbose)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
