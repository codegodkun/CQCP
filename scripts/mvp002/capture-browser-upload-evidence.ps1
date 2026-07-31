param(
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot,
    [Parameter(Mandatory = $true)]
    [string]$TaskId,
    [Parameter(Mandatory = $true)]
    [string]$ExecutionId,
    [string]$EvidenceRelativePath = "outputs/task-mvp-002/browser-evidence-current",
    [string]$BrowserObservationRelativePath =
        "outputs/task-mvp-002/browser-evidence-current/browser-raw-observation.json"
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required."
}
if ([string]::IsNullOrWhiteSpace($env:CQCP_ADMIN_READONLY_TOKEN)) {
    throw "CQCP_ADMIN_READONLY_TOKEN is required."
}
if ($TaskId -notmatch "^TASK_[a-f0-9]{32}$") {
    throw "Unexpected taskId."
}
if ($ExecutionId -notmatch "^EXEC_[a-f0-9]{32}$") {
    throw "Unexpected executionId."
}

$repo = [System.IO.Path]::GetFullPath($RepoRoot)
$evidenceRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repo $EvidenceRelativePath)
)
$allowedEvidenceRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repo "outputs/task-mvp-002")
)
if (-not "$evidenceRoot$([System.IO.Path]::DirectorySeparatorChar)".StartsWith(
        "$allowedEvidenceRoot$([System.IO.Path]::DirectorySeparatorChar)",
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
    throw "EvidenceRelativePath must remain under outputs/task-mvp-002."
}
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
$evidencePath = Join-Path $evidenceRoot "browser-upload-result.json"
$sourcePath = Join-Path $repo "packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx"
$downloadPath = Join-Path ([System.IO.Path]::GetTempPath()) "cqcp-mvp002-browser-upload-download.docx"
$baseUrl = "http://localhost:18082"
$headers = @{ Authorization = "Bearer $env:CQCP_ADMIN_READONLY_TOKEN" }
$observationPath = [System.IO.Path]::GetFullPath(
    (Join-Path $repo $BrowserObservationRelativePath)
)
if (-not "$observationPath".StartsWith(
        "$allowedEvidenceRoot$([System.IO.Path]::DirectorySeparatorChar)",
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
    throw "BrowserObservationRelativePath must remain under outputs/task-mvp-002."
}
if (-not (Test-Path -LiteralPath $observationPath -PathType Leaf)) {
    throw "Raw browser observation is required."
}

try {
    $observation = Get-Content -LiteralPath $observationPath -Raw |
        ConvertFrom-Json
    if ($observation.schemaVersion -ne "task-mvp-002-browser-raw-observation-v1" `
            -or $observation.status -ne "PASS" `
            -or $observation.upload.fileChooserEvent.source -ne "PLAYWRIGHT_PAGE_EVENT" `
            -or $observation.upload.fileChooserEvent.accepted -ne $true `
            -or $observation.upload.taskId -ne $TaskId `
            -or $observation.upload.executionId -ne $ExecutionId) {
        throw "Raw browser observation does not prove the requested file chooser upload."
    }
    $status = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks/$TaskId/executions/$ExecutionId"
    $result = Invoke-RestMethod `
        -Uri "$baseUrl/api/v1/tasks/$TaskId/result?executionId=$ExecutionId"
    $taskList = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks?page=0&size=20&q=$TaskId" `
        -Headers $headers
    $preview = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks/$TaskId/executions/$ExecutionId/document-preview" `
        -Headers $headers
    $download = Invoke-WebRequest `
        -Uri "$baseUrl/api/review/tasks/$TaskId/executions/$ExecutionId/document" `
        -Headers $headers `
        -OutFile $downloadPath `
        -PassThru

    if (-not $status.terminal -or $status.status -ne "SUCCESS") {
        throw "Browser-uploaded execution is not SUCCESS."
    }
    if ($result.taskId -ne $TaskId -or $result.executionId -ne $ExecutionId) {
        throw "Exact result identity mismatch."
    }
    if ($taskList.items.Count -ne 1 `
            -or $taskList.items[0].taskId -ne $TaskId `
            -or $taskList.items[0].executionId -ne $ExecutionId) {
        throw "Task list identity mismatch."
    }
    if ($preview.taskId -ne $TaskId `
            -or $preview.executionId -ne $ExecutionId `
            -or $preview.blocks.Count -lt 1) {
        throw "Preview identity or content mismatch."
    }

    $sourceFile = Get-Item -LiteralPath $sourcePath
    $sourceSha = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $downloadFile = Get-Item -LiteralPath $downloadPath
    $downloadSha = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $storedHeaderSha = [string]$download.Headers["X-CQCP-Document-SHA256"]
    if ($sourceSha -ne $downloadSha -or $sourceSha -ne $storedHeaderSha) {
        throw "Browser-uploaded document hash mismatch."
    }
    if ($sourceFile.Length -ne $downloadFile.Length) {
        throw "Browser-uploaded document size mismatch."
    }

    $evidence = [ordered]@{
        schemaVersion = "task-mvp-002-browser-upload-v1"
        status = "PASS"
        capturedAt = [DateTimeOffset]::UtcNow.ToString("O")
        uploadChannel = "BROWSER_FILE_CHOOSER_OBSERVED"
        rawBrowserObservation = [ordered]@{
            path = [System.IO.Path]::GetRelativePath(
                $repo,
                $observationPath
            ).Replace("\", "/")
            sha256 = (
                Get-FileHash -LiteralPath $observationPath -Algorithm SHA256
            ).Hash.ToLowerInvariant()
        }
        sourceFixture = "packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx"
        sourceSize = $sourceFile.Length
        sourceSha256 = $sourceSha
        taskId = $TaskId
        executionId = $ExecutionId
        executionStatus = $status.status
        resultExecutionId = $result.executionId
        taskListExecutionId = $taskList.items[0].executionId
        plannedPointCount = $result.summary.plannedPointCount
        passCount = $result.summary.passCount
        previewBlockCount = $preview.blocks.Count
        storedHeaderSha256 = $storedHeaderSha
        downloadSize = $downloadFile.Length
        downloadSha256 = $downloadSha
        browserResultPath = "/review/results/$TaskId`?executionId=$ExecutionId"
        authorizationPersisted = $false
    }
    $evidence | ConvertTo-Json -Depth 6 |
        Set-Content -LiteralPath $evidencePath -Encoding utf8
    $evidence | ConvertTo-Json -Depth 6 | Out-Host
} finally {
    if (Test-Path -LiteralPath $downloadPath) {
        Remove-Item -LiteralPath $downloadPath -Force
    }
}
