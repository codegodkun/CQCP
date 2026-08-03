import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  buildTrackBProviderRecoveryDiagnosticArtifacts,
  serializeTrackBProviderRecoveryArtifact
} from "./track-b-provider-recovery-diagnostic-contract.mjs";

export async function prepareTrackBProviderRecoveryDiagnostic({
  repoRoot,
  createdAt
}) {
  const root = resolve(repoRoot);
  const artifacts = buildTrackBProviderRecoveryDiagnosticArtifacts({
    fifthInputBytes: await readFile(
      resolve(
        root,
        "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json"
      )
    ),
    promptBytes: await readFile(
      resolve(
        root,
        "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt"
      )
    ),
    grantBytes: await readFile(
      resolve(
        root,
        "scripts/blind-evaluation/mvp002-standing-egress-grant.json"
      )
    ),
    createdAt
  });

  for (const [name, value] of [
    ["input", artifacts.modelInput],
    ["callSet", artifacts.callSet],
    ["dispatch", artifacts.dispatch],
    ["receipt", artifacts.receipt]
  ]) {
    const path = resolve(root, ...artifacts.paths[name].split("/"));
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, serializeTrackBProviderRecoveryArtifact(value), {
      flag: "wx"
    });
  }

  return {
    status: "FROZEN_READY_FOR_NON_ADMISSION_DIAGNOSTIC",
    ...artifacts.hashes,
    callCount: artifacts.calls.length,
    networkCallPerformed: false
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", createdAt = new Date().toISOString()] =
    process.argv.slice(2);
  const result = await prepareTrackBProviderRecoveryDiagnostic({
    repoRoot,
    createdAt
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
