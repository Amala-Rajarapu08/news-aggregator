param(
    [string]$MavenPath = "mvn",
    [switch]$ReadOnlyAudit,
    [switch]$Offline,
    [string]$MavenRepository = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
            $configName = $matches[1]; $configValue = $matches[2].Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($configName, $configValue, 'Process')
        }
    }
}
if ($ReadOnlyAudit) {
    foreach ($setting in @{
        NEWS_INGESTION_ENABLED = 'false'
        NEWS_INGESTION_INITIAL_DELAY_MS = '2147483647'
        NEWS_INGESTION_INTERVAL_MS = '2147483647'
        NEWS_CLEANUP_INTERVAL_MS = '2147483647'
        NEWS_IMAGE_BACKFILL_ENABLED = 'false'
        AI_EMBEDDING_BACKFILL_INITIAL_DELAY_MS = '2147483647'
        AI_EMBEDDING_BACKFILL_INTERVAL_MS = '2147483647'
    }.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($setting.Key, $setting.Value, 'Process')
    }
}
foreach ($name in 'DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD','JWT_SECRET') {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $value -or $value.StartsWith('REPLACE_')) { throw "Set $name in the ignored project .env file before starting." }
}

# Avoid launching a second Spring Boot instance when the configured API is already up.
$backendPort = 8080
$listenerPids = @(foreach ($connection in (& netstat.exe -ano -p tcp)) {
    if ($connection -match "^\s*TCP\s+\S+:$backendPort\s+\S+\s+LISTENING\s+(\d+)\s*$") {
        [int]$matches[1]
    }
})
$listenerPids = @($listenerPids | Sort-Object -Unique)
if ($listenerPids.Count -gt 0) {
    $listenerProcesses = @(foreach ($listenerPid in $listenerPids) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $listenerPid" -ErrorAction SilentlyContinue
        if (-not $process) {
            [pscustomobject]@{ PID = $listenerPid; Name = 'unknown'; Owner = 'unknown'; IsPulseAi = $false }
            continue
        }

        $owner = 'unknown'
        try {
            $ownerInfo = Invoke-CimMethod -InputObject $process -MethodName GetOwner -ErrorAction Stop
            if ($ownerInfo.ReturnValue -eq 0) { $owner = "$($ownerInfo.Domain)\$($ownerInfo.User)" }
        } catch { }

        [pscustomobject]@{
            PID = $listenerPid
            Name = $process.Name
            Owner = $owner
            IsPulseAi = ($process.CommandLine -match 'com\.pulseai\.PulseAiApplication|(?:^|[\\/])pulseai-backend[^\\/\s]*\.jar(?:\s|$)')
        }
    })

    foreach ($listenerProcess in $listenerProcesses) {
        Write-Host "Port $backendPort is listened to by PID $($listenerProcess.PID) ($($listenerProcess.Name), owner $($listenerProcess.Owner))."
    }

    if ($listenerProcesses.Count -eq 1) {
        try {
            $healthResponse = Invoke-WebRequest -Uri "http://localhost:$backendPort/api/health" -UseBasicParsing -TimeoutSec 5
            $healthBody = $healthResponse.Content | ConvertFrom-Json
            $publisherResponse = Invoke-WebRequest -Uri "http://localhost:$backendPort/api/v1/sources/available?category=business" -UseBasicParsing -TimeoutSec 5
            if ($healthBody.application -eq 'pulseai-backend' -and
                [int]$healthResponse.StatusCode -ge 200 -and [int]$healthResponse.StatusCode -lt 300 -and
                [int]$publisherResponse.StatusCode -ge 200 -and [int]$publisherResponse.StatusCode -lt 300) {
                Write-Host "PulseAI health and publisher contracts are healthy on port $backendPort (publisher route HTTP $([int]$publisherResponse.StatusCode)); reusing the existing backend."
                return
            }
        } catch {
            $contractStatus = if ($_.Exception.Response) {
                [int]$_.Exception.Response.StatusCode
            } else {
                $_.Exception.GetType().Name
            }
        }

        if (-not $contractStatus) { $contractStatus = 'unavailable' }
        $listenerDescription = if ($listenerProcesses[0].IsPulseAi) { 'a verified PulseAI process' } else { 'an unverified process' }
        throw "Port $backendPort is occupied by $listenerDescription, but the PulseAI health and publisher contracts could not be confirmed (GET /api/v1/sources/available?category=business: $contractStatus). Inspect the listener and restart it only if it is PulseAI; this script will not terminate a process automatically."
    }

    throw "Port $backendPort is already occupied. Refusing to launch another backend or stop an unrelated process."
}

if (-not (Get-Command $MavenPath -ErrorAction SilentlyContinue)) {
    $cachedMaven = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $cachedMaven) { throw 'Install Maven 3.9+ or supply -MavenPath with its mvn.cmd path.' }
    $MavenPath = $cachedMaven.FullName
}
Push-Location (Join-Path $projectRoot 'backend')
$mavenArguments = @()
if ($Offline) { $mavenArguments += '-o' }
if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
    $mavenArguments += "-Dmaven.repo.local=$MavenRepository"
}
$mavenArguments += 'spring-boot:run'
try { & $MavenPath @mavenArguments; if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See the error above.' } }
finally { Pop-Location }
