# Codex Test / Security Audit — NO_GO

- Subject: `47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3`
- Manifest SHA-256: `e83c7be5648c9724b441cf0b77d65906f022791c9f58bec75e95e38ca513517b`
- HEAD: `b3716385113528df5024316ed233fc11fd146a15`
- Tree: `e05c5853d1bce38ff603e3a7b18902b348ceafce`
- Full diff SHA-256: `f981b02dba29d362b51c56a83c6642485a4cd9a4e2aea19b081235a29d7ecab2`
- Verdict: `NO_GO`
- P0: `1`
- P1: `0`
- P2: `0`
- Blocking: `1`

## Verified facts

- PowerShell `7.6.3`; audit was read-only, offline, and did not rerun DeepSeek or read Secret/raw KEY/raw Provider response/reasoning.
- Manifest, subject identity, HEAD, tree, 320 changed-path records, and full diff SHA were independently recomputed.
- All 60 evidence Git blobs match frozen sizes and hashes.
- Verification reconstructs exactly: console manifest `8aba6687…`, result `1805feb1…`, 37 evidence records, 7 runs.
- Node `56/56`; verification builder `1/1`; Java seam JUnit `1/1`, no skip/failure/error; Gradle `5 actionable tasks: 5 executed`.
- DeepSeek claim/opinion remain the sealed reused bytes `600a821a…` / `e863596a…`; no DeepSeek rerun occurred.
- Admission remains pending: 9 provider calls, 9 Codex results, 9 DeepSeek results, 3 zero-call controls, six dimensions `9/9`, `providerAdmissionEstablished=false`.
- Secret-like/raw Provider/CR/diff gates independently recompute to zero/pass.
- PUBLIC remains `EVALUATION / enabled=false / is_default=false`; no runtime Provider, PUBLIC binding, A0/A1/A2, or production activation change is present.

## Finding

### P0 / blocking — CC projection re-exposed explicitly excluded content through the full diff

- Builder: `C:\tmp\build-track-b-cc-projection-once.mjs`
- Output: `C:\tmp\cqcp-cc-audit-47d84299-r7\allowed-subject.diff`
- The builder writes the complete `git diff --binary --full-index --no-renames 115be530… b371638…`
  into the package. Its SHA-256 is the frozen full diff `f981b02d…`; size is 2,341,126 bytes.
- Three files declared hash-only excluded are still present as plaintext JSON patches in that diff:
  - `outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json` (`194/0` lines)
  - `outputs/task-eval-006/track-b-recovery-v1/run-v1/admission-report.json` (`254/0` lines)
  - `outputs/task-eval-006/track-b-recovery-v1/run-v1/codex-revalidation-v1/admission-report-v2.json` (`283/0` lines)
- The same diff also contains five historical `human-ground-truth.json` files and five
  `human-ground-truth-review.md` files across TASK-EVAL-002 through TASK-EVAL-006.
- `excludedEvidenceContentAbsent=true` only checks that `files/<excluded-path>` is missing; it does not
  scan container files such as `allowed-subject.diff`. The receipt is therefore false.
- A textual boundary instruction cannot substitute for technical content isolation. The CC package already
  sent in this audit was capable of exposing forbidden ground-truth/comparison content, so the CC GO is invalid.

## Minimal remediation recommendation

Remove the full content diff from the CC package. Replace it with path/status/blob-hash inventory, or generate
content diff only from an explicit included/context allowlist. Add fail-closed tests proving that excluded and
forbidden changed-path content cannot occur anywhere in any package file or container. Then create a new package
and run an entirely fresh three-party audit; no result from this round may be reused.
