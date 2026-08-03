# Codex Test / Security / Evidence Audit — GO

- Subject: `a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad`
- Manifest SHA-256: `5cd7820ea7fa87254e5f08f94aea434cf9d09bb5d60cc1b1f2f3074ca2a5630c`
- HEAD: `dfc24de439ff549db4f48c2407bb127aa5e2ede8`
- Tree: `a65a5e065a3d3a355a888ea432ff5c3614851428`
- Full diff SHA-256: `c3e966d7307412617841c97692aad9cf33b18aa6a361fea6a3873b66a59c4aec`
- Verdict: `GO`
- P0 / P1 / P2 / blocking: `0 / 0 / 0 / 0`
- Findings: none

The fresh `gpt-5.6-sol/xhigh`, `fork_turns="none"` auditor independently
re-ran the freeze verifier, Node `56/56`, the verification builder `1/1`,
and Java runtime seam `1/1` with all five Gradle tasks executed. It also
verified negative schema probes, blind chronology, Secret/raw Provider scans,
canonical verification bytes, zero-call controls, and preservation of prior
failed subjects.
