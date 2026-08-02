import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  cp,
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  writeFile
} from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  validateTrackBEgressAuthorization
} from "./track-b-admission-authorization.mjs";
import {
  runTrackBAdmission
} from "./track-b-admission-deepseek-runner.mjs";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const runnerPath = path.resolve(
  scriptRoot,
  "track-b-admission-deepseek-runner.mjs"
);
const corpusGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);
const challengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-human-challenge.mjs"
);
const humanSealScriptPath = path.resolve(
  scriptRoot,
  "seal-track-b-human-ground-truth.mjs"
);
const dispatchGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-dispatch.mjs"
);
const egressChallengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-egress-challenge.mjs"
);
const codexOpinionSealerPath = path.resolve(
  scriptRoot,
  "seal-track-b-admission-codex-opinion.mjs"
);
const admissionSealerPath = path.resolve(
  scriptRoot,
  "seal-and-evaluate-track-b-admission.mjs"
);
const trustedPromptPath = path.resolve(
  scriptRoot,
  "track-b-admission-opinion-prompt.txt"
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const cleanDeepSeekEnvironment = (source = process.env) => {
  const environment = { ...source };
  for (const key of [
    "NODE_OPTIONS",
    "NODE_EXTRA_CA_CERTS",
    "SSL_CERT_FILE",
    "SSL_CERT_DIR",
    "OPENSSL_CONF",
    "NODE_USE_ENV_PROXY",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "ALL_PROXY",
    "http_proxy",
    "https_proxy",
    "all_proxy"
  ]) {
    delete environment[key];
  }
  delete environment.NODE_TLS_REJECT_UNAUTHORIZED;
  return environment;
};

test("Track B DeepSeek runner gates egress and persists only strict accepted opinion", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-runner-"));
  const extraRoots = [];
  try {
    const corpusCreatedAt = new Date(Date.now() - 120_000).toISOString();
    const challengeCreatedAt = new Date(Date.now() - 110_000).toISOString();
    const confirmedAt = new Date(Date.now() - 90_000).toISOString();
    const dispatchCreatedAt = new Date(Date.now() - 60_000).toISOString();
    let result = spawnSync(
      process.execPath,
      [corpusGeneratorPath, root, corpusCreatedAt],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    result = spawnSync(
      process.execPath,
      [
        challengeGeneratorPath,
        root,
        challengeCreatedAt,
        new Date(Date.now() + 120_000).toISOString(),
        "TBH-3123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);

    const runRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-run-v3"
    );
    const corpusRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2"
    );
    const promptRelativePath =
      "scripts/blind-evaluation/track-b-admission-opinion-prompt.txt";
    const promptPath = path.join(root, promptRelativePath);
    const corpusBytes = await readFile(path.join(corpusRoot, "corpus.json"));
    const draftBytes = await readFile(
      path.join(corpusRoot, "human-review-draft.json")
    );
    const reviewDocumentBytes = await readFile(
      path.join(corpusRoot, "human-ground-truth-review.md")
    );
    const humanChallengeBytes = await readFile(
      path.join(corpusRoot, "human-confirmation-challenge.json")
    );
    const draft = JSON.parse(draftBytes.toString("utf8"));
    const humanChallenge = JSON.parse(
      humanChallengeBytes.toString("utf8")
    );
    const decisions = draft.entries.map((entry) => ({
      caseId: entry.caseId,
      packetId: entry.packetId,
      expected: entry.proposedExpected
    }));
    const confirmationRelativePath =
      "outputs/task-eval-002/track-b-admission-corpus-v2/" +
      "human-confirmation.json";
    await writeFile(
      path.join(root, confirmationRelativePath),
      `${JSON.stringify({
        schemaVersion: "task-eval-002-track-b-human-confirmation-v1",
        confirmed: true,
        challengeNonce: humanChallenge.nonce,
        challengeSha256: sha256(humanChallengeBytes),
        corpusSha256: sha256(corpusBytes),
        humanReviewDraftSha256: sha256(draftBytes),
        humanReviewDocumentSha256: sha256(reviewDocumentBytes),
        decisionsSha256: sha256(
          Buffer.from(JSON.stringify(decisions), "utf8")
        ),
        decisions,
        confirmedBy: "TEST_HUMAN_REVIEWER",
        confirmedAt,
        confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
        confirmationStatement:
          `确认 ${humanChallenge.nonce} / ${humanChallenge.corpusSha256}`
      })}\n`
    );
    result = spawnSync(
      process.execPath,
      [
        humanSealScriptPath,
        root,
        confirmationRelativePath,
        "create"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    await mkdir(path.dirname(promptPath), { recursive: true });
    await copyFile(trustedPromptPath, promptPath);
    result = spawnSync(
      process.execPath,
      [
        dispatchGeneratorPath,
        root,
        dispatchCreatedAt,
        "/root/test_track_b_agent"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const inputPath = path.join(runRoot, "model-input.json");
    const inputBytes = await readFile(inputPath);
    const input = JSON.parse(inputBytes.toString("utf8"));
    const packets = input.packets;
    const dispatchPath = path.join(runRoot, "dispatch.json");
    const dispatchBytes = await readFile(dispatchPath);
    const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
    result = spawnSync(
      process.execPath,
      [
        egressChallengeGeneratorPath,
        root,
        new Date(Date.now() - 30_000).toISOString(),
        new Date(Date.now() + 120_000).toISOString(),
        "TBE-3123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const egressChallengeBytes = await readFile(
      path.join(
        root,
        ...dispatch.egressAuthorizationChallengePath.split("/")
      )
    );
    const egressChallenge = JSON.parse(
      egressChallengeBytes.toString("utf8")
    );
    const authorizationRelativePath = dispatch.egressAuthorizationPath;
    const missingAuthPath = path.join(
      root,
      ...authorizationRelativePath.split("/")
    );
    const blockedPath = path.join(runRoot, "blocked.json");
    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        blockedPath,
        dispatchPath,
        missingAuthPath,
        "preflight"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    let output = JSON.parse(await readFile(blockedPath, "utf8"));
    assert.equal(output.code, "EXTERNAL_EGRESS_NOT_AUTHORIZED");
    assert.equal(output.networkAttempted, false);

    const authorizationPath = missingAuthPath;
    const approvedAt = new Date(Date.now() - 1000).toISOString();
    const expiresAt = new Date(Date.now() + 60_000).toISOString();
    const authorization = {
      schemaVersion: "task-eval-002-track-b-egress-authorization-v2",
      authorized: true,
      scope: "DEEPSEEK_TRACK_B_ADMISSION_EGRESS",
      purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
      challengePath: dispatch.egressAuthorizationChallengePath,
      challengeSha256: sha256(egressChallengeBytes),
      challengeNonce: egressChallenge.nonce,
      modelInputSha256: sha256(inputBytes),
      providerCallSetSha256: egressChallenge.providerCallSetSha256,
      providerRequestBuilderVersion:
        egressChallenge.providerRequestBuilderVersion,
      providerCallCount: egressChallenge.providerCallCount,
      dispatchSha256: sha256(dispatchBytes),
      endpointHost: "api.deepseek.com",
      model: "deepseek-v4-pro",
      authorizedInputCount: 15,
      excludedZeroCallControlCount: 3,
      zeroCallControlsExcluded: true,
      humanGroundTruthExcludedFromPayload: true,
      findingOrVerdictExcludedFromPayload: true,
      oneTimeExecution: true,
      approvedBy: "TEST_HUMAN",
      approvedAt,
      expiresAt,
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `确认 ${egressChallenge.nonce} ${sha256(dispatchBytes)} ` +
        `${sha256(inputBytes)} ${egressChallenge.providerCallSetSha256} ` +
        "api.deepseek.com deepseek-v4-pro 6 15 3"
    };
    const authorizationBytes = Buffer.from(
      `${JSON.stringify(authorization)}\n`
    );
    assert.doesNotThrow(() =>
      validateTrackBEgressAuthorization({
        authorization,
        challenge: egressChallenge,
        challengeSha256: sha256(egressChallengeBytes),
        inputSha256: sha256(inputBytes),
        dispatchSha256: sha256(dispatchBytes),
        dispatchCreatedAt: dispatch.createdAt,
        validationTime: new Date().toISOString()
      })
    );
    await writeFile(
      authorizationPath,
      authorizationBytes
    );
    const preflightPath = path.join(runRoot, "preflight.json");
    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        preflightPath,
        dispatchPath,
        authorizationPath,
        "preflight"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    output = JSON.parse(await readFile(preflightPath, "utf8"));
    assert.equal(output.code, "PREFLIGHT_AUTHORIZED_NO_NETWORK");
    assert.equal(output.networkAttempted, false);

    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        path.join(runRoot, "forbidden-test-run.json"),
        dispatchPath,
        authorizationPath,
        "test-run"
      ],
      { encoding: "utf8" }
    );
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /mode must be run or preflight/);

    const injectedProductionPath = path.join(
      runRoot,
      "deepseek-opinion.json"
    );
    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        injectedProductionPath,
        dispatchPath,
        authorizationPath,
        "run"
      ],
      {
        encoding: "utf8",
        env: {
          ...cleanDeepSeekEnvironment(),
          DEEPSEEK_API_KEY: "TEST_ONLY_SECRET",
          NODE_OPTIONS: "--trace-warnings"
        }
      }
    );
    assert.notEqual(result.status, 0);
    assert.match(
      result.stderr,
      /DEEPSEEK_TRANSPORT_ENVIRONMENT_FORBIDDEN/
    );

    const productionEnv = cleanDeepSeekEnvironment();
    delete productionEnv.DEEPSEEK_API_KEY;
    delete productionEnv.NODE_OPTIONS;
    const productionPath = path.join(runRoot, "deepseek-opinion.json");
    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        productionPath,
        dispatchPath,
        authorizationPath,
        "run"
      ],
      { encoding: "utf8", env: productionEnv }
    );
    assert.equal(result.status, 4, result.stderr);
    const blockedDirectory = path.join(runRoot, "blocked");
    const blockedFiles = await readdir(blockedDirectory);
    assert.equal(blockedFiles.length, 1);
    output = JSON.parse(
      await readFile(path.join(blockedDirectory, blockedFiles[0]), "utf8")
    );
    assert.equal(output.code, "SECRET_MISSING");
    assert.equal(output.networkAttempted, false);
    await assert.rejects(
      readFile(productionPath),
      (error) => error.code === "ENOENT"
    );
    const claimPath = path.join(
      runRoot,
      "deepseek-execution-claim.json"
    );
    await assert.rejects(
      readFile(claimPath),
      (error) => error.code === "ENOENT"
    );

    result = spawnSync(
      process.execPath,
      [
        runnerPath,
        root,
        inputPath,
        productionPath,
        dispatchPath,
        authorizationPath,
        "run"
      ],
      { encoding: "utf8", env: productionEnv }
    );
    assert.equal(result.status, 4, result.stderr);
    const blockedFilesAfterRetry = await readdir(blockedDirectory);
    assert.equal(blockedFilesAfterRetry.length, 2);
    await assert.rejects(
      readFile(claimPath),
      (error) => error.code === "ENOENT"
    );

    const rawSecretSentinel =
      "CQCP_RAW_SECRET_SENTINEL_TRACK_B_RUNNER_20260729";
    const acceptedResponse = (body, responseId = "local-test-id") => {
      const outbound = JSON.parse(body.toString("utf8"));
      assert.deepEqual(outbound.thinking, { type: "disabled" });
      assert.equal(outbound.max_tokens, 1500);
      const providerInput = JSON.parse(outbound.messages[1].content);
      const payload = {
        opinions: providerInput.packets.map((packet) => ({
          packetId: packet.packetId,
          suggestedRole: packet.candidateRole,
          selectedOccurrenceIds: [
            packet.candidateOccurrences[0].occurrenceId
          ],
          selectedAnchorBlockIds: [
            packet.candidateOccurrences[0].sourceAnchor.blockId
          ],
          abstain: false,
          abstentionReason: null
        }))
      };
      return {
        status: 200,
        contentTypeClass: "APPLICATION_JSON",
        body: Buffer.from(
          JSON.stringify({
            id: responseId,
            model: "deepseek-v4-pro",
            created: 1785310000,
            choices: [
              {
                finish_reason: "stop",
                message: {
                  content: JSON.stringify(payload),
                  reasoning_content: responseId
                }
              }
            ]
          }),
          "utf8"
        )
      };
    };
    const clonedRunnerArgs = (cloneRoot) => {
      const cloneRunRoot = path.join(
        cloneRoot,
        "outputs/task-eval-002/track-b-admission-run-v3"
      );
      return [
        cloneRoot,
        path.join(cloneRunRoot, "model-input.json"),
        path.join(cloneRunRoot, "deepseek-opinion.json"),
        path.join(cloneRunRoot, "dispatch.json"),
        path.join(cloneRunRoot, "egress-authorization.json"),
        "run"
      ];
    };
    const crashRoots = [];
    for (const suffix of ["after-claim", "during-send", "after-response"]) {
      const cloneRoot = `${root}-${suffix}`;
      await cp(root, cloneRoot, { recursive: true });
      extraRoots.push(cloneRoot);
      crashRoots.push(cloneRoot);
    }
    const crashEnvironment = {
      ...productionEnv,
      DEEPSEEK_API_KEY: rawSecretSentinel
    };
    let crashTransportCalls = 0;
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[0]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          afterClaim: async () => {
            throw new Error("TEST_CRASH_AFTER_CLAIM");
          },
          transport: async () => {
            crashTransportCalls += 1;
            throw new Error("must not send");
          }
        }
      }),
      /TEST_CRASH_AFTER_CLAIM/
    );
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[0]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            crashTransportCalls += 1;
            throw new Error("must not replay");
          }
        }
      }),
      /already claimed/
    );
    assert.equal(crashTransportCalls, 0);

    let sendCrashCalls = 0;
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[1]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            sendCrashCalls += 1;
            throw new Error("TEST_CRASH_DURING_SEND");
          }
        }
      }),
      /TRACK_B_RUNNER_EXIT_5/
    );
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[1]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            sendCrashCalls += 1;
            throw new Error("must not replay");
          }
        }
      }),
      /already claimed/
    );
    assert.equal(sendCrashCalls, 1);

    let responseCrashCalls = 0;
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[2]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async ({ body }) => {
            responseCrashCalls += 1;
            return acceptedResponse(body, "TEST_RESPONSE_SENTINEL");
          },
          afterResponse: async () => {
            throw new Error("TEST_CRASH_AFTER_RESPONSE");
          }
        }
      }),
      /TRACK_B_RUNNER_EXIT_5/
    );
    await assert.rejects(
      runTrackBAdmission({
        argv: clonedRunnerArgs(crashRoots[2]),
        environment: crashEnvironment,
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            responseCrashCalls += 1;
            throw new Error("must not replay");
          }
        }
      }),
      /already claimed/
    );
    assert.equal(responseCrashCalls, 1);

    let transportCalls = 0;
    await runTrackBAdmission({
      argv: [
        root,
        inputPath,
        productionPath,
        dispatchPath,
        authorizationPath,
        "run"
      ],
      environment: {
        ...productionEnv,
        DEEPSEEK_API_KEY: rawSecretSentinel
      },
      dependencies: {
        resolveAddresses: async () => [
          { address: "8.8.8.8", family: 4 }
        ],
        transport: async ({ body }) => {
          transportCalls += 1;
          return acceptedResponse(body, rawSecretSentinel);
        }
      }
    });
    assert.equal(transportCalls, dispatch.providerCallCount);
    const acceptedOpinionText = await readFile(productionPath, "utf8");
    assert.doesNotMatch(acceptedOpinionText, new RegExp(rawSecretSentinel));
    const acceptedOpinion = JSON.parse(acceptedOpinionText);
    assert.equal(acceptedOpinion.status, "ACCEPTED");
    assert.equal(
      acceptedOpinion.providerResponses.every(
        (receipt) =>
          receipt.receiptStatus === "STRICT_SCHEMA_ACCEPTED" &&
          !("id" in receipt) &&
          !("requestId" in receipt) &&
          !("created" in receipt) &&
          !("model" in receipt)
      ),
      true
    );
    const claim = JSON.parse(await readFile(claimPath, "utf8"));
    assert.equal(
      claim.schemaVersion,
      "task-eval-002-track-b-deepseek-execution-claim-v2"
    );
    assert.match(claim.pinnedAddressSetSha256, /^[a-f0-9]{64}$/);

    await assert.rejects(
      runTrackBAdmission({
        argv: [
          root,
          inputPath,
          productionPath,
          dispatchPath,
          authorizationPath,
          "run"
        ],
        environment: {
          ...productionEnv,
          DEEPSEEK_API_KEY: rawSecretSentinel
        },
        dependencies: {
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            transportCalls += 1;
            throw new Error("must not send after claim");
          }
        }
      }),
      /already claimed/
    );
    assert.equal(transportCalls, dispatch.providerCallCount);

    const blindOpinions = input.packets.map((packet) => ({
      packetId: packet.packetId,
      suggestedRole: packet.candidateRole,
      selectedOccurrenceIds: [
        packet.candidateOccurrences[0].occurrenceId
      ],
      selectedAnchorBlockIds: [
        packet.candidateOccurrences[0].sourceAnchor.blockId
      ],
      abstain: false,
      abstentionReason: null
    }));
    const rawCodexOutputPath = path.join(
      runRoot,
      "codex-agent-output.json"
    );
    await writeFile(
      rawCodexOutputPath,
      `${JSON.stringify({ opinions: blindOpinions })}\n`
    );
    const codexStartedAt = new Date(Date.now() - 20_000).toISOString();
    const codexCompletedAt = new Date(Date.now() - 10_000).toISOString();
    result = spawnSync(
      process.execPath,
      [
        codexOpinionSealerPath,
        root,
        rawCodexOutputPath,
        "/root/test_track_b_agent",
        "test-track-b-runner-success-agent-id",
        codexStartedAt,
        codexCompletedAt
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    result = spawnSync(
      process.execPath,
      [
        admissionSealerPath,
        root,
        "seal",
        new Date(Date.now() + 1000).toISOString()
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const admissionSeal = JSON.parse(
      await readFile(path.join(runRoot, "opinion-seal.json"), "utf8")
    );
    assert.equal(
      admissionSeal.status,
      "BOTH_BLIND_MODEL_OPINIONS_SEALED_BEFORE_UNBLIND"
    );
  } finally {
    await rm(root, { recursive: true, force: true });
    await Promise.all(
      extraRoots.map((extraRoot) =>
        rm(extraRoot, { recursive: true, force: true })
      )
    );
  }
});
