param(
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required."
}

$repo = [System.IO.Path]::GetFullPath($RepoRoot)
$evidenceRoot = Join-Path $repo "outputs/task-mvp-002/core-audit/verification"
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
$runs = [System.Collections.Generic.List[object]]::new()

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Invoke-Logged {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [scriptblock]$Command
    )

    $logPath = Join-Path $evidenceRoot "$Name.log"
    $startedAt = [DateTimeOffset]::UtcNow
    $lines = [System.Collections.Generic.List[string]]::new()
    $thrown = $null
    Push-Location -LiteralPath $WorkingDirectory
    try {
        & $Command 2>&1 | ForEach-Object {
            $line = $_.ToString()
            $lines.Add($line)
            $line | Out-Host
        }
        $exitCode = $LASTEXITCODE
        if ($null -eq $exitCode) {
            $exitCode = 0
        }
    } catch {
        $thrown = $_
        $lines.Add($_.ToString())
        if ($null -eq $exitCode -or $exitCode -eq 0) {
            $exitCode = 1
        }
    } finally {
        [System.IO.File]::WriteAllLines(
            $logPath,
            $lines,
            [System.Text.UTF8Encoding]::new($false)
        )
        Pop-Location
    }
    $runs.Add([ordered]@{
        name = $Name
        command = $Command.ToString().Trim()
        workingDirectory = [System.IO.Path]::GetRelativePath(
            $repo,
            $WorkingDirectory
        ).Replace("\", "/")
        startedAt = $startedAt.ToString("O")
        finishedAt = [DateTimeOffset]::UtcNow.ToString("O")
        exitCode = $exitCode
        logPath = [System.IO.Path]::GetRelativePath(
            $repo,
            $logPath
        ).Replace("\", "/")
        logSha256 = Get-Sha256 $logPath
    })
    if ($null -ne $thrown) {
        throw "Verification command '$Name' threw: $($thrown.Exception.Message)"
    }
    if ($exitCode -ne 0) {
        throw "Verification command '$Name' failed with exit code $exitCode."
    }
}

function Read-BackendTestCounts {
    $testResultRoot = Join-Path $repo "apps/api-server/build/test-results/test"
    $testXml = @(Get-ChildItem -LiteralPath $testResultRoot -Filter "TEST-*.xml")
    if ($testXml.Count -lt 1) {
        throw "No backend test XML files were produced."
    }
    $counts = [ordered]@{
        suites = $testXml.Count
        tests = 0
        failures = 0
        errors = 0
        skipped = 0
    }
    foreach ($file in $testXml) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName
        $counts.tests += [int]$xml.testsuite.tests
        $counts.failures += [int]$xml.testsuite.failures
        $counts.errors += [int]$xml.testsuite.errors
        $counts.skipped += [int]$xml.testsuite.skipped
    }
    return $counts
}

function Read-LoggedTestCount {
    param(
        [string]$LogName,
        [string]$Pattern
    )
    $logPath = Join-Path $evidenceRoot $LogName
    $text = Get-Content -LiteralPath $logPath -Raw
    $plainText = [regex]::Replace(
        $text,
        [char]0x1b + "\[[0-?]*[ -/]*[@-~]",
        ""
    )
    if ($plainText -notmatch $Pattern) {
        throw "Unable to parse test count from $LogName."
    }
    return [int]$Matches[1]
}

function Read-EvidenceReferences {
    $required = @(
        "outputs/task-034-mvp-e2e-acceptance-v3/r7-evidence-seal.json",
        "outputs/task-eval-002/freeze-manifest.json",
        "outputs/task-eval-002/opinion-seal.json",
        "outputs/task-eval-002/deepseek-opinion-seal.json",
        "outputs/task-eval-002/unblind-report.json",
        "outputs/task-eval-002/track-b-admission-run-v3/admission-report.json",
        "outputs/task-eval-002/model-provider-gate.json",
        "outputs/task-mvp-002/browser-evidence-current/browser-assertions.json",
        "outputs/task-mvp-002/core-audit/verification/compose-acceptance-summary.json"
    )
    return @($required | ForEach-Object {
        $absolute = Join-Path $repo $_
        if (-not (Test-Path -LiteralPath $absolute -PathType Leaf)) {
            throw "Required Core evidence is missing: $_"
        }
        [ordered]@{
            path = $_
            size = (Get-Item -LiteralPath $absolute).Length
            sha256 = Get-Sha256 $absolute
        }
    })
}

