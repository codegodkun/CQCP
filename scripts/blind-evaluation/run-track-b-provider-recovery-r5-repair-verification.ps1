param(
    [string]$RepoRoot = "."
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required"
}

$root = (Resolve-Path -LiteralPath $RepoRoot).Path
$verifyRoot = Join-Path $root "outputs/task-eval-006/track-b-recovery-v1/verification-v2"
New-Item -ItemType Directory -Path $verifyRoot -Force | Out-Null

$nodeTests = @(
    "scripts/blind-evaluation/deepseek-secure-transport.test.mjs",
    "scripts/blind-evaluation/mvp002-standing-egress-grant.test.mjs",
    "scripts/blind-evaluation/freeze-track-b-provider-recovery-audit-package.test.mjs",
    "scripts/blind-evaluation/seal-track-b-provider-recovery-admission.test.mjs",
    "scripts/blind-evaluation/seal-track-b-provider-recovery-admission-revalidation.test.mjs",
    "scripts/blind-evaluation/seal-track-b-provider-recovery-diagnostic.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-admission-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-admission-runner.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-codex-revalidation-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-corpus.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-diagnostic-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-human-seal.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-opinion-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-projection.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-request-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-recovery-runner.test.mjs",
    "scripts/blind-evaluation/verify-track-b-provider-recovery-subject.test.mjs"
)

Push-Location $root
try {
    & node --test @nodeTests 2>&1 |
        Tee-Object -FilePath (Join-Path $verifyRoot "node-tests-phase-appropriate.console.log")
    if ($LASTEXITCODE -ne 0) {
        throw "Phase-appropriate Node tests failed: $LASTEXITCODE"
    }

    Push-Location (Join-Path $root "apps/api-server")
    try {
        & gradle test `
            --tests com.cqcp.apiserver.reviewengine.TrackBProviderRecoveryRuntimeContractTest `
            --rerun-tasks `
            --console=plain 2>&1 |
            Tee-Object -FilePath (Join-Path $verifyRoot "java-track-b-recovery-runtime.console.log")
        if ($LASTEXITCODE -ne 0) {
            throw "Recovery runtime seam test failed: $LASTEXITCODE"
        }
        $junit = Join-Path $root "apps/api-server/build/test-results/test/TEST-com.cqcp.apiserver.reviewengine.TrackBProviderRecoveryRuntimeContractTest.xml"
        if (-not (Test-Path -LiteralPath $junit -PathType Leaf)) {
            throw "Recovery runtime seam JUnit XML missing"
        }
        Copy-Item -LiteralPath $junit -Destination (Join-Path $verifyRoot "java-track-b-recovery-runtime.junit.xml") -Force
    }
    finally {
        Pop-Location
    }

    $sealPath = Join-Path $root "outputs/task-eval-006/track-b-recovery-v1/run-v1/codex-revalidation-v1/admission-seal-v2.json"
    $seal = Get-Content -LiteralPath $sealPath -Raw | ConvertFrom-Json
    $sealedAt = ([DateTimeOffset]$seal.sealedAt).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    & node scripts/blind-evaluation/seal-track-b-provider-recovery-admission-revalidation.mjs `
        . $sealedAt verify 2>&1 |
        Tee-Object -FilePath (Join-Path $verifyRoot "admission-revalidation-seal-verify.console.log")
    if ($LASTEXITCODE -ne 0) {
        throw "Revalidated admission seal verification failed: $LASTEXITCODE"
    }

    $outputFiles = Get-ChildItem -LiteralPath (Join-Path $root "outputs/task-eval-006") -Recurse -File |
        Where-Object { $_.FullName -notlike "*verification-v2*" -and $_.FullName -notlike "*verification\audit*" }
    $secretLikeMatches = 0
    $forbiddenProviderPayloadMatches = 0
    foreach ($file in $outputFiles) {
        $text = [IO.File]::ReadAllText($file.FullName)
        $secretLikeMatches += [regex]::Matches(
            $text,
            "(?i)(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{20,}"
        ).Count
        $forbiddenProviderPayloadMatches += [regex]::Matches(
            $text,
            '"(?:rawResponse|raw_request|raw_response|reasoning_content|authorizationHeader)"\s*:'
        ).Count
    }
    $leakLog = @(
        "secretLikeMatches=$secretLikeMatches",
        "forbiddenProviderPayloadMatches=$forbiddenProviderPayloadMatches",
        "scannedFiles=$($outputFiles.Count)",
        "priorExactSecretScanPreserved=true"
    ) -join "`n"
    [IO.File]::WriteAllText(
        (Join-Path $verifyRoot "secret-and-provider-leak-scan.console.log"),
        "$leakLog`n",
        [Text.UTF8Encoding]::new($false)
    )
    if ($secretLikeMatches -ne 0 -or $forbiddenProviderPayloadMatches -ne 0) {
        throw "Secret/provider payload leak scan failed"
    }

    $hashBoundFiles = Get-ChildItem -LiteralPath (Join-Path $root "outputs/task-eval-006") -Recurse -File |
        Where-Object { $_.Extension -in @(".json", ".md", ".txt") -and $_.FullName -notlike "*verification\audit*" }
    $filesContainingCR = 0
    foreach ($file in $hashBoundFiles) {
        if ([IO.File]::ReadAllBytes($file.FullName) -contains 13) {
            $filesContainingCR += 1
        }
    }
    $lineLog = "filesContainingCR=$filesContainingCR`nscannedFiles=$($hashBoundFiles.Count)`n"
    [IO.File]::WriteAllText(
        (Join-Path $verifyRoot "line-endings.console.log"),
        $lineLog,
        [Text.UTF8Encoding]::new($false)
    )
    if ($filesContainingCR -ne 0) {
        throw "Hash-bound evidence contains CR bytes"
    }

    $diffOutput = (& git diff --check 2>&1 | Out-String)
    $diffExitCode = $LASTEXITCODE
    [IO.File]::WriteAllText(
        (Join-Path $verifyRoot "git-diff-check.console.log"),
        "gitDiffCheckExitCode=$diffExitCode`n$diffOutput",
        [Text.UTF8Encoding]::new($false)
    )
    if ($diffExitCode -ne 0) {
        throw "git diff --check failed: $diffExitCode"
    }

    & node --test scripts/blind-evaluation/verify-track-b-provider-recovery-r5-repair-subject.test.mjs 2>&1 |
        Tee-Object -FilePath (Join-Path $verifyRoot "verification-builder-unit.console.log")
    if ($LASTEXITCODE -ne 0) {
        throw "Verification builder unit test failed: $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Output "TRACK_B_PROVIDER_RECOVERY_R5_REPAIR_VERIFICATION_COMMANDS_PASS"
