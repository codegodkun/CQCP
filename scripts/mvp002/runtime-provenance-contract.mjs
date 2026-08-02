import assert from "node:assert/strict";

export const REDACTED_COMPOSE_VALUE = '"<redacted>"';
export const REDACTION_ALGORITHM = "cqcp-compose-sensitive-environment-v1";

const YAML_SCALAR = /^(?<prefix>\s*)(?<quoted>["']?)(?<key>[A-Za-z0-9_.-]+)\k<quoted>\s*:(?<spacing>\s*)(?<value>.*)$/;

export function isSensitiveEnvironmentKey(key) {
  const normalized = String(key).toUpperCase();
  if (/(?:^|_)(?:SECRET_REF|SECRET_REFERENCE)(?:_|$)/.test(normalized)) {
    return false;
  }
  return /(?:^|_)(?:TOKEN|PASSWORD|PASSPHRASE|API_KEY|PRIVATE_KEY|CREDENTIALS?)(?:_|$)/.test(
    normalized
  );
}

export function redactResolvedComposeConfig(bytes) {
  const text = Buffer.isBuffer(bytes) ? bytes.toString("utf8") : String(bytes);
  const redacted = text
    .split(/(?<=\n)/)
    .map((line) => {
      const ending = line.endsWith("\r\n")
        ? "\r\n"
        : line.endsWith("\n")
          ? "\n"
          : "";
      const body = ending.length === 0 ? line : line.slice(0, -ending.length);
      const match = YAML_SCALAR.exec(body);
      if (!match?.groups || !isSensitiveEnvironmentKey(match.groups.key)) {
        return line;
      }
      return `${match.groups.prefix}${match.groups.quoted}${match.groups.key}${match.groups.quoted}:${match.groups.spacing}${REDACTED_COMPOSE_VALUE}${ending}`;
    })
    .join("");
  const result = Buffer.from(redacted, "utf8");
  assertRedactedResolvedComposeConfig(result);
  return result;
}

export function assertRedactedResolvedComposeConfig(bytes) {
  const text = Buffer.isBuffer(bytes) ? bytes.toString("utf8") : String(bytes);
  let sensitiveCount = 0;
  for (const line of text.split(/\r?\n/)) {
    const match = YAML_SCALAR.exec(line);
    if (!match?.groups || !isSensitiveEnvironmentKey(match.groups.key)) {
      continue;
    }
    sensitiveCount += 1;
    assert.equal(
      match.groups.value.trim(),
      REDACTED_COMPOSE_VALUE,
      `resolved Compose config persisted sensitive value for ${match.groups.key}`
    );
  }
  assert.ok(
    sensitiveCount > 0,
    "resolved Compose config did not expose any redactable credential field"
  );
  return sensitiveCount;
}
