import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { jsonBytes, sha256 } from "./track-b-holdout-contract.mjs";
import {
  buildTrackBFinalSourceArtifacts
} from "./prepare-track-b-final-source.mjs";

const OUTPUT_ROOT =
  "apps/api-server/src/test/resources/track-b-fifth-v1";

const ROLE_LABELS = new Map([
  ["PARTY_A", "合同甲方"],
  ["PARTY_B", "合同乙方"],
  ["CONTRACT_TOTAL_AMOUNT", "合同含税总金额"],
  ["TAX_AMOUNT", "合同税额"],
  ["PREPAYMENT_RATIO", "预付款比例"],
  ["PROGRESS_PAYMENT_RATIO", "进度款比例"],
  ["COMPLETION_PAYMENT_RATIO", "完工款比例"],
  ["SETTLEMENT_PAYMENT_RATIO", "结算款比例"],
  ["WARRANTY_RETENTION_RATIO", "质保金比例"]
]);

export function buildTrackBFifthSourceArtifacts() {
  const historical = buildTrackBFinalSourceArtifacts();
  const source = structuredClone(historical.source);
  source.schemaVersion = "task-eval-005-track-b-fifth-source-signals-v1";
  source.source = "INDEPENDENT_DEIDENTIFIED_SYNTHETIC_RUNTIME_SIGNALS";
  source.ruleSetVersion = "v20260803.fifth.1";
  source.identities = {
    MEDIUM: {
      taskId: "task-eval-005-track-b-fifth-medium",
      executionId: "execution-track-b-fifth-medium-v1"
    },
    CONFLICTED: {
      taskId: "task-eval-005-track-b-fifth-conflicted",
      executionId: "execution-track-b-fifth-conflicted-v1"
    },
    CONTROL: {
      taskId: "task-eval-005-track-b-fifth-control",
      executionId: "execution-track-b-fifth-control-v1"
    }
  };

  const proposalEntries = [];
  source.cases = source.cases.map((item, caseIndex) => {
    const next = structuredClone(item);
    next.caseId = item.caseId.replace(/^TBF-/, "TB5-");
    const roleLabel = ROLE_LABELS.get(next.candidateRole);
    assert.ok(roleLabel, `missing role label for ${next.candidateRole}`);
    const selectedIndex = caseIndex < 9
      ? caseIndex % next.occurrences.length
      : null;
    next.occurrences = next.occurrences.map((occurrence, occurrenceIndex) => {
      const number = String(occurrenceIndex + 1).padStart(2, "0");
      const candidateValue =
        `玄穹-${next.caseId}-${number}-${next.candidateRole}`;
      const target = selectedIndex === occurrenceIndex;
      return {
        ...occurrence,
        candidateValue,
        blockId: occurrence.blockId
          .replaceAll("tbf", "tb5")
          .replaceAll("TBF", "TB5"),
        evidenceText:
          next.caseId === "TB5-CON-003" && occurrenceIndex === 2
            ? "附属参考记录仅载明流程编号，不构成目标比例候选证据。"
            : target
              ? `经双方确认，本合同${roleLabel}为「${candidateValue}」。`
              : `附属参考记录（非${roleLabel}）载明代号「${candidateValue}」。`,
        sectionPath: [`第五套合成条款-${next.caseId}`],
        previewElementRef: occurrence.previewElementRef
          ?.replaceAll("tbf", "tb5")
          ?.replaceAll("TBF", "TB5") ?? null
      };
    });
    if (caseIndex < 9) {
      const selected = next.occurrences[selectedIndex];
      proposalEntries.push({
        caseId: next.caseId,
        proposedRationale:
          `“本合同${roleLabel}”直接标识目标 role；附属参考记录不属于该 role。`,
        proposedExpected: {
          suggestedRole: next.candidateRole,
          selectedOccurrenceIds: [
            `OCC-${String(selectedIndex + 1).padStart(3, "0")}`
          ],
          selectedAnchorBlockIds: [selected.blockId],
          abstain: false,
          abstentionReason: null
        }
      });
    } else {
      const control = next.caseId.includes("CTL-HIGH")
        ? ["DETERMINISTIC_HIGH_ZERO_CALL", "确定性 HIGH 必须保持 zero-call。"]
        : next.caseId.includes("CTL-INVALID")
          ? ["BUNDLE_INVALID", "不可靠 TABLE_CELL identity 必须 fail closed。"]
          : ["RELIABLE_ANCHOR_MISSING", "无可靠 anchor 必须保持 zero-call。"];
      proposalEntries.push({
        caseId: next.caseId,
        proposedRationale: control[1],
        proposedExpected: {
          suggestedRole: null,
          selectedOccurrenceIds: [],
          selectedAnchorBlockIds: [],
          abstain: true,
          abstentionReason: control[0]
        }
      });
    }
    return next;
  });

  const proposals = {
    schemaVersion: "task-eval-005-track-b-fifth-proposed-decisions-v1",
    status: "CODEX_PROPOSAL_NOT_HUMAN_GROUND_TRUTH",
    draftedBy: "CODEX",
    humanGroundTruthEstablished: false,
    entryCount: 12,
    entries: proposalEntries
  };
  assert.equal(source.cases.length, 12);
  assert.equal(proposals.entries.length, 12);
  return { source, proposals };
}

async function main() {
  const repoRoot = resolve(process.argv[2] ?? ".");
  const outputRoot = resolve(repoRoot, ...OUTPUT_ROOT.split("/"));
  const { source, proposals } = buildTrackBFifthSourceArtifacts();
  const sourceBytes = jsonBytes(source);
  const proposalBytes = jsonBytes(proposals);
  await mkdir(outputRoot, { recursive: true });
  await writeFile(resolve(outputRoot, "source-signals.json"), sourceBytes, {
    flag: "wx"
  });
  await writeFile(
    resolve(outputRoot, "proposed-decisions.json"),
    proposalBytes,
    { flag: "wx" }
  );
  process.stdout.write(`${JSON.stringify({
    sourceSha256: sha256(sourceBytes),
    proposalsSha256: sha256(proposalBytes),
    caseCount: source.caseCount,
    groundTruthIncluded: false
  })}\n`);
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  await main();
}
