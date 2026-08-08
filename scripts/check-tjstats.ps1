#Requires -Version 5.1
<#
.SYNOPSIS
    只读检查 tjstats 数据源和 LPL 官网可达性。
.DESCRIPTION
    对 LPL 官网页面和 tjstats 赛季端点进行 HTTP HEAD/GET 探测，仅输出状态码、
    耗时和环境变量就绪情况。不输出任何变量值、响应体或请求头。
    任何检查失败以非 0 退出，但脚本本身不修改数据库、容器或文件。
.PARAMETER BaseUrl
    tjstats API 基础地址，默认 https://open.tjstats.com/match-auth-app/open/v1。
.PARAMETER TimeoutSec
    HTTP 请求超时秒数，默认 15。
.EXAMPLE
    .\scripts\check-tjstats.ps1
    .\scripts\check-tjstats.ps1 -BaseUrl "https://custom.example.com/v1" -TimeoutSec 30
#>
param(
    [string]$BaseUrl = '',
    [int]$TimeoutSec = 15
)

$ErrorActionPreference = 'Continue'
$hasFailure = $false

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = $env:TJSTATS_BASE_URL
    if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
        $BaseUrl = 'https://open.tjstats.com/match-auth-app/open/v1'
    }
}

# ---------- 辅助函数 ----------

function Invoke-HttpCheck {
    <#
    .SYNOPSIS
        对指定 URL 发起只读 HTTP 请求，返回 [pscustomobject] 结果。
    #>
    param(
        [string]$Label,
        [string]$Url,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{}
    )

    $result = [pscustomobject]@{
        Label      = $Label
        Url        = $Url
        Status     = $null
        ElapsedMs  = $null
        ErrorType  = $null
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $reqParams = @{
            Uri             = $Url
            Method          = $Method
            TimeoutSec      = $TimeoutSec
            UseBasicParsing = $true
            Headers         = $Headers
            ErrorAction     = 'Stop'
        }
        $response = Invoke-WebRequest @reqParams
        $sw.Stop()
        $result.Status    = [int]$response.StatusCode
        $result.ElapsedMs = $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        $result.ElapsedMs = $sw.ElapsedMilliseconds
        $ex = $_.Exception

        # 分类错误类型
        if ($ex.Message -match 'timed out|timeout' -or
            $ex.GetType().Name -match 'TaskCanceled') {
            $result.ErrorType = 'TIMEOUT'
        } elseif ($ex.Message -match 'DNS|name resolution|resolve') {
            $result.ErrorType = 'DNS_ERROR'
        } elseif ($ex.Message -match 'connection|connect|refused') {
            $result.ErrorType = 'CONNECTION_ERROR'
        } elseif ($ex.Message -match 'SSL|TLS|certificate') {
            $result.ErrorType = 'SSL_ERROR'
        } elseif ($ex.Response) {
            $result.Status    = [int]$ex.Response.StatusCode
            $result.ErrorType = "HTTP_$($result.Status)"
        } else {
            $result.ErrorType = 'UNKNOWN'
        }
    }

    return $result
}

function Write-CheckResult {
    param([pscustomobject]$Result)

    if ($Result.ErrorType) {
        $statusText = $Result.ErrorType
    } else {
        $statusText = "$($Result.Status)"
    }

    Write-Host ("  {0,-30} 状态: {1,-16} 耗时: {2}ms" -f $Result.Label, $statusText, $Result.ElapsedMs)

    if ($Result.ErrorType -and $Result.ErrorType -ne "HTTP_$($Result.Status)") {
        return $true  # 表示有连接级错误
    }
    return $false
}

# ---------- 输出环境变量就绪状态 ----------

Write-Host ""
Write-Host "=== TJStats 连通性检查 ===" -ForegroundColor Cyan
Write-Host ""

$tjAuthSet = -not [string]::IsNullOrWhiteSpace($env:TJSTATS_AUTHORIZATION)
$tokenSet = -not [string]::IsNullOrWhiteSpace($env:INTERNAL_API_TOKEN)
$tjAuthStatus  = if ($tjAuthSet) { 'SET' } else { 'NOT_SET' }
$tokenStatus   = if ($tokenSet)  { 'SET' } else { 'NOT_SET' }

Write-Host ("  {0,-30} 状态: {1}" -f 'TJSTATS_AUTHORIZATION', $tjAuthStatus)
Write-Host ("  {0,-30} 状态: {1}" -f 'INTERNAL_API_TOKEN', $tokenStatus)
Write-Host ""

# ---------- 第一组：LPL 官网只读检查 ----------

Write-Host "--- LPL 官网可达性 ---" -ForegroundColor Yellow

$lplChecks = @(
    @{ Label = 'LPL 数据首页';   Url = 'https://lpl.qq.com/web202301/data-index.shtml' },
    @{ Label = 'LPL 统计脚本';   Url = 'https://lpl.qq.com/web202301/js/rank.js' }
)

foreach ($check in $lplChecks) {
    $r = Invoke-HttpCheck -Label $check.Label -Url $check.Url
    $connError = Write-CheckResult -Result $r
    if ($r.ErrorType) { $hasFailure = $true }
}

Write-Host ""

# ---------- 第二组：tjstats API 只读检查 ----------

Write-Host "--- TJStats API 可达性 ---" -ForegroundColor Yellow

$seasonEndpoint = "$($BaseUrl.TrimEnd('/'))/schedule/season"
$tjChecks = @(
    @{ Label = 'tjstats 赛季接口'; Url = $seasonEndpoint }
)

foreach ($check in $tjChecks) {
    $r = Invoke-HttpCheck -Label $check.Label -Url $check.Url
    $connError = Write-CheckResult -Result $r
    if ($r.ErrorType -and $r.Status -notin @(401, 403)) { $hasFailure = $true }

    # 若有连接级错误，给出 VPN/网络建议
    if ($connError) {
        Write-Host ""
        Write-Host "  [建议] tjstats 连接失败，请尝试以下操作：" -ForegroundColor Yellow
        Write-Host "    - 关闭 VPN / 代理 / 梯子" -ForegroundColor Yellow
        Write-Host "    - 使用中国大陆网络环境" -ForegroundColor Yellow
        Write-Host "    - 检查 TJSTATS_BASE_URL 是否指向可用的境内转发服务" -ForegroundColor Yellow
    }
}

# ---------- 第三组：带 Authorization 的赛季端点检查 ----------

if ($tjAuthSet) {
    Write-Host ""
    Write-Host "--- TJStats 赛季接口（带 Authorization） ---" -ForegroundColor Yellow

    $authHeaders = @{ 'Authorization' = $env:TJSTATS_AUTHORIZATION }
    $r = Invoke-HttpCheck -Label 'tjstats 赛季接口 (Auth)' -Url $seasonEndpoint -Headers $authHeaders

    if ($r.ErrorType) {
        $statusText = $r.ErrorType
    } else {
        $statusText = "$($r.Status)"
    }
    Write-Host ("  {0,-30} 状态: {1,-16} 耗时: {2}ms" -f 'tjstats 赛季接口 (Auth)', $statusText, $r.ElapsedMs)

    if ($r.ErrorType -or $r.Status -ne 200) { $hasFailure = $true }
}

# ---------- 汇总 ----------

Write-Host ""
if ($hasFailure) {
    Write-Host "=== 检查完成：存在失败项 ===" -ForegroundColor Red
    exit 1
} else {
    Write-Host "=== 检查完成：全部通过 ===" -ForegroundColor Green
    exit 0
}
