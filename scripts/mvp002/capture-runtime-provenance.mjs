import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const mode = process.argv[3] ?? "verify";
const requestedEvidenceRoot = path.resolve(
  process.argv[4] ??
    path.join(
      repoRoot,
      "outputs/task-mvp-002/audit/browser-rebuild-runtime"
    )
);
const evidenceRootPrefix = `${path.join(
  repoRoot,
  "outputs/task-mvp-002"
)}${path.sep}`;
assert.ok(
  `${requestedEvidenceRoot}${path.sep}`.startsWith(evidenceRootPrefix),
  "evidence root must remain under outputs/task-mvp-002"
);

const outputPath = path.join(
  requestedEvidenceRoot,
  "runtime-provenance.json"
);
const expectedComposeFiles = [
  "deploy/compose/compose.yml",
  "scripts/mvp002/compose.acceptance.override.yml"
];
const composeProject = process.argv[5] ?? "";
const composeFiles = process.argv.slice(6).map((filePath) =>
  filePath.replaceAll("\\", "/")
);
assert.equal(
  composeProject,
  "cqcp-mvp002-acceptance",
  "runtime provenance requires the acceptance Compose project"
);
assert.deepEqual(
  composeFiles,
  expectedComposeFiles,
  "runtime provenance must receive the executor's exact frozen Compose files"
);
const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    encoding: null,
    windowsHide: true,
    ...options
  });
  if (result.status !== 0) {
    throw new Error(
      `${command} ${args.join(" ")} failed: ${String(result.stderr)}`
    );
  }
  return result.stdout;
};
const relativePosix = (absolutePath) =>
  path.relative(repoRoot, absolutePath).split(path.sep).join("/");
const hashFile = (relativePath) =>
  sha256(fs.readFileSync(path.join(repoRoot, ...relativePath.split("/"))));

function currentSubject() {
  const baseHead = run("git", ["rev-parse", "HEAD"]).toString("utf8").trim();
  const branch = run("git", ["branch", "--show-current"])
    .toString("utf8")
    .trim();
  const trackedDiff = run("git", [
    "diff",
    "--binary",
    "--no-ext-diff",
    "HEAD",
    "--",
    ".",
    ":(exclude)outputs/**"
  ]);
  const untrackedPaths = run("git", [
    "ls-files",
    "--others",
    "--exclude-standard",
    "-z",
    "--",
    "."
  ])
    .toString("utf8")
    .split("\0")
    .filter(Boolean)
    .map((candidate) => candidate.split(path.sep).join("/"))
    .filter((candidate) => !candidate.startsWith("outputs/"))
    .sort((left, right) => left.localeCompare(right, "en"));
  const untracked = untrackedPaths.map((relativePath) => {
    const absolutePath = path.join(
      repoRoot,
      ...relativePath.split("/")
    );
    const bytes = fs.readFileSync(absolutePath);
    return {
      path: relativePath,
      size: bytes.length,
      sha256: sha256(bytes)
    };
  });
  const payload = {
    baseHead,
    branch,
    trackedDiffSha256: sha256(trackedDiff),
    trackedDiffBytes: trackedDiff.length,
    untracked
  };
  return {
    ...payload,
    sourceStateSha256: sha256(
      Buffer.from(JSON.stringify(payload), "utf8")
    )
  };
}

