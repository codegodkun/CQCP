import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  buildTrackBProviderRecoveryAdmissionArtifacts,
  serializeTrackBProviderRecoveryAdmissionArtifact
} from "./track-b-provider-recovery-admission-contract.mjs";

export async function prepareTrackBProviderRecoveryAdmission({
  repoRoot,
  createdAt
}) {
  const root = resolve(repoRoot);
  const artifacts = buildTrackBProviderRecoveryAdmissionArtifacts({
    corpusBytes: await readFile(
      resolve(root, "outputs/task-eval-006/track-b-recovery-v1/corpus.json")
    ),
    humanGroundTruthSealBytes: await readFile(
      resolve(
        root,
        "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json"
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
    await writeFile(
      path,
      serializeTrackBProviderRecoveryAdmissionArtifact(value),
      { flag: "wx" }
    );
  }

  return {
    status: "FROZEN_READY_FOR_FORMAL_ADMISSION",
    ...artifacts.hashes,
    callCount: artifacts.calls.length,
    zeroCallControlCount: artifacts.controls.length,
    networkCallPerformed: false
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", createdAt = new Date().toISOString()] =
    process.argv.slice(2);
  const result = await prepareTrackBProviderRecoveryAdmission({
    repoRoot,
    createdAt
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
