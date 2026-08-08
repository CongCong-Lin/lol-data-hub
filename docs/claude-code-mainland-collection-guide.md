# 关闭梯子后的真实数据采集操作手册

适用场景：本机已关闭 VPN、梯子和系统代理，且需要由 Claude Code 在
`D:\code\project\LoLDataHub` 完成 TJStats 真实数据采集与验证。

## 安全边界

1. 不要把 `TJSTATS_AUTHORIZATION`、`INTERNAL_API_TOKEN` 的值输出到终端、日志、回复、源码、README 或 Git 提交中。
2. 仅在当前 PowerShell 进程和被 Git 忽略的项目根目录 `.env` 中保存它们。
3. 不修改项目架构、不添加定时任务、不提交 Git。
4. 不进行浏览器视觉操作；所有验证都使用 PowerShell、HTTP 请求和 Docker 命令完成。
5. 任一步骤的网络或鉴权检查失败时，停止后续采集，不要写入伪造数据，也不要删除现有数据。

## Claude Code 可直接执行的任务说明

将下面整段任务交给 Claude Code：

```text
在 D:\code\project\LoLDataHub 执行“关闭梯子后的真实官网数据采集”。

安全要求：
- 不要输出、记录、提交或向我索取 TJSTATS_AUTHORIZATION 的实际值。
- 不要输出、记录、提交 INTERNAL_API_TOKEN 的实际值。
- 不要修改生产代码、不添加定时任务、不执行 git commit。
- 不要进行浏览器视觉操作。
- 如果连通性、凭据提取、鉴权、容器健康或任一采集步骤失败，立即停止后续采集；保留现有数据，只报告阶段、HTTP 状态和错误类别。

按以下顺序执行：

1. 进入项目根目录。确认 .env 被 Git 忽略；若 .env 不存在，从 .env.example 创建它。
2. 使用 Invoke-WebRequest 获取 https://lpl.qq.com/web202301/js/rank.js 到内存。从脚本中解析官网前端当前使用的 Authorization 请求头值。解析过程和输出不得打印该值或完整脚本文本。
3. 若未能解析到非空 Authorization，停止并报告“官网脚本中未找到当前 Authorization”，不要继续。
4. 使用 .NET RandomNumberGenerator 生成 32 字节随机十六进制 INTERNAL_API_TOKEN。将两个值同时写入当前 PowerShell 环境和项目根目录 .env；.env 仅保留本地使用，禁止 Git 暂存。
5. 运行 .\scripts\check-tjstats.ps1。只有 LPL 页面、rank.js 和带 Authorization 的 TJStats 检查均成功时才能继续；否则停止。
6. 使用 docker compose -f deploy/docker-compose.yml up -d --force-recreate backend，等待 backend health 为 healthy。不要重置 MySQL、Redis volume，也不要删除容器或数据。
7. 使用 X-Internal-Token 直连 http://localhost:8080：
   - POST /api/internal/catalog/sync?seasonId=237
   - POST /api/internal/collections/teams，请求体 {"seasonId":237,"stageIds":[112,113,100]}
   - POST /api/internal/collections/players，请求体 {"seasonId":237,"stageIds":[112,113,100]}
   每次请求只输出 runId、status、changedRecords、unchangedStageIds 或失败的 HTTP 状态；绝不输出 Token、Authorization 或原始响应。
8. 只有 team 和 player 两次采集都返回 SUCCESS 或 NO_CHANGE，才进入验收。否则停止并报告哪类采集失败。
9. 验收：
   - docker compose ps 显示 backend、frontend、mysql、redis 均健康。
   - 通过 Nginx 的 http://localhost:8081 分别调用 team 和 player 公共查询接口两次：
     /api/v1/statistics/teams?stageKeys=237:112,237:113,237:100&minimumMatchCount=5&sortBy=winningRate&sortDirection=desc
     /api/v1/statistics/players?stageKeys=237:112,237:113,237:100&minimumMatchCount=5&sortBy=kda&sortDirection=desc
     每种统计的响应必须 success=true、total 大于 0；第二次请求用于验证缓存。
   - 在 MySQL 中只读统计 team_stage_stat_current、player_stage_stat_current、对应 snapshot 表和 collection_run 的行数，确认 current 行数大于 0。
   - 在 Redis 中只读查找 loldatahub:stats:s3:*:team:* 和 loldatahub:stats:s3:*:player:* 键，确认两类键都存在。需兼容 REDIS_PASSWORD 为空或非空两种情况。
   - 运行 mvn -q test 和在 frontend 目录运行 npm.cmd run build。
   - 执行 git status --short，确认 .env 未出现在状态中，且没有凭据被暂存。
10. 最后报告：执行命令（脱敏）、各采集 runId/status/changedRecords、MySQL 行数、两类 API total、Redis 键是否存在、测试与构建结果、失败项和待解决问题。不要报告任何实际令牌值。
```

## 推荐的安全实现片段

以下片段供 Claude Code 使用。变量始终只在内存中保存，不能 `Write-Host` 或
`Write-Output` 变量值。

### 提取 Authorization

```powershell
$rankJs = (Invoke-WebRequest `
  -Uri 'https://lpl.qq.com/web202301/js/rank.js' `
  -UseBasicParsing `
  -TimeoutSec 20 `
  -ErrorAction Stop).Content

$authMatch = [regex]::Match(
  $rankJs,
  '(?i)["'']?authorization["'']?\s*:\s*["'']([^"'']+)["'']'
)

if (-not $authMatch.Success -or [string]::IsNullOrWhiteSpace($authMatch.Groups[1].Value)) {
  throw '官网脚本中未找到当前 Authorization'
}

$tjAuth = $authMatch.Groups[1].Value
```

若官网脚本格式变化，Claude Code 应停止并报告，不能把整份脚本打印出来进行人工搜索。

### 生成内部令牌

```powershell
$tokenBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($tokenBytes)
$rng.Dispose()
$internalToken = ([BitConverter]::ToString($tokenBytes) -replace '-', '').ToLowerInvariant()
```

### 仅输出采集摘要

```powershell
$headers = @{ 'X-Internal-Token' = $internalToken }
$payload = '{"seasonId":237,"stageIds":[112,113,100]}'

$teamRun = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/internal/collections/teams' `
  -Method Post -Headers $headers -ContentType 'application/json' -Body $payload -ErrorAction Stop

$teamRun.data | Select-Object runId, status, changedRecords, unchangedStageIds
```

对 player 采集使用相同模式。不要显示 `$headers`、`$tjAuth`、`$internalToken`、
`.env` 内容或官网原始 JSON。

## 成功标准

完成后必须同时满足：

- TEAM 和 PLAYER 至少各有一条真实 current 数据；
- 两类公共统计接口均可通过 Nginx 查询到非空结果；
- 两类查询的 Redis 缓存键均存在；
- 测试、前端构建和容器健康检查通过；
- `.env` 未被 Git 跟踪，真实凭据未出现在 Git diff 或报告中。
