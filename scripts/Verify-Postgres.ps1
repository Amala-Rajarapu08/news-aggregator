param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin', [string]$MavenPath = 'mvn', [int]$Port = 55432, [switch]$LiveNews, [string]$Test = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$verificationRoot = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $verificationRoot -Force | Out-Null
$cluster = Join-Path $verificationRoot ('postgres-verification-' + [Guid]::NewGuid().ToString('N'))
$passwordFile = Join-Path $verificationRoot ('pg-password-' + [Guid]::NewGuid().ToString('N'))
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$verificationPassword = [Convert]::ToBase64String($bytes)
if (-not (Get-Command $MavenPath -ErrorAction SilentlyContinue)) {
    $cachedMaven = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $cachedMaven) { throw 'Install Maven or supply -MavenPath.' }
    $MavenPath = $cachedMaven.FullName
}
$ownedServerStarted = $false
try {
    [IO.File]::WriteAllText($passwordFile, $verificationPassword)
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U pulse_verify --auth=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 --locale=C *> (Join-Path $verificationRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed. See .runtime/initdb.log.' }
    Remove-Item -LiteralPath $passwordFile
    $controlLog = Join-Path $verificationRoot 'pg-control.log'
    $controlError = Join-Path $verificationRoot 'pg-control-error.log'
    $serverLog = Join-Path $cluster 'server.log'
    $controlArgs = "-D `"$cluster`" -l `"$serverLog`" -o `"-p $Port -h 127.0.0.1`" -w start"
    $controlProcess = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList $controlArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $controlLog -RedirectStandardError $controlError
    $null = $controlProcess.Handle
    $controlProcess.WaitForExit()
    $controlProcess.Refresh()
    if ($controlProcess.ExitCode -ne 0) { throw 'The isolated verification server could not start.' }
    $ownedServerStarted = $true
    $env:PGPASSWORD = $verificationPassword
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U pulse_verify pulseai_verification
    if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated verification database.' }
    $env:VERIFY_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$Port/pulseai_verification"
    $env:VERIFY_DATABASE_USERNAME = 'pulse_verify'
    $env:VERIFY_DATABASE_PASSWORD = $verificationPassword
    $env:VERIFY_DATABASE_DRIVER = 'org.postgresql.Driver'
    $env:VERIFY_DATABASE_DIALECT = 'org.hibernate.dialect.PostgreSQLDialect'
    $env:VERIFY_FLYWAY_TARGET = '8'
    if ($LiveNews) { $env:PULSE_LIVE_NEWS_TEST = 'true' }
    $mavenArgs = @('-B', 'test')
    if ($Test) { $mavenArgs += ('-Dtest=' + $Test) }
    Push-Location (Join-Path $projectRoot 'backend')
    try { $ErrorActionPreference = 'Continue'; & $MavenPath @mavenArgs; $testExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop'; Pop-Location }
    if ($testExit -ne 0) { throw 'PostgreSQL integration tests failed.' }
    $migrationVersion = & (Join-Path $PostgresBin 'psql.exe') -X -qAt -h 127.0.0.1 -p $Port -U pulse_verify -d pulseai_verification -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
    if ($LASTEXITCODE -ne 0 -or $migrationVersion.Trim() -ne '8') { throw "Expected isolated Flyway schema version 8, found '$migrationVersion'." }
    Write-Output "Verified on isolated PostgreSQL at port $Port. Flyway V1-V8 applied and validated; existing database services were untouched. Cluster artifacts: $cluster"
} finally {
    if ($ownedServerStarted -and (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid'))) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($name in 'PGPASSWORD','VERIFY_DATABASE_PASSWORD','VERIFY_DATABASE_URL','VERIFY_DATABASE_USERNAME','VERIFY_DATABASE_DRIVER','VERIFY_DATABASE_DIALECT','VERIFY_FLYWAY_TARGET','PULSE_LIVE_NEWS_TEST') { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
}
