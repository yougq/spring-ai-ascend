---
rule_id: 97
title: "Release-Note Numeric Truth"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0084, "rc10 category-sweep I-α-2"]
enforcer_refs: [E135, E136]
status: active
kernel_cap: 8
kernel: |
  **The LATEST release note under `docs/releases/*.md` (lex-sort `tail -1`) MUST NOT contain an absolute `<N> nodes` or `<M> edges` prose claim that disagrees with the live values in `docs/governance/architecture-graph.yaml#node_count` and `#edge_count`, unless the line carries a historical / `rc[N] snapshot` / `rc[N] correction` / `rc[N] first cut` / superseded marker within ±3 lines. Delta-formatted claims (`+N nodes / +M edges`) are exempt by syntax. Closes rc10 category-sweep I-α-2: the rc9 release note declared "360 nodes / 510 edges" while the live architecture-graph.yaml header was 369 / 520 — Rule 91 narrowly checked baseline_metrics.active_gate_checks; release-note prose drift was outside its scope.**
---

# Rule 97 — Release-Note Numeric Truth

## Motivation

The rc8 post-corrective review's P0-1 finding fixed the gate's executable manifest count mismatch (74 declared vs 102 actual), and rc9 added Rule 91 to enforce that `architecture-status.yaml#baseline_metrics.active_gate_checks` matches the canonical manifest. But Rule 91's scope was narrow: it only checked **one** baseline-metrics key. The same drift mechanism (declared prose vs live source) was still possible for every other numeric claim — including graph node/edge counts which Rule 91 never touched.

The rc10 category sweep (defect family I-α) found that the rc9 release note line 33 declared "360 nodes / 510 edges" while the live `architecture-graph.yaml` header was 369 / 520. The delta narrative "+12 nodes / +24 edges" also failed the arithmetic (rc8 baseline 348 + 12 = 360 ≠ 369). The release note prose was wrong at write time and no rule caught it.

Rule 97 closes this gap for the most likely future-recurrence surface: release-note graph counts.

## Algorithm

The gate identifies the LATEST release note (lex-sort `find docs/releases -maxdepth 1 -name '*.md' | sort | tail -1`). Older release notes are historical snapshots and exempt by construction — each captured the count at its wave time.

For the latest release note, the gate scans for the pattern `<N> nodes` and `<M> edges` (absolute claims, NOT preceded by `+` — delta-formatted claims like `+21 nodes / +34 edges` are exempt by syntax). For each absolute claim:

1. If `N` equals the live `architecture-graph.yaml#node_count` value → pass.
2. If `M` equals the live `architecture-graph.yaml#edge_count` value → pass.
3. If neither equals AND the line has a historical / `rc[N] correction` / `rc[N] snapshot` / `rc[N] first cut` / superseded marker within ±3 lines → pass (acceptable historical reference).
4. Otherwise → fail.

Lines inside fenced code blocks are excluded (gate runs are not authority surfaces).

## Why latest-only, not all release notes

Every prior release note is a frozen historical snapshot. The rc8 release note's "348 nodes" claim was correct at rc8 wave time. Requiring all release notes to carry per-wave snapshot markers would mean retrofitting hundreds of markers and the gate would still allow none-of-them to assert any truth. Latest-only ensures the CURRENT release-note prose can be trusted, which is what readers act on.

When a new release note ships, it becomes "latest" and Rule 97 starts enforcing its claims against then-current live values. The previous "latest" relaxes to historical snapshot status automatically.

## Why absolute vs delta distinction

Release notes commonly state both absolute counts ("369 nodes / 520 edges") and deltas ("+21 nodes / +34 edges since rc8"). Both formats use the word `nodes` and `edges`. The discriminating feature is the `+` prefix on deltas — Rule 97's awk pattern excludes any number with a `+` immediately preceding it. This avoids false positives on the delta narrative.

## Enforcement

Enforced by E135 (Gate Rule 97 — `release_note_numeric_truth`). Positive self-test: synthetic latest release note with `369 nodes / 520 edges` matching live → pass. Negative self-test: same prose declaring `360 nodes / 510 edges` with no marker → fail. Also positive: `360 nodes / 510 edges` followed by `rc10 correction: ...` within ±3 lines → pass.

## Activation

Activated 2026-05-19 by the v2.0.0-rc10 wave (rc8 post-corrective review category-sweep follow-up). Enforcer E135 + E136.

## Cross-references

- ADR-0084 — rc10 corpus-truth + prevention-widening authority record.
- Rule 91 — `baseline_metric_matches_executable_manifest` (Rule 97 extends the same mechanism to release-note prose).
- Rule 82 — baseline_metrics numeric-agreement (Rule 97 covers a parallel surface that Rule 82 didn't enumerate).
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` category-sweep follow-up — origin.
