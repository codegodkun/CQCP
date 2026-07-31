param(
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required."
}

$repo = [System.IO.Path]::GetFullPath($RepoRoot)
$evidence = [System.IO.Path]::GetFullPath($EvidenceRoot)
$allowedEvidenceRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repo "outputs/task-mvp-002")
)
if (-not "$evidence$([System.IO.Path]::DirectorySeparatorChar)".StartsWith(
        "$allowedEvidenceRoot$([System.IO.Path]::DirectorySeparatorChar)",
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
    throw "EvidenceRoot must remain under outputs/task-mvp-002."
}
$runtimeRoot = [System.IO.Path]::GetFullPath("C:\tmp\cqcp-mvp002-runtime")
if (-not $runtimeRoot.StartsWith("C:\tmp\", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Acceptance runtime root must remain under C:\tmp."
}

$compose = @(
    "compose",
    "-p", "cqcp-mvp002-acceptance",
    "-f", (Join-Path $repo "deploy/compose/compose.yml"),
    "-f", (Join-Path $repo "scripts/mvp002/compose.acceptance.override.yml")
)
$env:CQCP_DB_PORT = "54331"
$env:CQCP_API_PORT = "18082"
$env:CQCP_WEB_PORT = "15175"
$env:CQCP_UPLOAD_HOST_PATH = (Join-Path $runtimeRoot "uploads")
$apiRuntimeContext = Join-Path $runtimeRoot "api-image"
$env:CQCP_API_ACCEPTANCE_CONTEXT = $apiRuntimeContext
$env:CQCP_ACCEPTANCE_NGINX_TEMPLATE = (
    Resolve-Path (Join-Path $repo "scripts/mvp002/nginx.acceptance.conf.template")
).Path
$env:CQCP_ADMIN_API_TOKEN = "mvp002-acceptance-admin"
$env:CQCP_ADMIN_READONLY_TOKEN = "mvp002-acceptance-readonly"
Remove-Item Env:\DEEPSEEK_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:\CQCP_DEEPSEEK_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:\CQCP_MODEL_DEEPSEEK_API_KEY -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force -Path $evidence | Out-Null
if (Test-Path -LiteralPath $runtimeRoot) {
    Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $env:CQCP_UPLOAD_HOST_PATH | Out-Null
New-Item -ItemType Directory -Force -Path $apiRuntimeContext | Out-Null
$bootJars = @(
    Get-ChildItem `
        -LiteralPath (Join-Path $repo "apps/api-server/build/libs") `
        -Filter "*.jar" `
        -File |
        Where-Object { $_.Name -notlike "*-plain.jar" }
)
if ($bootJars.Count -ne 1) {
    throw "Compose acceptance requires exactly one host-built bootJar."
}
Copy-Item `
    -LiteralPath (Join-Path $repo "scripts/mvp002/Dockerfile.api-runtime") `
    -Destination (Join-Path $apiRuntimeContext "Dockerfile")
Copy-Item `
    -LiteralPath $bootJars[0].FullName `
    -Destination (Join-Path $apiRuntimeContext "app.jar")

$summaryPath = Join-Path $evidence "compose-acceptance-summary.json"
$downloadPath = Join-Path $runtimeRoot "downloaded.docx"
$composeLogPath = Join-Path $evidence "compose-services.log"
$networkEvidencePath = Join-Path $evidence "compose-network-policy.json"
$baseUrl = "http://localhost:15175"
$started = $false

try {
    & docker @compose down -v --remove-orphans 2>&1
    & docker @compose up -d --build 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Compose build/start failed with exit code $LASTEXITCODE."
    }
    $started = $true

    $networkInspectRaw = & docker network inspect cqcp_mvp002_acceptance_model_isolated
    if ($LASTEXITCODE -ne 0) {
        throw "Acceptance network inspection failed."
    }
    $networkInspect = @($networkInspectRaw | ConvertFrom-Json)
    if ($networkInspect.Count -ne 1 -or -not $networkInspect[0].Internal) {
        throw "Acceptance network must be internal/default-deny."
    }
    $apiContainerId = (& docker @compose ps -q api-server).Trim()
    if ($LASTEXITCODE -ne 0 -or $apiContainerId -notmatch "^[a-f0-9]{64}$") {
        throw "Acceptance API container identity is unavailable."
    }
    $apiInspect = @(& docker inspect $apiContainerId | ConvertFrom-Json)
    if ($LASTEXITCODE -ne 0 -or $apiInspect.Count -ne 1) {
        throw "Acceptance API container inspection failed."
    }
    $apiNetworks = @($apiInspect[0].NetworkSettings.Networks.PSObject.Properties)
    if ($apiNetworks.Count -ne 1 `
            -or $apiNetworks[0].Name -ne $networkInspect[0].Name `
            -or -not [string]::IsNullOrWhiteSpace(
                [string]$apiNetworks[0].Value.Gateway
            )) {
        throw "Acceptance API must have only the internal model-isolated network and no gateway."
    }
    $networkEvidence = [ordered]@{
        schemaVersion = "task-mvp-002-compose-network-policy-v1"
        status = "PASS"
        capturedAt = [DateTimeOffset]::UtcNow.ToString("O")
        networkName = $networkInspect[0].Name
        networkId = $networkInspect[0].Id
        internal = [bool]$networkInspect[0].Internal
        attachable = [bool]$networkInspect[0].Attachable
        ingress = [bool]$networkInspect[0].Ingress
        apiContainerId = $apiContainerId
        apiNetworks = @($apiNetworks | ForEach-Object {
            [ordered]@{
                name = $_.Name
                networkId = $_.Value.NetworkID
                gateway = [string]$_.Value.Gateway
                ipAddress = [string]$_.Value.IPAddress
            }
        })
        modelEndpointEgressAllowed = $false
    }
    $networkEvidence | ConvertTo-Json -Depth 6 |
        Set-Content -LiteralPath $networkEvidencePath -Encoding utf8

    $health = $null
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health"
            if ($health.status -eq "UP") {
                break
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if ($null -eq $health -or $health.status -ne "UP") {
        throw "API did not become healthy."
    }
    $webRoot = Invoke-WebRequest -Uri "$baseUrl/review/tasks"
    if ($webRoot.StatusCode -ne 200) {
        throw "Admin web did not serve the review task route."
    }
    $unauthenticatedApi = Invoke-WebRequest `
        -Uri "$baseUrl/api/admin/model-profiles" `
        -SkipHttpErrorCheck
    $unauthenticatedWeb = Invoke-WebRequest `
        -Uri "$baseUrl/api/admin/model-profiles" `
        -SkipHttpErrorCheck
    $readonlyApi = Invoke-WebRequest `
        -Uri "$baseUrl/api/admin/model-profiles" `
        -Headers @{Authorization = "Bearer mvp002-acceptance-readonly"} `
        -SkipHttpErrorCheck
    $unauthenticatedTaskList = Invoke-WebRequest `
        -Uri "$baseUrl/api/review/tasks" `
        -SkipHttpErrorCheck
    $unauthenticatedPreview = Invoke-WebRequest `
        -Uri "$baseUrl/api/review/tasks/task-unknown/executions/execution-unknown/document-preview" `
        -SkipHttpErrorCheck
    if ($unauthenticatedApi.StatusCode -ne 401 `
            -or $unauthenticatedWeb.StatusCode -ne 401 `
            -or $readonlyApi.StatusCode -ne 403 `
            -or $unauthenticatedTaskList.StatusCode -ne 401 `
            -or $unauthenticatedPreview.StatusCode -ne 401) {
        throw "Admin authentication/authorization gate is inconsistent."
    }
    $adminHeaders = @{Authorization = "Bearer mvp002-acceptance-admin"}
    $readonlyHeaders = @{Authorization = "Bearer mvp002-acceptance-readonly"}

    $fixturePath = Join-Path $repo "packages/test-fixtures/docx/1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx"
    $maliciousFixturePath = Join-Path $runtimeRoot "malicious-preview-body.docx"
    $maliciousText = "<img src=x onerror=alert('cqcp-xss')>恶意合同正文"
    & (Join-Path $repo "scripts/mvp002/create-malicious-preview-docx.ps1") `
        -SourcePath $fixturePath `
        -OutputPath $maliciousFixturePath `
        -MaliciousText $maliciousText | Out-Null
    $expectedPath = Join-Path $repo "packages/test-fixtures/expected/CQCP-MVP-DOCX-001.json"
    $expected = Get-Content -LiteralPath $expectedPath -Raw | ConvertFrom-Json
    $metadata = @{
        contractType = "ENGINEERING"
        structuredFields = $expected.goldenExpected.structuredFields
    } | ConvertTo-Json -Depth 8 -Compress
    $creation = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/review/tasks" `
        -Form @{file = Get-Item -LiteralPath $fixturePath; metadata = $metadata}

    $status = $null
    for ($attempt = 1; $attempt -le 120; $attempt++) {
        $status = Invoke-RestMethod -Uri "$baseUrl/api/review/tasks/$($creation.taskId)/executions/$($creation.executionId)"
        if ($status.terminal) {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($null -eq $status -or -not $status.terminal -or $status.status -ne "SUCCESS") {
        throw "Execution did not reach SUCCESS."
    }

    $result = Invoke-RestMethod -Uri "$baseUrl/api/v1/tasks/$($creation.taskId)/result?executionId=$($creation.executionId)"
    $taskList = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks?page=0&size=20&statusGroup=COMPLETED&q=%E5%A5%94%E8%85%BE" `
        -Headers $readonlyHeaders
    $preview = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks/$($creation.taskId)/executions/$($creation.executionId)/document-preview" `
        -Headers $readonlyHeaders
    $download = Invoke-WebRequest `
        -Uri "$baseUrl/api/review/tasks/$($creation.taskId)/executions/$($creation.executionId)/document" `
        -Headers $readonlyHeaders `
        -OutFile $downloadPath `
        -PassThru

    if ($result.executionId -ne $creation.executionId) {
        throw "Exact result query returned a different execution."
    }
    if ($taskList.items.Count -ne 1 -or $taskList.items[0].executionId -ne $creation.executionId) {
        throw "Task list did not return the expected execution row."
    }
    if ($preview.blocks.Count -lt 1) {
        throw "Document preview is empty."
    }

    $maliciousFields = ConvertFrom-Json `
        ($expected.goldenExpected.structuredFields | ConvertTo-Json -Depth 8) `
        -AsHashtable
    $maliciousFields.contractName = "恶意正文浏览器验收"
    $maliciousMetadata = @{
        contractType = "ENGINEERING"
        structuredFields = $maliciousFields
    } | ConvertTo-Json -Depth 8 -Compress
    $maliciousCreation = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/review/tasks" `
        -Form @{file = Get-Item -LiteralPath $maliciousFixturePath; metadata = $maliciousMetadata}
    $maliciousStatus = $null
    for ($attempt = 1; $attempt -le 120; $attempt++) {
        $maliciousStatus = Invoke-RestMethod `
            -Uri "$baseUrl/api/review/tasks/$($maliciousCreation.taskId)/executions/$($maliciousCreation.executionId)"
        if ($maliciousStatus.terminal) {
            break
        }
        Start-Sleep -Seconds 1
    }
    if ($null -eq $maliciousStatus `
            -or -not $maliciousStatus.terminal `
            -or $maliciousStatus.status -ne "SUCCESS") {
        throw "Malicious-body execution did not reach SUCCESS."
    }
    $maliciousPreview = Invoke-RestMethod `
        -Uri "$baseUrl/api/review/tasks/$($maliciousCreation.taskId)/executions/$($maliciousCreation.executionId)/document-preview" `
        -Headers $readonlyHeaders
    $maliciousBlock = $maliciousPreview.blocks |
        Where-Object { $_.text -eq $maliciousText } |
        Select-Object -First 1
    if ($null -eq $maliciousBlock) {
        throw "Parser-backed preview did not preserve the malicious contract body as text."
    }

    $sourceSha = (Get-FileHash -LiteralPath $fixturePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $downloadSha = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $headerSha = [string]$download.Headers["X-CQCP-Document-SHA256"]
    if ($sourceSha -ne $downloadSha -or $sourceSha -ne $headerSha) {
        throw "Downloaded DOCX hash does not match the upload."
    }

    $profileBody = @{
        profileCode = "DEEPSEEK_EVAL_ACCEPTANCE"
        displayName = "DeepSeek 验收评测"
        providerType = "PUBLIC_OPENAI_COMPATIBLE"
        endpointAlias = "deepseek-official"
        modelName = "deepseek-v4-pro"
        usageScope = "EVALUATION"
        secretRef = "env:CQCP_MODEL_DEEPSEEK_API_KEY"
        timeoutSeconds = 5
        retryCount = 0
    } | ConvertTo-Json -Compress
    $createdProfile = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/admin/model-profiles" `
        -Headers $adminHeaders `
        -ContentType "application/json" `
        -Body $profileBody
    $connectivity = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/api/admin/model-profiles/DEEPSEEK_EVAL_ACCEPTANCE/connectivity-tests" `
        -Headers $adminHeaders `
        -ContentType "application/json" `
        -Body "{}"
    $profiles = Invoke-RestMethod `
        -Uri "$baseUrl/api/admin/model-profiles" `
        -Headers $adminHeaders
    $networkAttemptMetric = Invoke-RestMethod `
        -Uri "$baseUrl/actuator/metrics/cqcp.model.connectivity.network.attempts"
    $networkAttemptCount = @(
        $networkAttemptMetric.measurements |
        Where-Object statistic -eq "COUNT" |
        Select-Object -ExpandProperty value
    )
    if ($networkAttemptCount.Count -ne 1 -or [double]$networkAttemptCount[0] -ne 0) {
        throw "Acceptance observed a model connectivity network attempt."
    }
    $mock = $profiles.items | Where-Object profileCode -eq "MVP_DEMO_MOCK"

    if ($createdProfile.enabled -or $createdProfile.defaultForNewTask) {
        throw "PUBLIC EVALUATION profile was unexpectedly enabled or made default."
    }
    if ($createdProfile.usageScope -ne "EVALUATION" -or $createdProfile.secretConfigured) {
        throw "PUBLIC profile scope or secret readiness is invalid."
    }
    if ($connectivity.status -ne "SECRET_MISSING" -or $connectivity.durationMs -ne 0) {
        throw "Missing-secret connectivity test did not fail before network access."
    }
    if (-not $mock.enabled -or -not $mock.defaultForNewTask -or $mock.usageScope -ne "DEMO") {
        throw "MVP_DEMO_MOCK lifecycle changed."
    }

    $runtimeProvenancePath = Join-Path $evidence "runtime-provenance.json"
    & node `
        (Join-Path $repo "scripts/mvp002/capture-runtime-provenance.mjs") `
        $repo `
        "capture" `
        $evidence
    if ($LASTEXITCODE -ne 0) {
        throw "Runtime provenance capture failed with exit code $LASTEXITCODE."
    }

    $summary = [ordered]@{
        schemaVersion = "task-mvp-002-compose-acceptance-v2"
        status = "PASS"
        runtimeProvenance = [ordered]@{
            path = [System.IO.Path]::GetRelativePath(
                $repo,
                $runtimeProvenancePath
            ).Replace("\", "/")
            sha256 = (Get-FileHash `
                -LiteralPath $runtimeProvenancePath `
                -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        health = $health.status
        webStatusCode = $webRoot.StatusCode
        adminSecurity = [ordered]@{
            directUnauthenticatedStatus = $unauthenticatedApi.StatusCode
            proxiedUnauthenticatedStatus = $unauthenticatedWeb.StatusCode
            authenticatedNonAdminStatus = $readonlyApi.StatusCode
            taskListUnauthenticatedStatus = $unauthenticatedTaskList.StatusCode
            previewUnauthenticatedStatus = $unauthenticatedPreview.StatusCode
            readonlyWorkbenchAccess = $true
        }
        taskId = $creation.taskId
        executionId = $creation.executionId
        executionStatus = $status.status
        resultExecutionId = $result.executionId
        plannedPointCount = $result.summary.plannedPointCount
        passCount = $result.summary.passCount
        taskListRows = $taskList.items.Count
        previewBlockCount = $preview.blocks.Count
        sourceSha256 = $sourceSha
        storedHeaderSha256 = $headerSha
        downloadSha256 = $downloadSha
        maliciousBodyExecution = [ordered]@{
            taskId = $maliciousCreation.taskId
            executionId = $maliciousCreation.executionId
            executionStatus = $maliciousStatus.status
            sourceSha256 = (Get-FileHash -LiteralPath $maliciousFixturePath -Algorithm SHA256).Hash.ToLowerInvariant()
            previewBlockId = $maliciousBlock.blockId
            previewText = $maliciousBlock.text
            renderedAsTextRequired = $true
        }
        publicProfile = [ordered]@{
            profileCode = $createdProfile.profileCode
            usageScope = $createdProfile.usageScope
            enabled = $createdProfile.enabled
            defaultForNewTask = $createdProfile.defaultForNewTask
            secretConfigured = $createdProfile.secretConfigured
            connectivity = $connectivity.status
            connectivityDurationMs = $connectivity.durationMs
        }
        mockProfile = [ordered]@{
            profileCode = $mock.profileCode
            usageScope = $mock.usageScope
            enabled = $mock.enabled
            defaultForNewTask = $mock.defaultForNewTask
            readiness = $mock.readiness
        }
        modelNetworkEvidence = [ordered]@{
            metricName = $networkAttemptMetric.name
            observedAttemptCount = [double]$networkAttemptCount[0]
            networkPolicyPath = [System.IO.Path]::GetRelativePath(
                $repo,
                $networkEvidencePath
            ).Replace("\", "/")
            networkPolicySha256 = (
                Get-FileHash -LiteralPath $networkEvidencePath -Algorithm SHA256
            ).Hash.ToLowerInvariant()
            internalDefaultDeny = [bool]$networkInspect[0].Internal
        }
        externalModelNetworkAttempted = ([double]$networkAttemptCount[0] -ne 0)
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    $summary | ConvertTo-Json -Depth 8

    & docker @compose logs --no-color 2>&1 | Set-Content -LiteralPath $composeLogPath -Encoding utf8
} finally {
    if ($started -and -not $KeepRunning) {
        & docker @compose down -v --remove-orphans 2>&1
    }
    if (-not $KeepRunning -and (Test-Path -LiteralPath $runtimeRoot)) {
        Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
    }
    if ($KeepRunning -and $started) {
        Write-Output "Compose acceptance stack kept running for browser evidence capture."
    }
}
