import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  TrackBHoldoutRunnerExit,
  runTrackBFinalDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";

export { runTrackBFinalDeepSeek };

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  try {
    const result = await runTrackBFinalDeepSeek();
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } catch (error) {
    if (error instanceof TrackBHoldoutRunnerExit) {
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
