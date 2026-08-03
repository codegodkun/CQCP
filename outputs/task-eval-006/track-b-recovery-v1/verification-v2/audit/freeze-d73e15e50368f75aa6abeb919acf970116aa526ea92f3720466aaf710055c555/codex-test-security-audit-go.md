# Codex Test / Security / Evidence Audit — R10 GO

- Subject: `d73e15e50368f75aa6abeb919acf970116aa526ea92f3720466aaf710055c555`
- Manifest SHA-256: `845bf692144081c37e5938f756ed9c895a5ee14054f81fdf3b9029aa576af05d`
- HEAD: `fa64271ad2da5994e1d02eb4fa9c2375b80f4f28`
- Tree: `3a180069d4759acae0669b287bede29cdefff751`
- Full diff SHA-256: `ef21f51e7027a00b08ba8609eaf850649a5f118cd5a3a93c7ffa4d3080926339`
- Verdict: `GO`
- P0: `0`
- P1: `0`
- P2: `0`
- Blocking: `0`

## Verified facts

- Freeze verifier、335 changed records 与 75 项 evidence 全部独立重算一致。
- R5-R9 的 50/54/60/65/70 项历史 evidence 均零缺失、零 mismatch，未组合旧 verdict。
- R10 RED `4 != 5` 可重建，当前定向测试 `1/1` GREEN；正式 Node `56/56`。
- Java seam 独立重跑 `1/1`，Gradle `5 executed`，无 `UP-TO-DATE`。
- 9 calls + 3 zero-call controls、27 项 hash chain、12 项时序检查均无失败。
- Secret-like/raw Provider 为 `0/0`、CR 为 `0`、full-range diff check 为 `0`。
- 双方意见无 Finding/verdict；PUBLIC runtime 未被修改，admission 仍 pending CI。

## Findings

None.
