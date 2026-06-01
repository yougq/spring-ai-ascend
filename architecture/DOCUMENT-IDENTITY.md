---
level: L0
view: governance
status: active
authority: "architecture/workspace.dsl + architecture/README.md"
document_role: identity_registry
source_of_truth: true
---

# Architecture Document Identity

Every architecture-adjacent document must be readable as one of these roles.
When roles conflict, the stricter and more canonical role wins.

## Role Vocabulary

| Role | Meaning | Can Override Canonical Architecture? |
|---|---|---|
| `canonical_model` | Machine-readable architecture model and validated graph. | Yes. This is the highest architecture authority. |
| `canonical_prose` | Accepted human-readable L0/L1/L2 architecture prose mounted under `architecture/docs/`. | Yes, when consistent with the model, contracts, ADRs, and generated facts. |
| `canonical_contract` | Accepted runtime contract under `docs/contracts/` and listed in the contract catalog. | Yes for runtime promises. |
| `accepted_decision` | Accepted ADR under `docs/adr/` or mirrored under `architecture/decisions/`. | Yes for decision rationale and supersession. |
| `governance_rule` | Active rule, enforcer, or status ledger under `docs/governance/`. | Yes for process, validation, and shipped/deferred status. |
| `generated_fact` | Deterministic fact extracted from code, tests, contracts, modules, or runtime config. | Yes for factual claims; generated facts beat prose when they disagree. |
| `proposal` | Draft design or delivery view not yet accepted. | No. It must be promoted before it becomes authoritative. |
| `evidence` | Review, release, assessment, or audit material. | No. It supports claims but does not create architecture truth by itself. |
| `historical` | Superseded or archived material. | No. It explains history only. |
| `navigation` | README or index material. | No, except to route readers to the proper authority. |

## Current Directory Identity

| Path | Role | Source of Truth |
|---|---|---|
| `architecture/workspace.dsl` | `canonical_model` | true |
| `architecture/docs/L0/` | `canonical_prose` | true |
| `architecture/docs/L1/` | `canonical_prose` | true |
| `architecture/docs/L2/` | `canonical_prose` | true when populated by accepted designs |
| `architecture/features/` | `canonical_model` support | true for authored DSL intent |
| `architecture/facts/generated/` | `generated_fact` | true for extracted facts |
| `architecture/generated/` | generated projection | true only as generated output |
| `architecture/decisions/` | `accepted_decision` mirror | true only as ADR mirror |
| `docs/contracts/` | `canonical_contract` | true for accepted/cataloged contracts |
| `docs/adr/` | `accepted_decision` | true |
| `docs/governance/` | `governance_rule` | true for active rules, enforcers, and ledgers |
| `docs/dfx/` | DFX evidence/status | true when referenced by module metadata and governance |
| `docs/architecture/` | `proposal` / `evidence` | false |
| `docs/logs/` | `evidence` | false |
| `docs/reviews/` | `evidence` | false |
| `docs/archive/` | `historical` | false |

## Frontmatter Contract

New or materially edited architecture-adjacent Markdown should include:

```yaml
status: active | accepted | draft | proposed | rejected | superseded | archived
document_role: canonical_model | canonical_prose | canonical_contract | accepted_decision | governance_rule | generated_fact | proposal | evidence | historical | navigation
source_of_truth: true | false
authority: "<canonical source or reason>"
superseded_by: "<path or ADR, if applicable>"
```

Generated files may use their existing generated banner/provenance instead of
this exact frontmatter, but their role must remain unambiguous.
