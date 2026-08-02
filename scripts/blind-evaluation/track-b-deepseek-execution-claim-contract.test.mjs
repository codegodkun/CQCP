import assert from "node:assert/strict";
import test from "node:test";

import {
  buildTrackBDeepSeekExecutionClaim,
  validateTrackBDeepSeekExecutionClaim
} from "./track-b-deepseek-execution-claim-contract.mjs";

const sha = (digit) => digit.repeat(64);

test("claim v2 builder and validator share exact identity and time contract", () => {
  const claim = buildTrackBDeepSeekExecutionClaim({
    dispatchSha256: sha("1"),
    modelInputSha256: sha("2"),
    providerCallSetSha256: sha("3"),
    providerCallCount: 6,
    egressAuthorizationSha256: sha("4"),
    egressChallengeSha256: sha("5"),
    pinnedAddressSetSha256: sha("6"),
    claimedAt: "2026-07-29T12:00:01.000Z"
  });
  assert.equal(
    validateTrackBDeepSeekExecutionClaim(claim, {
      dispatchSha256: sha("1"),
      modelInputSha256: sha("2"),
      providerCallSetSha256: sha("3"),
      providerCallCount: 6,
      egressAuthorizationSha256: sha("4"),
      egressChallengeSha256: sha("5"),
      pinnedAddressSetSha256: sha("6"),
      startedAt: "2026-07-29T12:00:00.000Z",
      completedAt: "2026-07-29T12:00:02.000Z"
    }),
    claim
  );
});

test("claim v2 rejects field, hash, binding and time drift", () => {
  const claim = buildTrackBDeepSeekExecutionClaim({
    dispatchSha256: sha("1"),
    modelInputSha256: sha("2"),
    providerCallSetSha256: sha("3"),
    providerCallCount: 6,
    egressAuthorizationSha256: sha("4"),
    egressChallengeSha256: sha("5"),
    pinnedAddressSetSha256: sha("6"),
    claimedAt: "2026-07-29T12:00:01.000Z"
  });
  assert.throws(
    () =>
      validateTrackBDeepSeekExecutionClaim({
        ...claim,
        upstreamRequestId: "forbidden"
      }),
    /CLAIM_INVALID/
  );
  assert.throws(
    () =>
      validateTrackBDeepSeekExecutionClaim(
        { ...claim, pinnedAddressSetSha256: sha("A") },
        { pinnedAddressSetSha256: sha("6") }
      ),
    /CLAIM_INVALID/
  );
  assert.throws(
    () =>
      validateTrackBDeepSeekExecutionClaim(claim, {
        dispatchSha256: sha("9")
      }),
    /BINDING_MISMATCH/
  );
  assert.throws(
    () =>
      validateTrackBDeepSeekExecutionClaim(claim, {
        startedAt: "2026-07-29T12:00:02.000Z",
        completedAt: "2026-07-29T12:00:03.000Z"
      }),
    /TIME_INVALID/
  );
});
