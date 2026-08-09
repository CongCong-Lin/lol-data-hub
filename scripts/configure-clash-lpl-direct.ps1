[CmdletBinding()]
param(
    [string]$ConfigRoot = "$env:APPDATA\io.github.clash-verge-rev.clash-verge-rev"
)

$ErrorActionPreference = 'Stop'

$profileScriptPath = Join-Path $ConfigRoot 'profiles\Script.js'
$runtimeConfigPath = Join-Path $ConfigRoot 'clash-verge.yaml'

if (-not (Test-Path -LiteralPath $profileScriptPath)) {
    throw "未找到 Clash Verge 全局扩展脚本：$profileScriptPath"
}
if (-not (Test-Path -LiteralPath $runtimeConfigPath)) {
    throw "未找到 Clash Verge 运行时配置：$runtimeConfigPath"
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$scriptBackupPath = "$profileScriptPath.bak-$timestamp"
$runtimeBackupPath = "$runtimeConfigPath.bak-$timestamp"
Copy-Item -LiteralPath $profileScriptPath -Destination $scriptBackupPath
Copy-Item -LiteralPath $runtimeConfigPath -Destination $runtimeBackupPath

$directRules = @(
    'DOMAIN-SUFFIX,lpl.qq.com,DIRECT',
    'DOMAIN,lol.qq.com,DIRECT',
    'DOMAIN-SUFFIX,tjstats.com,DIRECT',
    'DOMAIN,game.gtimg.cn,DIRECT'
)

$profileScript = @'
// Clash Verge Rev 全局扩展脚本：LPL 数据源使用中国大陆网络直连。
// 除下列域名外，其余流量继续遵循订阅原有规则（包括 Codex/OpenAI）。
const LPL_DIRECT_RULES = [
  "DOMAIN-SUFFIX,lpl.qq.com,DIRECT",
  "DOMAIN,lol.qq.com,DIRECT",
  "DOMAIN-SUFFIX,tjstats.com,DIRECT",
  "DOMAIN,game.gtimg.cn,DIRECT",
];

function main(config, profileName) {
  const currentRules = Array.isArray(config.rules) ? config.rules : [];
  const retainedRules = currentRules.filter((rule) => !LPL_DIRECT_RULES.includes(rule));
  config.rules = [...LPL_DIRECT_RULES, ...retainedRules];
  return config;
}
'@

$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($profileScriptPath, $profileScript, $utf8WithoutBom)

$runtimeLines = [System.Collections.Generic.List[string]]::new()
[System.IO.File]::ReadAllLines($runtimeConfigPath) | ForEach-Object {
    if ($directRules -notcontains $_.TrimStart().TrimStart('-').Trim()) {
        $runtimeLines.Add($_)
    }
}

$rulesIndex = $runtimeLines.IndexOf('rules:')
if ($rulesIndex -lt 0) {
    throw '当前 Clash Verge 运行时配置缺少 rules 段，已停止修改。'
}

for ($index = $directRules.Count - 1; $index -ge 0; $index--) {
    $runtimeLines.Insert($rulesIndex + 1, "- $($directRules[$index])")
}
[System.IO.File]::WriteAllLines($runtimeConfigPath, $runtimeLines, $utf8WithoutBom)

$baseConfigPath = Join-Path $ConfigRoot 'config.yaml'
$baseConfig = Get-Content -LiteralPath $baseConfigPath -Encoding UTF8
$secretLine = $baseConfig | Where-Object { $_ -match '^secret:' } | Select-Object -First 1
$secret = if ($null -eq $secretLine) {
    ''
} else {
    ($secretLine -replace '^secret:\s*', '').Trim().Trim('"', "'")
}

function Invoke-MihomoPipeRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Target,
        [string]$Body = ''
    )

    $pipe = New-Object System.IO.Pipes.NamedPipeClientStream(
        '.',
        'verge-mihomo',
        [System.IO.Pipes.PipeDirection]::InOut
    )
    try {
        $pipe.Connect(5000)
        $encoding = New-Object System.Text.UTF8Encoding($false)
        $bodyLength = $encoding.GetByteCount($Body)
        $authorization = if ([string]::IsNullOrWhiteSpace($secret)) {
            ''
        } else {
            "Authorization: Bearer $secret`r`n"
        }
        $request = "$Method $Target HTTP/1.1`r`n" +
            "Host: localhost`r`n" +
            $authorization +
            "Content-Type: application/json`r`n" +
            "Content-Length: $bodyLength`r`n" +
            "Connection: close`r`n`r`n" +
            $Body

        $writer = New-Object System.IO.StreamWriter($pipe, $encoding, 1024, $true)
        $writer.Write($request)
        $writer.Flush()
        $reader = New-Object System.IO.StreamReader($pipe, $encoding, $false, 1024, $true)
        return $reader.ReadToEnd()
    } finally {
        $pipe.Dispose()
    }
}

$reloadBody = @{ path = $runtimeConfigPath } | ConvertTo-Json -Compress
$reloadResponse = Invoke-MihomoPipeRequest -Method 'PUT' -Target '/configs?force=true' -Body $reloadBody
if ($reloadResponse -notmatch '^HTTP/1\.1 20[04]') {
    $statusLine = ($reloadResponse -split "`r`n")[0]
    throw "Mihomo 热重载失败：$statusLine"
}

$rulesResponse = Invoke-MihomoPipeRequest -Method 'GET' -Target '/rules'
if ($rulesResponse -notmatch '^HTTP/1\.1 200') {
    $statusLine = ($rulesResponse -split "`r`n")[0]
    throw "无法读取 Mihomo 生效规则：$statusLine"
}
foreach ($rule in $directRules) {
    $payload = $rule.Split(',')[1]
    $escapedPayload = [regex]::Escape($payload)
    if ($rulesResponse -notmatch "(?s)$escapedPayload.{0,160}DIRECT") {
        throw "热重载后未发现预期规则：$rule"
    }
}

$pageStatus = (Invoke-WebRequest -Uri 'https://lpl.qq.com/web202301/data-index.shtml' -UseBasicParsing -TimeoutSec 20).StatusCode

Write-Output "配置完成：$($directRules.Count) 条赛事域名规则已置于规则列表顶部。"
Write-Output "LPL 页面访问状态：HTTP $pageStatus"
Write-Output "全局脚本备份：$scriptBackupPath"
Write-Output "运行时配置备份：$runtimeBackupPath"
