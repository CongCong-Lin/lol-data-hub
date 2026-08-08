#Requires -Version 5.1
<#
.SYNOPSIS
    验证 docker-compose.yml 已启动服务的健康状态和关键接口可用性。
.DESCRIPTION
    只读检查，不删除容器、不执行 down、不修改数据库。
.PARAMETER StageKeys
    英雄统计 API 的 stageKeys 参数，默认 '237:112,237:113,237:100'。
.PARAMETER MinimumPickCount
    英雄统计 API 的 minimumPickCount 参数，默认 10。
#>
param(
    [string]$StageKeys = '237:112,237:113,237:100',
    [int]$MinimumPickCount = 10
)

$ErrorActionPreference = 'Stop'
$composeFile = 'deploy/docker-compose.yml'

# ---------- 工具函数 ----------
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

# ---------- 1. docker compose ps 检查 ----------
Write-Host "`n=== 1. 服务状态检查 ===" -ForegroundColor Cyan

try {
    $psOutput = docker compose -f $composeFile ps --format json 2>&1
    $services = $psOutput | ForEach-Object { $_ | ConvertFrom-Json }
} catch {
    Write-Host "[FATAL] 无法执行 docker compose ps，请确认已启动服务。" -ForegroundColor Red
    exit 1
}

$requiredServices = @('backend', 'frontend', 'mysql', 'redis')
foreach ($svc in $requiredServices) {
    $found = $services | Where-Object { $_.Service -eq $svc }
    if ($found) {
        # 状态中包含 "running" 或 "healthy" 视为可用
        $state = if ($null -ne $found.State) { $found.State } elseif ($null -ne $found.Status) { $found.Status } else { '' }
        $state = $state.ToLower()
        $isUp = $state -match 'running|healthy|up'
        Write-Check "服务 [$svc] 存在且状态可用" $isUp "(状态: $state)"
    } else {
        Write-Check "服务 [$svc] 存在且状态可用" $false "(未找到)"
    }
}

# ---------- 2. HTTP 接口检查 ----------
Write-Host "`n=== 2. HTTP 接口检查 ===" -ForegroundColor Cyan

function Test-HttpStatus {
    param([string]$Url, [int]$ExpectedStatus, [string]$Name, [hashtable]$Headers = @{})
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 15 -Headers $Headers -ErrorAction SilentlyContinue
        $ok = $response.StatusCode -eq $ExpectedStatus
        Write-Check $Name $ok "(实际状态码: $($response.StatusCode), 期望: $ExpectedStatus)"
    } catch {
        $actualStatus = $_.Exception.Response.StatusCode.value__
        $ok = $actualStatus -eq $ExpectedStatus
        Write-Check $Name $ok "(实际状态码: $actualStatus, 期望: $ExpectedStatus)"
    }
}

# 2a. 前端首页
Test-HttpStatus 'http://localhost:8081' 200 '前端首页 http://localhost:8081'

# 2b. 赛季目录 API（经 Nginx 代理）
Test-HttpStatus 'http://localhost:8081/api/v1/catalog/seasons' 200 '赛季目录 /api/v1/catalog/seasons'

# ---------- 3. 英雄统计 API 两次请求 ----------
Write-Host "`n=== 3. 英雄统计 API（两次请求） ===" -ForegroundColor Cyan

$heroUrl = 'http://localhost:8081/api/v1/statistics/champions?stageKeys=' + $StageKeys + '&minimumPickCount=' + $MinimumPickCount
for ($i = 1; $i -le 2; $i++) {
    Test-HttpStatus $heroUrl 200 "英雄统计 API 第 ${i} 次请求"
}

# ---------- 4. Nginx 隔离检查：内部接口应返回 404 ----------
Write-Host "`n=== 4. Nginx 内部接口隔离检查 ===" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8081/api/internal/catalog/sync' `
        -Method POST `
        -Headers @{ 'X-Internal-Token' = 'verify-compose-dummy-token' } `
        -UseBasicParsing -TimeoutSec 15 -ErrorAction SilentlyContinue
    $actualStatus = $response.StatusCode
} catch {
    $actualStatus = $_.Exception.Response.StatusCode.value__
}

Write-Check 'Nginx 内部接口 /api/internal/catalog/sync 返回 404' ($actualStatus -eq 404) "(实际状态码: $actualStatus, 期望: 404)"

# ---------- 5. Redis 键检查 ----------
Write-Host "`n=== 5. Redis 数据键检查 ===" -ForegroundColor Cyan

try {
    $redisKeys = docker exec lol-datahub-redis redis-cli --scan --pattern 'loldatahub:stats:s3:*' 2>&1
    $keyList = @($redisKeys | Where-Object { $_ -and $_ -notmatch '^Warning:' })
    $hasKeys = $keyList.Count -gt 0
    Write-Check "Redis 中存在 loldatahub:stats:s3:* 键" $hasKeys "(找到 $($keyList.Count) 个键)"
} catch {
    Write-Check "Redis 中存在 loldatahub:stats:s3:* 键" $false "(无法连接 Redis: $_)"
}

# ---------- 汇总 ----------
Write-Host "`n========== 汇总 ==========" -ForegroundColor Cyan
Write-Host "通过: $passCount  失败: $failCount"

if ($failCount -gt 0) {
    Write-Host "`n验收未通过，请检查上方 [FAIL] 项。" -ForegroundColor Red
    exit 1
} else {
    Write-Host "`n全部验收通过。" -ForegroundColor Green
    exit 0
}
