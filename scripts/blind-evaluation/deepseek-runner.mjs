import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";

import {
  sha256File,
  validateDeepSeekCompletionEnvelope
} from "./opinion-contract.mjs";
import {
  parseJsonRejectDuplicateKeys
} from "./strict-json.mjs";

const [inputArg, outputArg, authorizationArg, mode = "run"] =
  process.argv.slice(2);
if (!inputArg || !outputArg || !authorizationArg) {
  throw new Error(
    "Usage: node deepseek-runner.mjs <blind-input> <output> <authorization> [preflight]"
  );
}

const inputPath = resolve(inputArg);
const outputPath = resolve(outputArg);
const authorizationPath = resolve(authorizationArg);
const blindInput = JSON.parse(await readFile(inputPath, "utf8"));
const inputSha256 = await sha256File(inputPath);

const block = async (code, networkAttempted = false, detailCode) => {
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(
    outputPath,
    `${JSON.stringify(
      {
        schemaVersion: "task-eval-002-deepseek-run-v1",
        status: "BLOCKED",
        code,
        ...(detailCode ? { detailCode } : {}),
        inputSha256,
        networkAttempted
      },
      null,
      2
    )}\n`,
    "utf8"
  );
};

let authorization;
try {
  authorization = JSON.parse(await readFile(authorizationPath, "utf8"));
} catch {
  await block("EXTERNAL_EGRESS_NOT_AUTHORIZED");
  if (mode === "preflight") process.exit(0);
  process.exit(3);
}

const authorized =
  authorization?.schemaVersion === "task-eval-002-egress-authorization-v1" &&
  authorization?.authorized === true &&
  authorization?.scope === "DEEPSEEK_EVALUATION_EGRESS" &&
  authorization?.sampleId === blindInput.sampleId &&
  authorization?.inputSha256 === inputSha256 &&
  authorization?.endpointHost === "api.deepseek.com" &&
  authorization?.model === "deepseek-v4-pro" &&
  typeof authorization?.purpose === "string" &&
  authorization.purpose.includes("TASK-EVAL-002") &&
  typeof authorization?.approvedBy === "string" &&
  authorization.approvedBy.trim().length > 0 &&
  Number.isFinite(Date.parse(authorization?.approvedAt)) &&
  Number.isFinite(Date.parse(authorization?.expiresAt)) &&
  Date.parse(authorization.approvedAt) <= Date.now() &&
  Date.parse(authorization.expiresAt) > Date.now();

if (!authorized) {
  await block("EXTERNAL_EGRESS_NOT_AUTHORIZED");
  if (mode === "preflight") process.exit(0);
  process.exit(3);
}

if (mode === "preflight") {
  await block("PREFLIGHT_AUTHORIZED_NO_NETWORK");
  process.exit(0);
}

const secret = process.env.DEEPSEEK_API_KEY;
if (!secret?.trim()) {
  await block("SECRET_MISSING");
  process.exit(4);
}

const prompt = [
  "你是合同语义盲评员。只根据给定 JSON 中的 submittedStructuredFields、reviewPoints 和 documentProjection 判断。",
  "必须输出 JSON，且只输出 JSON。不得输出推理过程或 Markdown。",
  '顶层必须严格为 {"opinions":[...]}，不得直接输出数组或增加其他顶层字段。',
  "opinions 必须为数组，并且为输入中的每个 reviewPointCode 恰好输出一项。",
  "每项必须且只能包含 reviewPointCode、opinion、confidence、candidateValues、evidenceQuotes、opaqueLocations、insufficiencyReason。",
  "confidence 必须是 0 到 1 的 JSON number；candidateValues、evidenceQuotes、opaqueLocations 必须是 JSON string array；insufficiencyReason 必须是 JSON string 或 null。",
  "opinion 仅允许 CONSISTENT、INCONSISTENT、NOT_ENOUGH_EVIDENCE、NOT_APPLICABLE。",
  "只能引用输入中存在的 opaque location ID；evidenceQuotes 中每个字符串必须逐字包含在所引用 location 的 text 或 cell text 中。",
  "NOT_ENOUGH_EVIDENCE 必须填写非空 insufficiencyReason；其他意见的 insufficiencyReason 使用 null。",
  "除 NOT_ENOUGH_EVIDENCE 外，evidenceQuotes 与 opaqueLocations 均不得为空；证据不足必须拒答，不得虚构。",
  JSON.stringify(blindInput)
].join("\n");

let response;
try {
  response = await fetch("https://api.deepseek.com/chat/completions", {
    method: "POST",
    redirect: "error",
    signal: AbortSignal.timeout(60_000),
    headers: {
      Authorization: `Bearer ${secret.trim()}`,
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({
      model: "deepseek-v4-pro",
      stream: false,
      response_format: { type: "json_object" },
      messages: [{ role: "user", content: prompt }]
    })
  });
} catch {
  await block("NETWORK_OR_TIMEOUT", true);
  process.exit(5);
}

if (!response.ok) {
  await block(
    response.status === 401 || response.status === 403
      ? "AUTHENTICATION_FAILED"
      : response.status === 429
        ? "RATE_LIMITED"
        : response.status >= 500
          ? "UPSTREAM_5XX"
          : "UPSTREAM_REJECTED",
    true
  );
  process.exit(6);
}

let responseText;
try {
  responseText = await response.text();
} catch {
  await block(
    "SCHEMA_INVALID_OR_EMPTY",
    true,
    "RESPONSE_BODY_READ_FAILED"
  );
  process.exit(7);
}

let envelope;
try {
  envelope = parseJsonRejectDuplicateKeys(responseText);
} catch {
  const trimmed = responseText.trimStart();
  const responseShape =
    trimmed.length === 0
      ? "EMPTY"
      : trimmed.startsWith("<")
        ? "HTML_LIKE"
        : trimmed.startsWith("{")
          ? "JSON_OBJECT_PREFIX"
          : trimmed.startsWith("[")
            ? "JSON_ARRAY_PREFIX"
            : "OTHER";
  const mediaType = response.headers
    .get("content-type")
    ?.split(";", 1)[0]
    ?.trim()
    ?.toLowerCase();
  await block("SCHEMA_INVALID_OR_EMPTY", true, "RESPONSE_BODY_NOT_JSON");
  const blocked = JSON.parse(await readFile(outputPath, "utf8"));
  await writeFile(
    outputPath,
    `${JSON.stringify(
      {
        ...blocked,
        responseMediaType: mediaType || "UNSPECIFIED",
        responseShape,
        responseLength: responseText.length
      },
      null,
      2
    )}\n`,
    "utf8"
  );
  process.exit(7);
}

let accepted;
try {
  accepted = validateDeepSeekCompletionEnvelope(blindInput, envelope);
} catch (error) {
  await block(
    "SCHEMA_INVALID_OR_EMPTY",
    true,
    error?.admissionCode ?? "UNKNOWN_VALIDATION_FAILURE"
  );
  process.exit(7);
}

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(
  outputPath,
  `${JSON.stringify(
    {
      schemaVersion: "task-eval-002-model-opinion-v1",
      status: "ACCEPTED",
      track: "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION",
      sampleId: blindInput.sampleId,
      evaluator: "deepseek-v4-pro",
      inputSha256,
      networkAttempted: true,
      finishReason: "stop",
      opinions: accepted.opinions
    },
    null,
    2
  )}\n`,
  "utf8"
);
