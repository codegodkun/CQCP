param(
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required."
}

$repo = [System.IO.Path]::GetFullPath($RepoRoot)
$apiServer = Join-Path $repo "apps/api-server"
$outputRoot = Join-Path $repo "outputs/task-034-mvp-e2e-acceptance-v3"
$verificationRoot = Join-Path $repo "outputs/task-mvp-002/core-audit/verification"
$rawLogPath = Join-Path $verificationRoot "formal-r7.log"
$testXmlPath = Join-Path $apiServer (
    "build/test-results/test/" +
    "TEST-com.cqcp.apiserver.reviewengine.Task034MvpE2EAcceptanceHarnessTest.xml"
)
$formalXmlPath = Join-Path $outputRoot "formal-test-result.xml"
$sealPath = Join-Path $outputRoot "r7-evidence-seal.json"
$expectedBranch = "codex/task-mvp-002"
$expectedCommit = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $expectedCommit -notmatch "^[a-f0-9]{40}$") {
    throw "Formal R7 cannot resolve the current HEAD."
}

foreach ($path in @(
        $apiServer,
        $outputRoot,
        $verificationRoot,
        $rawLogPath,
        $testXmlPath,
        $formalXmlPath,
        $sealPath
    )) {
    $resolved = [System.IO.Path]::GetFullPath($path)
    if (-not (
            $resolved.Equals($repo, [System.StringComparison]::OrdinalIgnoreCase) -or
            $resolved.StartsWith(
                $repo + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)
        )) {
        throw "Formal R7 path escapes repository root: $resolved"
    }
}

if (-not (Test-Path -LiteralPath $apiServer -PathType Container)) {
    throw "API server directory does not exist."
}
$branch = [string](& git -C $repo branch --show-current)
$branch = $branch.Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Formal R7 cannot resolve the current branch."
}
if ([string]::IsNullOrWhiteSpace($branch)) {
    $branchHead = (& git -C $repo rev-parse "refs/heads/$expectedBranch").Trim()
    if ($LASTEXITCODE -ne 0 -or $branchHead -ne $expectedCommit) {
        throw "Detached Formal R7 HEAD is not bound to $expectedBranch."
    }
    $branch = $expectedBranch
}
if ($branch -ne $expectedBranch) {
    throw "Formal R7 must run on branch $expectedBranch; current branch is '$branch'."
}

New-Item -ItemType Directory -Force -Path $outputRoot, $verificationRoot | Out-Null
foreach ($path in @(
        $rawLogPath,
        $testXmlPath,
        $formalXmlPath,
        $sealPath,
        (Join-Path $outputRoot "run-manifest.json"),
        (Join-Path $outputRoot "console-summary.md"),
        (Join-Path $outputRoot "occurrence-comparison.csv"),
        (Join-Path $outputRoot "production-branch-scope-ledger.json"),
        (Join-Path $outputRoot "sample-results/CQCP-MVP-DOCX-001.json"),
        (Join-Path $outputRoot "sample-results/CQCP-MVP-DOCX-002.json"),
        (Join-Path $outputRoot "sample-results/CQCP-MVP-DOCX-003.json")
    )) {
    Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
}

$previousJavaToolOptions = [Environment]::GetEnvironmentVariable(
    "JAVA_TOOL_OPTIONS",
    [EnvironmentVariableTarget]::Process
)
$lines = [System.Collections.Generic.List[string]]::new()
try {
    $env:JAVA_TOOL_OPTIONS = (
        "-Dcqcp.task034.formal=true " +
        "-Dcqcp.task034.formalInput=true " +
        "-Dcqcp.task034.commit=$expectedCommit " +
        "-Dcqcp.task034.branch=$expectedBranch " +
        "-Dcqcp.task034.gradleVersion=8.10.2"
    )
    Push-Location -LiteralPath $apiServer
    try {
        & gradle test --no-daemon --rerun-tasks `
            --tests (
                "com.cqcp.apiserver.reviewengine." +
                "Task034MvpE2eAcceptanceHarnessTest." +
                "formalAcceptanceEntryPointIsPropertyAndInputGated"
            ) 2>&1 | ForEach-Object {
                $line = $_.ToString()
                $lines.Add($line)
                $line | Out-Host
            }
        $gradleExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        [System.IO.File]::WriteAllLines(
            $rawLogPath,
            $lines,
            [System.Text.UTF8Encoding]::new($false)
        )
    }
    if ($gradleExitCode -ne 0) {
        throw "Formal R7 Gradle execution failed with exit code $gradleExitCode."
    }
} finally {
    if ($null -eq $previousJavaToolOptions) {
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
    } else {
        $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    }
}

if (-not (Test-Path -LiteralPath $testXmlPath -PathType Leaf)) {
    throw "Formal R7 JUnit XML was not produced."
}
[xml]$testXml = Get-Content -LiteralPath $testXmlPath
if ([int]$testXml.testsuite.tests -ne 1 `
        -or [int]$testXml.testsuite.skipped -ne 0 `
        -or [int]$testXml.testsuite.failures -ne 0 `
        -or [int]$testXml.testsuite.errors -ne 0) {
    throw "Formal R7 JUnit XML must be exactly 1/0/0/0."
}
Copy-Item -LiteralPath $testXmlPath -Destination $formalXmlPath -Force

Push-Location -LiteralPath $repo
try {
    & node scripts/mvp002/verify-r7-evidence.mjs . create
    if ($LASTEXITCODE -ne 0) {
        throw "Formal R7 evidence sealing failed."
    }
    & node scripts/mvp002/verify-r7-evidence.mjs . verify
    if ($LASTEXITCODE -ne 0) {
        throw "Formal R7 evidence verification failed."
    }
} finally {
    Pop-Location
}
