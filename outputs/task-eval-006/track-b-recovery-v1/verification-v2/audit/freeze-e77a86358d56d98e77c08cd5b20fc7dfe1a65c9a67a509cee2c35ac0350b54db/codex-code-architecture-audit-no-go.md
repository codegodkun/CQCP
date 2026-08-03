# Codex Code / Architecture Audit — NO_GO

- Subject: `e77a86358d56d98e77c08cd5b20fc7dfe1a65c9a67a509cee2c35ac0350b54db`
- Manifest SHA-256: `a713467b0358fb4ec1d8bcc09a84a26e72e325a224a57e7c36c47330b6471cf3`
- Base: `115be530480e2ff9b92a7076b2668c066a44ae5c`
- HEAD: `53766271736d71bedf4b8539d224bd951a2d4bbf`
- Tree: `2ffda89e764b52089d6da93bbb593c0733335237`
- Full diff SHA-256: `03001592a37ce2cf22d155546b21c9925bbe6c1d37c1fa9a8aad48b9bbb9ea69`
- Auditor: fresh `gpt-5.6-sol/xhigh`, `fork_turns=none`, `/root/r5_code_arch_audit`
- Verdict: `NO_GO`
- P0: `0`
- P1: `1`
- P2: `3`
- Blocking findings: `4`

## Findings

1. **P1 — Codex v4 revalidation envelope is not top-level fail-closed.**
   `track-b-provider-recovery-codex-revalidation-contract.mjs` validates known fields and nested opinions but does not reject additional top-level `finding` / `verdict` keys. A read-only negative mutation remained sealable as GO while the report asserted `modelProducedFindingOrVerdict=false`.

2. **P2 — DeepSeek evidence reuse is not derived from the prior admission seal.**
   `seal-track-b-provider-recovery-admission-revalidation.mjs` accepts caller-supplied DeepSeek evidence and declares reuse without comparing the prior seal's `deepSeekExecutionClaimSha256` and `deepSeekOpinionSha256`. A read-only mismatch mutation still sealed GO.

3. **P2 — Architecture / ADR / admission state contracts conflict.**
   `docs/ARCHITECTURE.md` retains the earlier prohibition on a sixth corpus while later incorporating ADR-026's bounded sixth recovery. ADR-026 also requires verification/freeze/three zero-finding audits before `ESTABLISHED`, while the current seal/result claim establishment before those audits finish.

4. **P2 — MVP task map records stale R5 verification facts.**
   `tasks/MVP_TASK_MAP.md` states Node `70/70` and references the old seal `2d879b13…`; the frozen R5 evidence is Node `54/54` with revalidated seal `927eb673…`.

## Independently verified positive facts

- Freeze verifier recomputed the subject, 310 changed paths and 50 evidence records.
- The Java seam uses `RuntimeEvidencePacketBuilder`; JUnit is `1/1` and Gradle records `5 actionable tasks: 5 executed`.
- Current model-facing packets exclude routing/diagnostic/identity/ground-truth fields.
- Current actual Codex and DeepSeek opinion files do not contain an extra Finding/verdict.
- Current DeepSeek claim/opinion hashes match both stored seals and were unchanged by the repair.
- No `src/main`, public API/OpenAPI, database migration, infra/workflow, A0/A1/A2 runtime or PUBLIC binding activation was introduced by this repair.

This report invalidates the entire three-party audit round. It must not be combined with any old or partial audit result.
