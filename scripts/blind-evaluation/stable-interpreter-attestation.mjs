import { spawnSync } from "node:child_process";
import { existsSync, realpathSync, statSync } from "node:fs";

const sanitizedEnvironment = () => ({
  SystemRoot: process.env.SystemRoot,
  WINDIR: process.env.WINDIR,
  LANG: "C.UTF-8",
});

const supportedRuntimeVersion = (value) => {
  const match = /^(\d+)\.(\d+)(?:\.\d+)*(?:$)/.exec(value);
  if (!match) return false;
  const major = Number.parseInt(match[1], 10);
  const minor = Number.parseInt(match[2], 10);
  return process.platform === "win32"
    ? major >= 7
    : major > 3 || (major === 3 && minor >= 10);
};

const executableIdentity = (command) => {
  let stats;
  try {
    stats = statSync(command, { bigint: true });
  } catch {
    throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
  }
  if (!stats.isFile()) {
    throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
  }
  return [
    stats.dev,
    stats.ino,
    stats.size,
    stats.mtimeNs,
    stats.ctimeNs,
  ].join(":");
};

const resolveTrustedInterpreter = () => {
  const candidates =
    process.platform === "win32"
      ? ["C:\\Program Files\\PowerShell\\7\\pwsh.exe"]
      : ["/usr/bin/python3", "/usr/local/bin/python3"];
  for (const candidate of candidates) {
    if (!existsSync(candidate)) continue;
    try {
      return realpathSync.native(candidate);
    } catch {
      throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
    }
  }
  throw new Error("CAPABILITY_RUNTIME_UNSUPPORTED");
};

export const attestTrustedInterpreter = () => {
  const command = resolveTrustedInterpreter();
  const beforeIdentity = executableIdentity(command);
  const args =
    process.platform === "win32"
      ? [
          "-NoLogo",
          "-NoProfile",
          "-NonInteractive",
          "-Command",
          "$PSVersionTable.PSVersion.ToString()",
        ]
      : ["-c", "import platform; print(platform.python_version())"];
  const probe = spawnSync(command, args, {
    encoding: "utf8",
    windowsHide: true,
    timeout: 10_000,
    maxBuffer: 16_384,
    env: sanitizedEnvironment(),
  });
  const runtimeVersion =
    typeof probe.stdout === "string" ? probe.stdout.trim() : "";
  if (
    probe.status !== 0 ||
    probe.error ||
    !supportedRuntimeVersion(runtimeVersion) ||
    executableIdentity(command) !== beforeIdentity
  ) {
    throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
  }
  return Object.freeze({
    command,
    runtimeVersion,
    executableIdentity: beforeIdentity,
  });
};

export const assertTrustedInterpreterAfterUse = (
  attestation,
  observedRuntimeVersion,
) => {
  if (
    !attestation ||
    observedRuntimeVersion !== attestation.runtimeVersion ||
    executableIdentity(attestation.command) !==
      attestation.executableIdentity
  ) {
    throw new Error("CAPABILITY_RUNTIME_ATTESTATION_FAILED");
  }
};

export const stableInterpreterEnvironment = sanitizedEnvironment;