$coreNodeTests = @(
    "scripts/blind-evaluation/blind-evidence-chain.test.mjs",
    "scripts/blind-evaluation/deepseek-evidence-chain.test.mjs",
    "scripts/blind-evaluation/deepseek-secure-transport.test.mjs",
    "scripts/blind-evaluation/opinion-contract.test.mjs",
    "scripts/blind-evaluation/prepare-track-b-admission-corpus.test.mjs",
    "scripts/blind-evaluation/prepare-track-b-admission-dispatch.test.mjs",
    "scripts/blind-evaluation/prepare-track-b-human-challenge.test.mjs",
    "scripts/blind-evaluation/prepare-track-b-identity-migration-challenge.test.mjs",
    "scripts/blind-evaluation/provider-gate-decision.test.mjs",
    "scripts/blind-evaluation/seal-and-evaluate-track-b-admission.test.mjs",
    "scripts/blind-evaluation/seal-track-b-human-ground-truth.test.mjs",
    "scripts/blind-evaluation/seal-track-b-identity-migration.test.mjs",
    "scripts/blind-evaluation/seam-mainline-gate.test.mjs",
    "scripts/blind-evaluation/stable-capability-create.test.mjs",
    "scripts/blind-evaluation/stable-capability-file.test.mjs",
    "scripts/blind-evaluation/stable-interpreter-attestation.test.mjs",
    "scripts/blind-evaluation/strict-json.test.mjs",
    "scripts/blind-evaluation/track-b-admission-authorization.test.mjs",
    "scripts/blind-evaluation/track-b-admission-deepseek-runner.test.mjs",
    "scripts/blind-evaluation/track-b-admission-opinion-contract.test.mjs",
    "scripts/blind-evaluation/track-b-deepseek-execution-claim-contract.test.mjs",
    "scripts/blind-evaluation/track-b-opinion-contract.test.mjs",
    "scripts/blind-evaluation/track-b-provider-request-contract.test.mjs",
    "scripts/mvp002/core-subject.test.mjs",
    "scripts/mvp002/freeze-core-audit-package.test.mjs",
    "scripts/validate-review-assets.test.mjs"
)
foreach ($testPath in $coreNodeTests) {
    if (-not (Test-Path -LiteralPath (Join-Path $repo $testPath) -PathType Leaf)) {
        throw "Core Node test allowlist entry is missing: $testPath"
    }
}

$databaseName = "cqcp_mvp002_core_" + [guid]::NewGuid().ToString("N")
if ($databaseName -notmatch "^cqcp_mvp002_core_[a-f0-9]{32}$") {
    throw "Unsafe verification database name."
}
$databaseCreated = $false

& docker exec cqcp-postgres-1 `
    psql -U cqcp -d postgres -v ON_ERROR_STOP=1 `
    -c "CREATE DATABASE $databaseName" | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Failed to create isolated PostgreSQL verification database."
}
$databaseCreated = $true

$env:CQCP_DB_URL = "jdbc:postgresql://localhost:54329/$databaseName"
$env:CQCP_DB_USERNAME = "cqcp"
$env:CQCP_DB_PASSWORD = "cqcp"
Remove-Item Env:\DEEPSEEK_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:\CQCP_DEEPSEEK_API_KEY -ErrorAction SilentlyContinue