function captureRuntime() {
  fs.mkdirSync(requestedEvidenceRoot, { recursive: true });
  const bootJars = fs
    .readdirSync(path.join(repoRoot, "apps/api-server/build/libs"), {
      withFileTypes: true
    })
    .filter(
      (entry) =>
        entry.isFile() &&
        entry.name.endsWith(".jar") &&
        !entry.name.endsWith("-plain.jar")
    )
    .map((entry) =>
      path.join(repoRoot, "apps/api-server/build/libs", entry.name)
    );
  assert.equal(
    bootJars.length,
    1,
    "runtime provenance requires exactly one bootJar"
  );

  const composeArgs = [
    "compose",
    "-p",
    composeProject,
    ...composeFiles.flatMap((relativePath) => [
      "-f",
      path.join(repoRoot, ...relativePath.split("/"))
    ])
  ];
  const composeConfig = run("docker", [...composeArgs, "config"]);
  const resolvedConfigPath = path.join(
    requestedEvidenceRoot,
    "resolved-compose-config.yaml"
  );
  fs.writeFileSync(resolvedConfigPath, composeConfig);
  const services = {};
  for (const serviceName of ["api-server", "admin-web", "postgres"]) {
    const containerId = run("docker", [
      ...composeArgs,
      "ps",
      "-q",
      serviceName
    ])
      .toString("utf8")
      .trim();
    assert.match(
      containerId,
      /^[a-f0-9]{64}$/,
      `missing running container for ${serviceName}`
    );
    const imageId = run("docker", [
      "inspect",
      "--format",
      "{{.Image}}",
      containerId
    ])
      .toString("utf8")
      .trim();
    assert.match(
      imageId,
      /^sha256:[a-f0-9]{64}$/,
      `invalid image ID for ${serviceName}`
    );
    const inspect = JSON.parse(
      run("docker", ["inspect", containerId]).toString("utf8")
    );
    assert.equal(inspect.length, 1);
    const labels = inspect[0].Config?.Labels ?? {};
    assert.equal(labels["com.docker.compose.project"], composeProject);
    assert.equal(labels["com.docker.compose.service"], serviceName);
    assert.match(
      labels["com.docker.compose.config-hash"] ?? "",
      /^[a-f0-9]{64}$/
    );
    const networks = Object.keys(
      inspect[0].NetworkSettings?.Networks ?? {}
    ).sort((left, right) => left.localeCompare(right, "en"));
    assert.ok(networks.length > 0, `missing live networks for ${serviceName}`);
    services[serviceName] = {
      containerId,
      imageId,
      composeProject: labels["com.docker.compose.project"],
      composeService: labels["com.docker.compose.service"],
      composeConfigHash: labels["com.docker.compose.config-hash"],
      networks
    };
  }

  const bootJar = bootJars[0];
  const evidence = {
    schemaVersion: "task-mvp-002-runtime-provenance-v2",
    status: "PASS",
    capturedAt: new Date().toISOString(),
    subject: currentSubject(),
    bootJar: {
      path: relativePosix(bootJar),
      size: fs.statSync(bootJar).size,
      sha256: sha256(fs.readFileSync(bootJar))
    },
    compose: {
      project: composeProject,
      files: composeFiles.map((relativePath) => ({
        path: relativePath,
        sha256: hashFile(relativePath)
      })),
      resolvedConfig: {
        path: relativePosix(resolvedConfigPath),
        size: composeConfig.length,
        sha256: sha256(composeConfig)
      },
      services
    }
  };
  fs.writeFileSync(
    outputPath,
    `${JSON.stringify(evidence, null, 2)}\n`,
    "utf8"
  );
  return evidence;
}

function verifyRuntime() {
  const evidence = JSON.parse(fs.readFileSync(outputPath, "utf8"));
  assert.equal(
    evidence.schemaVersion,
    "task-mvp-002-runtime-provenance-v2"
  );
  assert.equal(evidence.status, "PASS");
  assert.equal(evidence.compose.project, composeProject);
  assert.deepEqual(
    evidence.compose.files.map((composeFile) => composeFile.path),
    composeFiles
  );
  assert.deepEqual(
    currentSubject(),
    evidence.subject,
    "browser runtime evidence no longer matches the current source subject"
  );
  for (const composeFile of evidence.compose.files) {
    assert.equal(
      hashFile(composeFile.path),
      composeFile.sha256,
      `bound compose file changed: ${composeFile.path}`
    );
  }
  const resolvedConfigPath = path.resolve(
    repoRoot,
    evidence.compose.resolvedConfig.path
  );
  assert.equal(
    `${resolvedConfigPath}${path.sep}`.startsWith(
      `${requestedEvidenceRoot}${path.sep}`
    ),
    true,
    "resolved Compose config escaped the evidence root"
  );
  const resolvedConfig = fs.readFileSync(resolvedConfigPath);
  assert.equal(resolvedConfig.length, evidence.compose.resolvedConfig.size);
  assert.equal(
    sha256(resolvedConfig),
    evidence.compose.resolvedConfig.sha256
  );
  for (const serviceName of ["api-server", "admin-web", "postgres"]) {
    assert.match(
      evidence.compose.services[serviceName].containerId,
      /^[a-f0-9]{64}$/
    );
    assert.match(
      evidence.compose.services[serviceName].imageId,
      /^sha256:[a-f0-9]{64}$/
    );
    assert.equal(
      evidence.compose.services[serviceName].composeProject,
      composeProject
    );
    assert.equal(
      evidence.compose.services[serviceName].composeService,
      serviceName
    );
    assert.match(
      evidence.compose.services[serviceName].composeConfigHash,
      /^[a-f0-9]{64}$/
    );
    assert.ok(evidence.compose.services[serviceName].networks.length > 0);
  }
  return evidence;
}

assert.ok(["capture", "verify"].includes(mode), "mode must be capture or verify");
const evidence = mode === "capture" ? captureRuntime() : verifyRuntime();
process.stdout.write(
  `${JSON.stringify({
    status: "PASS",
    mode,
    sourceStateSha256: evidence.subject.sourceStateSha256,
    bootJarSha256: evidence.bootJar.sha256,
    apiImageId: evidence.compose.services["api-server"].imageId,
    adminWebImageId: evidence.compose.services["admin-web"].imageId
  })}\n`
);
