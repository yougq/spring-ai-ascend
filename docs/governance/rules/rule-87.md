---
rule_id: 87
title: "Status YAML Allowed Claim Module Name Truth"
level: L0
view: development
principle_ref: P-C
authority_refs: [ADR-0078, ADR-0079, "v2.0.0-rc6 post-response review P1-2"]
enforcer_refs: [E120]
status: active
kernel_cap: 8
kernel: |
  **Every `allowed_claim:` text value in `docs/governance/architecture-status.yaml` MUST NOT contain a current-tense reference to the pre-Phase-C module names `agent-platform` or `agent-runtime` (the latter NOT matching `agent-runtime-core`) outside an explicit historical marker (`historical`, `pre-ADR-NNNN`, `pre-Phase-C`, `consolidated into`, `merged into`, `was rooted`, `formerly`, `superseded`, `deprecated`, `archived`) within ±3 lines of the same claim. Operationalises rc6 post-response review P1-2 closure: ledger `allowed_claim:` text cannot drift to deleted module names while structured `repository_counts` correctly declares the post-ADR-0078 9-module topology.**
---

# Rule 87 — Status YAML Allowed Claim Module Name Truth

## Motivation

`docs/governance/architecture-status.yaml#repository_counts` is the structured single-source for module counts. Rule 82 (rc6, strengthened from rc5) enforces that numeric phrases in entrypoint docs match the structured block. But the same yaml file ALSO carries prose `allowed_claim:` strings on every capability row — and those strings can drift unchecked.

The 2026-05-18 rc6 post-response architecture review (finding P1-2 in `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`) found that:

- `architecture-status.yaml:1054` said "Service Layer (agent-platform HTTP edge + agent-runtime cognitive runtime)" — names two deleted modules in present tense.
- `architecture-status.yaml:1391` said "each of the 4 reactor modules" — count contradicts the canonical `reactor_modules: 9`.
- `architecture-status.yaml:1409` said "agent-runtime (kind:domain) declares 2 SPI packages" — names a deleted module.

The rc6 family-wide self-check found a fourth occurrence at line 720 (`platform_agent_runtime_independence` allowed_claim) that the reviewer's manual sweep missed.

The pattern is consistent: `allowed_claim:` text was authored during the pre-Phase-C era (when `agent-platform` and `agent-runtime` were separate modules) and never refreshed when ADR-0078 (Phase C consolidation) shipped. Rule 82 caught the numeric drift on entrypoint docs; Rule 86 caught the L0 ARCHITECTURE.md drift; Rule 87 catches the analogous drift inside this yaml ledger.

## Details

### Algorithm

For each line in `docs/governance/architecture-status.yaml`:

1. Match the line against `^\s+allowed_claim:\s*` (a yaml field assignment).
2. Extract the value (everything after the `:`); strip surrounding quotes if present.
3. Grep the value for `\bagent-platform\b` OR `\bagent-runtime\b` (negative lookahead on `agent-runtime-core` — the new shared-kernel module from ADR-0079).
4. If a stale match is found, scan ±3 lines of the yaml file for any of the historical markers listed in the kernel.
5. If no marker is found in the surrounding paragraph, fail with the offending line + the stale module name.

### Stale-pattern detection

The negative lookahead on `agent-runtime-core` is the important detail: ADR-0079 (2026-05-18) introduced a new module whose name starts with the substring `agent-runtime`. A naive substring search for `agent-runtime` would false-fire on every reference to the new module. The rule uses word-boundary regex (`\bagent-runtime\b`) so `agent-runtime-core` is NOT matched. Tools may use `grep -E 'agent-runtime\b' | grep -v 'agent-runtime-core'` as an equivalent pipeline.

### Marker convention

The marker set is intentionally the same family as Rule 84 + Rule 86 with two additions specific to yaml-ledger context: `archived` (for fully-archived rows pointing to `docs/archive/`) and `deprecated` (used by older capability rows).

### Why scope to allowed_claim only

`allowed_claim:` is the prose narration of each capability — the field most likely to contain free-form text that ages out. Other yaml fields (`implementation:`, `tests:`, `l0_decision:`) are structured path lists that can be cross-validated by other rules (Rule 25, Rule 28). Rule 87 narrowly targets the narrative drift surface.

## Activation

Activated 2026-05-18 by the v2.0.0-rc6 post-response architecture review response wave (v2.0.0-rc7). Enforcer E120. Closes P1-2 of `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`.

## Cross-references

- Rule 25 (Architecture-Text Truth) — Rule 87 is the ledger-yaml specialisation.
- Rule 80 (S2cCallbackSignal Historical-Only in Authority) — same marker-convention family for deleted-type references.
- Rule 82 (Baseline Metrics Single Source) — sibling numeric-agreement gate from rc6; Rule 82 covers entrypoint numeric phrases, Rule 87 covers ledger prose module names.
- Rule 86 (Root ARCHITECTURE Count + Path Truth) — companion gate from the same rc6-post wave; Rule 86 covers the Markdown-corpus side, Rule 87 covers the YAML-corpus side.
- ADR-0078 (Phase C consolidation, 2026-05-18) — origin of the deleted module names.
- ADR-0079 (Engine SPI + shared kernel extraction, 2026-05-18) — introduces `agent-runtime-core` (the negative-lookahead exemption).
- `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P1-2 — origin.
- `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md` — response document.