try {
    Invoke-Logged "core-scope-test" $repo {
        node --test scripts/mvp002/core-subject.test.mjs
    }
    Invoke-Logged "formal-r7-evidence" $repo {
        node scripts/mvp002/verify-r7-evidence.mjs . verify
    }
    $formalR7Evidence = Get-Content -LiteralPath (
        Join-Path $repo "outputs/task-034-mvp-e2e-acceptance-v3/r7-evidence-seal.json"
    ) -Raw | ConvertFrom-Json

    Invoke-Logged "d1-combined-443" (Join-Path $repo "apps/api-server") {
        gradle test --no-daemon --rerun-tasks `
            --tests "com.cqcp.apiserver.reviewengine.ConsistencyCandidateCollectorTest" `
            --tests "com.cqcp.apiserver.reviewengine.ConsistencySetCollectorTest" `
            --tests "com.cqcp.apiserver.reviewengine.MinimalCandidateResolverTest" `
            --tests "com.cqcp.apiserver.reviewengine.ParserBackedReviewInputPreparerEvidenceTest" `
            --tests "com.cqcp.apiserver.reviewengine.MinimalReviewEngineTest" `
            --tests "com.cqcp.apiserver.wordparser.DocxWordParserSpikeTest" `
            --tests "com.cqcp.apiserver.reviewengine.ConsistencyRuntimeExecutionActivationTest" `
            --tests "com.cqcp.apiserver.reviewengine.RuntimeRuleSetLoaderTest" `
            --tests "com.cqcp.apiserver.reviewengine.RuleSetActivationGateTest" `
            --tests "com.cqcp.apiserver.reviewengine.TaskExecutionStateMachineTest" `
            --tests "com.cqcp.apiserver.reviewengine.VersionedRatioScopeV20260729Test"
    }
    $d1CombinedCounts = Read-BackendTestCounts
    if ($d1CombinedCounts.tests -ne 443 `
            -or $d1CombinedCounts.failures -ne 0 `
            -or $d1CombinedCounts.errors -ne 0 `
            -or $d1CombinedCounts.skipped -ne 0) {
        throw "D1 combined gate must be exactly 443/0/0/0."
    }

    Invoke-Logged "d2-seam-test" (Join-Path $repo "apps/api-server") {
        gradle test --no-daemon --rerun-tasks `
            --tests "com.cqcp.apiserver.reviewengine.ModelAssistRuntimeSeamTest"
    }
    $d2SeamCounts = Read-BackendTestCounts
    if ($d2SeamCounts.tests -ne 20 `
            -or $d2SeamCounts.failures -ne 0 `
            -or $d2SeamCounts.errors -ne 0 `
            -or $d2SeamCounts.skipped -ne 0) {
        throw "D2 seam gate must be exactly 20/0/0/0."
    }

    Invoke-Logged "review-assets-node-test" $repo {
        node --test scripts/validate-review-assets.test.mjs
    }
    Invoke-Logged "review-assets-validate" $repo {
        node scripts/validate-review-assets.mjs
    }
    Invoke-Logged "backend-test" (Join-Path $repo "apps/api-server") {
        gradle test --no-daemon --rerun-tasks
    }
    $backendFirstCounts = Read-BackendTestCounts
    Invoke-Logged "backend-test-repeat" (Join-Path $repo "apps/api-server") {
        gradle test --no-daemon --rerun-tasks
    }
    $backendRepeatCounts = Read-BackendTestCounts
    if (($backendFirstCounts | ConvertTo-Json -Compress) `
            -ne ($backendRepeatCounts | ConvertTo-Json -Compress)) {
        throw "Repeated backend test counts differ."
    }
    Invoke-Logged "backend-bootjar" (Join-Path $repo "apps/api-server") {
        gradle bootJar --no-daemon --rerun-tasks
    }
    Invoke-Logged "admin-web-test" (Join-Path $repo "apps/admin-web") {
        npm.cmd run test
    }
    Invoke-Logged "admin-web-lint" (Join-Path $repo "apps/admin-web") {
        npm.cmd run lint
    }
    Invoke-Logged "admin-web-build" (Join-Path $repo "apps/admin-web") {
        npm.cmd run build
    }
    Invoke-Logged "core-node-test" $repo {
        node --test $coreNodeTests
    }
    Invoke-Logged "blind-freeze-verify" $repo {
        node scripts/blind-evaluation/verify-freeze.mjs .
    }
    Invoke-Logged "deepseek-seal-verify" $repo {
        node scripts/blind-evaluation/verify-deepseek-seal.mjs .
    }
    Invoke-Logged "blind-unblind" $repo {
        node scripts/blind-evaluation/unblind.mjs .
    }
    Invoke-Logged "track-b-dispatch" $repo {
        node scripts/blind-evaluation/prepare-track-b-dispatch.mjs . verify
    }
    Invoke-Logged "track-b-opinion-seal" $repo {
        node scripts/blind-evaluation/track-b-opinion-contract.mjs . verify
    }
    Invoke-Logged "track-b-admission-verify" $repo {
        node scripts/blind-evaluation/seal-and-evaluate-track-b-admission.mjs . verify
    }
    Invoke-Logged "openapi-consistency" $repo {
        node scripts/mvp002/verify-openapi.mjs .
    }
    Invoke-Logged "browser-evidence" $repo {
        node scripts/mvp002/verify-browser-evidence.mjs .
    }
    Invoke-Logged "git-diff-check" $repo {
        git diff --check 1035739b751386176e47c6871738a62bff86de02 HEAD
    }
    Invoke-Logged "compose-acceptance" $repo {
        & (Join-Path $repo "scripts/mvp002/run-compose-acceptance.ps1") `
            -RepoRoot $repo `
            -EvidenceRoot $evidenceRoot
    }

    $adminWebTestCount = Read-LoggedTestCount `
        -LogName "admin-web-test.log" `
        -Pattern "Tests\s+(\d+)\s+passed"
    $coreNodeTestCount = Read-LoggedTestCount `
        -LogName "core-node-test.log" `
        -Pattern "(?m)^\D*tests\s+(\d+)\s*$"

    $summary = [ordered]@{
        schemaVersion = "task-mvp-002-core-verification-v1"
        status = "PASS"
        generatedAt = [DateTimeOffset]::UtcNow.ToString("O")
        powerShellVersion = $PSVersionTable.PSVersion.ToString()
        networkModelCalls = 0
        providerA0Included = $false
        postgres = [ordered]@{
            engine = "PostgreSQL"
            isolatedDatabase = $databaseName
            createdForThisRun = $true
            cleanupPolicy = "DROP_WITH_FORCE_IN_FINALLY"
        }
        backend = $backendRepeatCounts
        backendRuns = @(
            [ordered]@{ name = "backend-test"; counts = $backendFirstCounts },
            [ordered]@{ name = "backend-test-repeat"; counts = $backendRepeatCounts }
        )
        formalR7 = $formalR7Evidence.assertions
        d1Combined = $d1CombinedCounts
        d2Seam = $d2SeamCounts
        adminWeb = [ordered]@{ tests = $adminWebTestCount }
        coreNode = [ordered]@{
            files = $coreNodeTests.Count
            tests = $coreNodeTestCount
        }
        evidenceReferences = Read-EvidenceReferences
        runs = $runs
    }
    $summaryPath = Join-Path $evidenceRoot "verification-summary.json"
    $summaryJson = $summary | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText(
        $summaryPath,
        $summaryJson + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false)
    )
    $summaryJson | Out-Host
} finally {
    if ($databaseCreated) {
        & docker exec cqcp-postgres-1 `
            psql -U cqcp -d postgres -v ON_ERROR_STOP=1 `
            -c "DROP DATABASE IF EXISTS $databaseName WITH (FORCE)" | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to clean isolated PostgreSQL verification database."
        }
    }
}
