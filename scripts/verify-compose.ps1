#Requires -Version 5.1
<#
.SYNOPSIS
    验证 Docker Compose 服务、三类统计接口、MySQL 数据与 Redis 缓存。
.DESCRIPTION
    只读验收：不执行采集、不修改数据库、不输出凭据。
.PARAMETER StageKeys
    用于三类统计的已采集复合赛段键。
.PARAMETER EnvFile
    Compose 环境文件，默认为项目根目录下的 .env。
#>
param(
    [string]$StageKeys = '237:112,237:113,237:100',
    [int]$MinimumPickCount = 10,
    [int]$MinimumMatchCount = 5,
    [string]$EnvFile = '.env'
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $repoRoot 'deploy/docker-compose.yml'
$envFilePath = if ([System.IO.Path]::IsPathRooted($EnvFile)) {
    [System.IO.Path]::GetFullPath($EnvFile)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $EnvFile))
}

$passCount = 0
$failCount = 0

function Write-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        $script:passCount++
        Write-Host "[PASS] $Name" -ForegroundColor Green
    } else {
        $script:failCount++
        Write-Host "[FAIL] $Name $Detail" -ForegroundColor Red
    }
}

function Test-HttpStatus {
    param([string]$Url, [int]$ExpectedStatus, [string]$Name, [string]$Method = 'GET')
    $actualStatus = 0
    try {
        $response = Invoke-WebRequest -Uri $Url -Method $Method -UseBasicParsing -TimeoutSec 15 -ErrorAction Stop
        $actualStatus = [int]$response.StatusCode
    } catch {
        if ($null -ne $_.Exception.Response) {
            $actualStatus = [int]$_.Exception.Response.StatusCode.value__
        }
    }
    Write-Check $Name ($actualStatus -eq $ExpectedStatus) "(实际: $actualStatus，期望: $ExpectedStatus)"
}

function Test-JsonApi {
    param(
        [string]$Url,
        [string]$Name,
        [switch]$RequireItems,
        [switch]$RequireNonEmptyData
    )
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20 -ErrorAction Stop
        $body = $response.Content | ConvertFrom-Json
        $ok = $response.StatusCode -eq 200 -and $body.success -eq $true -and $null -ne $body.data
        if ($RequireItems) {
            $ok = $ok -and [int64]$body.data.dataVersion -gt 0 `
                -and [int64]$body.data.total -gt 0 `
                -and @($body.data.items).Count -gt 0
        }
        if ($RequireNonEmptyData) {
            $ok = $ok -and @($body.data).Count -gt 0
        }
        Write-Check $Name $ok '(响应必须为 success=true 且包含非空数据)'
    } catch {
        Write-Check $Name $false "($($_.Exception.Message))"
    }
}

Write-Host "`n=== 1. Compose 健康状态 ===" -ForegroundColor Cyan
$composeArgs = @('compose')
if (Test-Path -LiteralPath $envFilePath) {
    $composeArgs += @('--env-file', $envFilePath)
}
$composeArgs += @('-f', $composeFile, 'ps', '--format', 'json')
$psOutput = & docker $composeArgs 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FATAL] docker compose ps 执行失败。" -ForegroundColor Red
    exit 1
}

try {
    $services = @($psOutput | Where-Object { $_.ToString().Trim().StartsWith('{') } | ForEach-Object {
        $_ | ConvertFrom-Json
    })
} catch {
    Write-Host "[FATAL] 无法解析 docker compose ps JSON。" -ForegroundColor Red
    exit 1
}

