---
level: L0
view: governance
status: draft
authority: "triage register for docs/architecture; canonical authority remains ../../architecture/"
document_role: proposal
source_of_truth: false
canonical_authority: ../../architecture/workspace.dsl
---

# Draft Architecture Triage Register

This register tracks draft material in `docs/architecture/` that must be
reviewed before it can be promoted, archived, or deleted. It is deliberately not
an architecture authority.

## Promotion States

| State | Meaning |
|---|---|
| `pending_triage` | Needs review against `architecture/` before reuse. |
| `candidate_promote` | Likely useful, but not authoritative until rewritten into the canonical target. |
| `conflict` | Conflicts with canonical architecture or contains unresolved merge markers. |
| `archive_candidate` | Useful history, not a source for future implementation. |

## Current Directory Triage

| Path | State | Notes | Likely Target |
|---|---|---|---|
| `docs/architecture/l0/` | `pending_triage` | Draft L0 delivery tree. Some material may be useful, but L0 authority is `architecture/docs/L0/ARCHITECTURE.md`. | `architecture/docs/L0/`, `architecture/features/`, `docs/governance/`, or archive |
| `docs/architecture/l0/l1/agent-service/` | `conflict` | Duplicate/historical L1 copy. Contains unresolved merge markers and must not be used as canonical L1. | compare against `architecture/docs/L1/agent-service/`; then promote deltas or archive |
| `docs/architecture/l0/05-contracts/` | `candidate_promote` | Draft ICD and machine-readable contract sketches. Must not drive production until moved to `docs/contracts/` and cataloged. | `docs/contracts/`, ADR, tests |
| `docs/architecture/l0/10-governance/` | `candidate_promote` | A2D process ideas and documentation constraints. Needs reconciliation with active governance rules. | `docs/governance/` |
| `docs/architecture/l1/` | `pending_triage` | Standalone dated L1 design notes. Must be compared with canonical L1 modules. | `architecture/docs/L1/` or archive |
| `docs/architecture/trustworthy/` | `candidate_promote` | Cross-cutting trustworthy-AI assessment lens. Useful, but not a module-boundary authority. | accepted L2/security/trustworthy design, governance, or DFX evidence |

## Known Conflicts

| Conflict | Files | Handling |
|---|---|---|
| Unresolved merge markers in duplicate `agent-service` L1 material | `docs/architecture/l0/l1/agent-service/{development,logical,physical,process,scenarios}.md`, `docs/architecture/l0/l1/agent-service/05-contracts/spi-appendix.md` | Do not promote directly. Compare against canonical `architecture/docs/L1/agent-service/` and retain only accepted deltas. |
| Unresolved merge markers in canonical `agent-service` L1 material | `architecture/docs/L1/agent-service/{development,logical,physical,process,scenarios,spi-appendix}.md` | Needs semantic review before cleanup. Until resolved, these files are canonical by location but conflict-tainted. |
| Old authority wording for `docs/architecture/l0/l1/agent-service/` | historical/draft docs under `docs/architecture/` | Superseded by `architecture/docs/L1/agent-service/`. |
| Engine/bus SPI ownership drift in trustworthy assessment | `docs/architecture/trustworthy/l0-l1-decomposition.md` | Must be aligned with ADR-0158 before promotion. Neutral orchestration/engine SPI belongs to `agent-bus`; `agent-execution-engine` owns engine adapter SPI plus `EngineRegistry`/`EngineEnvelope`. |

## Review Questions

These need human architecture judgment before the next consolidation pass:

1. Should `TaskStateStore` become a canonical L1 concept, or should Run remain
   the only accepted execution/control aggregate until a formal ADR changes it?
2. Should `docs/architecture/trustworthy/` be promoted into
   `architecture/docs/L2/trustworthy-ai/`, into `docs/governance/`, or split
   across both?
3. Should draft machine-readable contracts under
   `docs/architecture/l0/05-contracts/machine-readable/` be discarded,
   promoted to `docs/contracts/`, or retained as proposal fixtures?
