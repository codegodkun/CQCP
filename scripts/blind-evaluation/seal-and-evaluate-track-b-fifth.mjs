import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  sealAndEvaluateTrackBFifth
} from "./seal-and-evaluate-track-b-holdout.mjs";

export { sealAndEvaluateTrackBFifth };

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoArg, evaluatedAtArg = new Date().toISOString(), mode = "create"] =
    process.argv.slice(2);
  const result = await sealAndEvaluateTrackBFifth({
    repoRoot: repoArg,
    evaluatedAt: evaluatedAtArg,
    mode
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
  if (result.providerAdmission === "NOT_ESTABLISHED") {
    process.exitCode = 2;
  }
}