foreach ($serviceName in @('backend', 'frontend', 'mysql', 'redis')) {
    $service = $services | Where-Object { $_.Service -eq $serviceName } | Select-Object -First 1
    $state = if ($null -ne $service) { [string]$service.State } else { '' }
    $health = if ($null -ne $service) { [string]$service.Health } else { '' }
    $ok = $null -ne $service -and $state.ToLowerInvariant() -eq 'running' `
        -and $health.ToLowerInvariant() -eq 'healthy'
    Write-Check "服务 [$serviceName] running + healthy" $ok "(state=$state, health=$health)"
}

Write-Host "`n=== 2. 公共 HTTP 与 JSON 接口 ===" -ForegroundColor Cyan
Test-HttpStatus 'http://localhost:8081/' 200 '前端首页'
Test-JsonApi 'http://localhost:8081/api/v1/catalog/seasons' '赛事目录' -RequireNonEmptyData

$encodedStageKeys = [uri]::EscapeDataString($StageKeys)
$heroUrl = "http://localhost:8081/api/v1/statistics/champions?stageKeys=${encodedStageKeys}&minimumPickCount=${MinimumPickCount}"
$teamUrl = "http://localhost:8081/api/v1/statistics/teams?stageKeys=${encodedStageKeys}&minimumMatchCount=${MinimumMatchCount}"
$playerUrl = "http://localhost:8081/api/v1/statistics/players?stageKeys=${encodedStageKeys}&minimumMatchCount=${MinimumMatchCount}"
Test-JsonApi $heroUrl '英雄统计第一次查询' -RequireItems
Test-JsonApi $heroUrl '英雄统计第二次查询（缓存候选）' -RequireItems
Test-JsonApi $teamUrl '战队统计' -RequireItems
Test-JsonApi $playerUrl '选手统计' -RequireItems
Test-HttpStatus 'http://localhost:8081/api/internal/catalog/sync' 404 'Nginx 隔离内部接口' 'POST'

Write-Host "`n=== 3. MySQL 当前数据 ===" -ForegroundColor Cyan
$dbOutput = @()
$dbExitCode = 1
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $containerEnvOutput = docker inspect lol-datahub-mysql --format '{{json .Config.Env}}' 2>&1
    if ($LASTEXITCODE -eq 0) {
        $containerEnv = $containerEnvOutput | ConvertFrom-Json
        $dbUser = (($containerEnv | Where-Object { $_ -like 'MYSQL_USER=*' }) -split '=', 2)[1]
        $dbPassword = (($containerEnv | Where-Object { $_ -like 'MYSQL_PASSWORD=*' }) -split '=', 2)[1]
        $dbName = (($containerEnv | Where-Object { $_ -like 'MYSQL_DATABASE=*' }) -split '=', 2)[1]
        $dbQuery = "SELECT 'HERO',COUNT(*) FROM champion_stage_stat_current UNION ALL SELECT 'TEAM',COUNT(*) FROM team_stage_stat_current UNION ALL SELECT 'PLAYER',COUNT(*) FROM player_stage_stat_current"
        $dbOutput = docker exec -e "MYSQL_PWD=$dbPassword" lol-datahub-mysql `
            mysql -u $dbUser -D $dbName -N -e $dbQuery 2>&1
        $dbExitCode = $LASTEXITCODE
    }
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$rowCounts = @{}
if ($dbExitCode -eq 0) {
    foreach ($line in $dbOutput) {
        if ($line -match '^(HERO|TEAM|PLAYER)\s+([0-9]+)$') {
            $rowCounts[$Matches[1]] = [int64]$Matches[2]
        }
    }
}
foreach ($type in @('HERO', 'TEAM', 'PLAYER')) {
    $count = if ($rowCounts.ContainsKey($type)) { $rowCounts[$type] } else { 0 }
    Write-Check "MySQL $type current 非空" ($dbExitCode -eq 0 -and $count -gt 0) "(rows=$count, exit=$dbExitCode)"
}

Write-Host "`n=== 4. Redis 连接与统计缓存 ===" -ForegroundColor Cyan
$redisPassword = ''
$redisInspectOk = $false
$ErrorActionPreference = 'Continue'
try {
    $redisEnvOutput = docker inspect lol-datahub-redis --format '{{json .Config.Env}}' 2>&1
    if ($LASTEXITCODE -eq 0) {
        $redisContainerEnv = $redisEnvOutput | ConvertFrom-Json
        $redisPasswordEntry = $redisContainerEnv | Where-Object { $_ -like 'REDIS_PASSWORD=*' } | Select-Object -First 1
        if ($null -ne $redisPasswordEntry) {
            $redisPassword = ($redisPasswordEntry -split '=', 2)[1]
        }
        $redisInspectOk = $true
    }
    if ($redisPassword.Length -gt 0) {
        $redisPingOutput = @(docker exec -e "REDISCLI_AUTH=$redisPassword" lol-datahub-redis redis-cli ping 2>&1)
    } else {
        $redisPingOutput = @(docker exec lol-datahub-redis redis-cli ping 2>&1)
    }
    $redisPingExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$redisPingLines = @($redisPingOutput | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
$redisPingOk = $redisInspectOk -and $redisPingExitCode -eq 0 `
    -and $redisPingLines.Count -eq 1 -and $redisPingLines[0] -eq 'PONG'
Write-Check 'Redis 认证配置与 PING 一致' $redisPingOk "(exit=$redisPingExitCode)"

$ErrorActionPreference = 'Continue'
try {
    if ($redisPassword.Length -gt 0) {
        $redisScanOutput = @(docker exec -e "REDISCLI_AUTH=$redisPassword" lol-datahub-redis `
            redis-cli --scan --pattern 'loldatahub:stats:*' 2>&1)
    } else {
        $redisScanOutput = @(docker exec lol-datahub-redis `
            redis-cli --scan --pattern 'loldatahub:stats:*' 2>&1)
    }
    $redisScanExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$redisScanLines = @($redisScanOutput | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
$redisHasError = @($redisScanLines | Where-Object { $_ -match '(?i)(AUTH failed|NOAUTH|^ERR )' }).Count -gt 0
$redisKeys = @($redisScanLines | Where-Object { $_ -match '^loldatahub:stats:s\d+:' })
$redisScanOk = $redisScanExitCode -eq 0 -and -not $redisHasError
Write-Check 'Redis 扫描命令无认证或执行错误' $redisScanOk "(exit=$redisScanExitCode)"
foreach ($type in @('champion', 'team', 'player')) {
    $hasTypeKey = @($redisKeys | Where-Object { $_ -match ":${type}:" }).Count -gt 0
    Write-Check "Redis 存在 $type 统计缓存" $hasTypeKey "(总键数=$($redisKeys.Count))"
}

Write-Host "`n========== 汇总 ==========" -ForegroundColor Cyan
Write-Host "通过: $passCount  失败: $failCount"
if ($failCount -gt 0) {
    Write-Host "`n验收未通过，请检查上方 [FAIL] 项。" -ForegroundColor Red
    exit 1
}

Write-Host "`n全部验收通过。" -ForegroundColor Green
exit 0
