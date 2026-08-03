import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  TrackBHoldoutRunnerExit,
  runTrackBSuccessorDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";

export { runTrackBSuccessorDeepSeek };

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  try {
    const result = await runTrackBSuccessorDeepSeek();
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } catch (error) {
    if (error instanceof TrackBHoldoutRunnerExit) {
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
