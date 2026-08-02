import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertTrustedInterpreterAfterUse,
  attestTrustedInterpreter,
  stableInterpreterEnvironment,
} from "./stable-interpreter-attestation.mjs";

const unsafeSegment = (segment) =>
  segment === "" ||
  segment === "." ||
  segment === ".." ||
  /[\\:/\0\r\n]/.test(segment);

const normalizeRoot = (repoRoot, requiredRoot) => {
  if (
    typeof requiredRoot !== "string" ||
    !requiredRoot.endsWith("/") ||
    path.posix.isAbsolute(requiredRoot) ||
    path.posix.normalize(requiredRoot) !== requiredRoot ||
    requiredRoot.split("/").slice(0, -1).some(unsafeSegment)
  ) {
    throw new Error("CAPABILITY_CREATE_ROOT_INVALID");
  }
  const resolvedRepo = path.resolve(repoRoot);
  const resolvedRoot = path.resolve(
    resolvedRepo,
    ...requiredRoot.slice(0, -1).split("/"),
  );
  const relativeRoot = path.relative(resolvedRepo, resolvedRoot);
  if (
    relativeRoot === ".." ||
    relativeRoot.startsWith(`..${path.sep}`) ||
    path.isAbsolute(relativeRoot)
  ) {
    throw new Error("CAPABILITY_CREATE_ROOT_INVALID");
  }
  return { resolvedRepo, resolvedRoot };
};

const normalizeFiles = (files) => {
  if (
    !Array.isArray(files) ||
    files.length === 0 ||
    files.length > 64
  ) {
    throw new Error("CAPABILITY_CREATE_BATCH_INVALID");
  }
  const names = new Set();
  return files.map(({ childName, bytes }) => {
    if (
      typeof childName !== "string" ||
      unsafeSegment(childName) ||
      !Buffer.isBuffer(bytes) ||
      bytes.length > 16 * 1024 * 1024 ||
      names.has(childName)
    ) {
      throw new Error("CAPABILITY_CREATE_FILE_INVALID");
    }
    names.add(childName);
    return {
      childName,
      bytesBase64: bytes.toString("base64"),
      size: bytes.length,
    };
  });
};

const runStableCreate = ({
  repoRoot,
  requiredRoot,
  directoryName,
  files,
}) => {
  const { resolvedRepo, resolvedRoot } = normalizeRoot(
    repoRoot,
    requiredRoot,
  );
  const normalizedFiles = normalizeFiles(files);
  if (
    directoryName !== null &&
    (typeof directoryName !== "string" || unsafeSegment(directoryName))
  ) {
    throw new Error("CAPABILITY_CREATE_DIRECTORY_INVALID");
  }
  const request = {
    schemaVersion: "cqcp-stable-file-create-request-v1",
    mode: directoryName === null ? "WRITE_EXISTING_ROOT" : "CREATE_ROOT",
    repoRoot: resolvedRepo,
    requiredRoot: resolvedRoot,
    directoryName,
    files: normalizedFiles,
  };
  const requestBase64 = Buffer.from(
    JSON.stringify(request),
    "utf8",
  ).toString("base64");
  const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
  const runtimeAttestation = attestTrustedInterpreter();
  const command = runtimeAttestation.command;
  const args =
    process.platform === "win32"
      ? [
          "-NoLogo",
          "-NoProfile",
          "-NonInteractive",
          "-File",
          path.join(scriptRoot, "stable-create-windows.ps1"),
        ]
      : [
          path.join(scriptRoot, "stable-create-posix.py"),
        ];
  const maxBytes = normalizedFiles.reduce(
    (total, entry) => total + entry.size,
    0,
  );
  const result = spawnSync(command, args, {
    encoding: "utf8",
    input: requestBase64,
    windowsHide: true,
    timeout: 60_000,
    maxBuffer: Math.min(
      128 * 1024 * 1024,
      Math.max(1_048_576, maxBytes * 2),
    ),
    env: stableInterpreterEnvironment(),
  });
  if (
    result.status !== 0 ||
    result.error ||
    typeof result.stdout !== "string"
  ) {
    const category =
      typeof result.stderr === "string"
        ? result.stderr.match(
            /CQCP_STABLE_FILE_CREATE_FAILED:([A-Z0-9_:]+)/,
          )?.[1]
        : null;
    throw new Error(
      category && category !== "UNCLASSIFIED"
        ? `CAPABILITY_STABLE_CREATE_FAILED:${category}`
        : "CAPABILITY_STABLE_CREATE_FAILED",
    );
  }
  let output;
  try {
    output = JSON.parse(result.stdout);
  } catch {
    throw new Error("CAPABILITY_STABLE_CREATE_FAILED");
  }
  assertTrustedInterpreterAfterUse(
    runtimeAttestation,
    output?.runtimeVersion,
  );
  if (
    output?.schemaVersion !== "cqcp-stable-file-create-result-v1" ||
    output.status !== "CREATED" ||
    output.mode !== request.mode ||
    !Array.isArray(output.files) ||
    output.files.length !== normalizedFiles.length ||
    output.files.some(
      (entry, index) =>
        entry.childName !== normalizedFiles[index].childName ||
        entry.size !== normalizedFiles[index].size ||
        typeof entry.fileIdentity !== "string" ||
        entry.fileIdentity.length === 0,
    )
  ) {
    throw new Error("CAPABILITY_STABLE_CREATE_FAILED");
  }
  return output;
};

export const writeStableBytesDirectChildren = ({
  repoRoot,
  requiredRoot,
  files,
}) =>
  runStableCreate({
    repoRoot,
    requiredRoot,
    directoryName: null,
    files,
  });

export const createStableDirectoryWithFiles = ({
  repoRoot,
  parentRoot,
  directoryName,
  files,
}) =>
  runStableCreate({
    repoRoot,
    requiredRoot: parentRoot,
    directoryName,
    files,
  });
