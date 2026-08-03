# CC AUDIT — R7 Track B Provider Recovery

> `INVALIDATED_BY_CODEX_TEST_SECURITY_P0`: this CC session received an isolation package whose
> `allowed-subject.diff` re-exposed hash-only excluded ground-truth/comparison content. Its reported GO is
> preserved as historical output only and cannot satisfy the three-party gate.

- Subject: `47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3`
- Manifest SHA-256: `e83c7be5648c9724b441cf0b77d65906f022791c9f58bec75e95e38ca513517b`
- Base: `115be530480e2ff9b92a7076b2668c066a44ae5c`
- HEAD: `b3716385113528df5024316ed233fc11fd146a15`
- Tree: `e05c5853d1bce38ff603e3a7b18902b348ceafce`
- Full diff SHA-256: `f981b02dba29d362b51c56a83c6642485a4cd9a4e2aea19b081235a29d7ecab2`
- Verdict: `GO`
- Counts: P0 `0` / P1 `0` / P2 `0` / blocking `0`

## Evidence partition

- Included evidence: 57; all present under `files/`.
- Hash-only excluded: 3 (`human-ground-truth.json`, `run-v1/admission-report.json`,
  `codex-revalidation-v1/admission-report-v2.json`); content absent, paths/sizes/hashes/reasons auditable.
- Subject evidence total: 60.
- Source context, not evidence: 5, explicitly declared and disjoint.
- Package files: 72; `PACKAGE_SHA256SUMS.txt` covers all 71 content files.

## Verified facts

1. All package identity fields match the manifest and content receipt. Failed subjects remain preserved.
2. R5 findings are closed: exact Codex opinion keys, fail-closed negative tests, prior DeepSeek
   claim/opinion hash recomputation, ADR-026 sixth-corpus boundary, and current pending-admission state.
3. R6 transport findings are closed: included files are frozen-HEAD Git blob bytes; the nine previously
   tainted governance files contain zero CR bytes; the 57+3=60 evidence partition and five source-context
   files are complete, explicit, and disjoint.
4. Evaluation chronology is monotonic and machine-validated from human seal through verification.
5. DeepSeek claim/opinion are reused R4 bytes; no network call was repeated during repair.
6. The 9 eligible calls plus three zero-call controls are bound consistently; Codex and DeepSeek opinions
   both contain exactly nine entries and agree packet-for-packet.
7. Java recovery runtime seam was forced with `--rerun-tasks`; Gradle reports five executed tasks and
   JUnit reports 1/1 with no skip, failure, or error.
8. Secret and forbidden Provider payload scans are zero; excluded sensitive comparison content is absent.
9. Admission remains pending triple audit and CI; A0/A1/A2, TASK-028/031/032, and Production Ready remain false.
10. Scope contains no main runtime, DB migration, Provider activation, or PUBLIC binding change.

## Findings

None.

## Environment limitation

The read-only CC session had no Shell tool, so it could not independently recompute SHA-256. It used
triple-authority hash cross-checking plus direct CR-byte detection. CC explicitly classified this as an
environment limitation, not a finding.
