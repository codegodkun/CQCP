import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertTrustedInterpreterAfterUse,
  attestTrustedInterpreter,
  stableInterpreterEnvironment,
} from "./stable-interpreter-attestation.mjs";
import {
  parseJsonRejectDuplicateKeys,
} from "./strict-json.mjs";

const unsafeSegment = (segment) =>
  segment === "" ||
  segment === "." ||
  segment === ".." ||
  /[\0\r\n]/.test(segment);

const normalizeOperation = ({
  repoRoot,
  relativePath,
  requiredRoot,
  maxBytes = 65_536,
}) => {
  if (
    typeof relativePath !== "string" ||
    typeof requiredRoot !== "string" ||
    (requiredRoot !== "" && !requiredRoot.endsWith("/")) ||
    !Number.isSafeInteger(maxBytes) ||
    maxBytes <= 0 ||
    relativePath.includes("\\") ||
    relativePath.includes(":") ||
    path.posix.isAbsolute(relativePath) ||
    path.posix.normalize(relativePath) !== relativePath ||
    relativePath.split("/").some(unsafeSegment) ||
    !relativePath.startsWith(requiredRoot) ||
    relativePath.slice(requiredRoot.length).includes("/")
  ) {
    throw new Error("CAPABILITY_PATH_INVALID");
  }
  const resolvedRepo = path.resolve(repoRoot);
  const lexicalRoot =
    requiredRoot === ""
      ? resolvedRepo
      : path.resolve(
          resolvedRepo,
          ...requiredRoot.replace(/\/$/, "").split("/"),
        );
  const lexicalFile = path.resolve(
    resolvedRepo,
    ...relativePath.split("/"),
  );
  if (path.dirname(lexicalFile) !== lexicalRoot) {
    throw new Error("CAPABILITY_PATH_INVALID");
  }
  return {
    repoRoot: resolvedRepo,
    requiredRoot: lexicalRoot,
    childName: path.basename(lexicalFile),
    maxBytes,
  };
};

export const readStableBytesDirectChildren = (operations) => {
  if (
    !Array.isArray(operations) ||
    operations.length === 0 ||
    operations.length > 512
  ) {
    throw new Error("CAPABILITY_BATCH_INVALID");
  }
  const requests = operations.map(normalizeOperation);
  const batchBase64 = Buffer.from(
    JSON.stringify(requests),
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
          path.join(scriptRoot, "stable-file-windows.ps1"),
        ]
      : [
          path.join(scriptRoot, "stable-file-posix.py"),
        ];
  const maxBytes = requests.reduce(
    (total, request) => total + request.maxBytes,
    0,
  );
  const result = spawnSync(command, args, {
    encoding: "utf8",
    input: batchBase64,
    windowsHide: true,
    timeout: 60_000,
    maxBuffer: Math.min(
      128 * 1024 * 1024,
      Math.max(1_048_576, maxBytes * 3),
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
            /CQCP_STABLE_FILE_READ_FAILED:([A-Z0-9_:-]+)/,
          )?.[1]
        : null;
    throw new Error(
      category && category !== "UNCLASSIFIED"
        ? `CAPABILITY_STABLE_OPEN_FAILED:${category}`
        : "CAPABILITY_STABLE_OPEN_FAILED",
    );
  }
  let stableResults;
  try {
    stableResults = JSON.parse(result.stdout);
  } catch {
    throw new Error("CAPABILITY_STABLE_OPEN_FAILED");
  }
  if (
    !Array.isArray(stableResults) ||
    stableResults.length !== requests.length
  ) {
    throw new Error("CAPABILITY_STABLE_OPEN_FAILED");
  }
  const observedRuntimeVersions = new Set(
    stableResults.map((stable) => stable?.runtimeVersion),
  );
  if (
    observedRuntimeVersions.size !== 1 ||
    !observedRuntimeVersions.has(runtimeAttestation.runtimeVersion)
  ) {
    throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
  }
  assertTrustedInterpreterAfterUse(
    runtimeAttestation,
    stableResults[0]?.runtimeVersion,
  );
  return stableResults.map((stable, index) => {
    const max = requests[index].maxBytes;
    if (
      stable?.schemaVersion !== "cqcp-stable-file-read-v1" ||
      typeof stable.bytesBase64 !== "string" ||
      !Number.isSafeInteger(stable.size) ||
      stable.size < 0 ||
      stable.size > max ||
      typeof stable.fileIdentity !== "string" ||
      !stable.fileIdentity ||
      stable.runtimeVersion !== runtimeAttestation.runtimeVersion
    ) {
      throw new Error("CAPABILITY_STABLE_OPEN_FAILED");
    }
    const bytes = Buffer.from(stable.bytesBase64, "base64");
    if (
      bytes.length !== stable.size ||
      bytes.toString("base64") !== stable.bytesBase64
    ) {
      throw new Error("CAPABILITY_STABLE_OPEN_FAILED");
    }
    return { bytes, fileIdentity: stable.fileIdentity };
  });
};

export const readStableBytesDirectChild = (operation) =>
  readStableBytesDirectChildren([operation])[0];

export const readStableJsonDirectChild = (options) => {
  const { bytes } = readStableBytesDirectChild(options);
  if (
    bytes.length >= 3 &&
    bytes[0] === 0xef &&
    bytes[1] === 0xbb &&
    bytes[2] === 0xbf
  ) {
    throw new Error("CAPABILITY_BOM_REJECTED");
  }
  let text;
  try {
    text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new Error("CAPABILITY_UTF8_INVALID");
  }
  let value;
  try {
    value = parseJsonRejectDuplicateKeys(text);
  } catch {
    throw new Error("CAPABILITY_JSON_INVALID");
  }
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value)
  ) {
    throw new Error("CAPABILITY_JSON_INVALID");
  }
  return { bytes, value };
};
