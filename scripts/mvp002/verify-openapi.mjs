import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";

import yaml from "js-yaml";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const yamlPath = path.join(repoRoot, "packages/api-contracts/openapi.yaml");
const jsonPath = path.join(repoRoot, "packages/api-contracts/openapi.json");
const yamlDocument = yaml.load(await readFile(yamlPath, "utf8"));
const jsonDocument = JSON.parse(await readFile(jsonPath, "utf8"));

assert.deepEqual(jsonDocument, yamlDocument);
process.stdout.write(
  `${JSON.stringify({
    status: "PASS",
    paths: Object.keys(jsonDocument.paths ?? {}).length,
    schemas: Object.keys(jsonDocument.components?.schemas ?? {}).length
  })}\n`
);
