import { resolve } from "node:path";

import { validateDeepSeekSeal } from "./deepseek-evidence-chain.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const validated = await validateDeepSeekSeal(repoRoot);

process.stdout.write(
  `${JSON.stringify({
    status: "DEEPSEEK_TRACK_A_AUTHORIZATION_AND_OPINIONS_VERIFIED",
    sealSha256: validated.sealSha256,
    sampleCount: validated.records.length,
    opinionCount: validated.records.reduce(
      (total, record) => total + record.opinionCount,
      0
    )
  })}\n`
);
