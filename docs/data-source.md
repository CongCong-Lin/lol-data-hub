# 数据源说明

## 已确认的官网接口

基础地址默认为：

```text
https://open.tjstats.com/match-auth-app/open/v1
```

当前已经确认以下接口：

| 用途 | 路径 | 主要参数 |
| --- | --- | --- |
| 赛季目录 | `/schedule/season` | 无 |
| 赛段目录 | `/schedule/stage` | `seasonId`；必须校验响应中的 `data.seasonId` 与请求一致 |
| 比赛列表 | `/schedule/match` | `seasonId` |
| 英雄聚合 | `/compound/public/hero` | `seasonId`, `stageIds` |
| 选手聚合 | `/compound/public/player` | `seasonId`, `stageIds` |
| 战队聚合 | `/compound/public/team` | `seasonId`, `stageIds` |
| 选手英雄记录 | `/compound/heroRecord` | `playerId`, `seasonId`, `stageIds` |

这些接口是官网前端使用的内部接口，不等同于有稳定性承诺的开放 API。请求凭据必须通过
`TJSTATS_AUTHORIZATION` 环境变量注入，禁止写入源码或下发给浏览器。

`/schedule/stage` 对部分不受支持的旧赛季不会返回 404，而是返回 `seasonId=0`、包含大量通用赛段的全局字典。目录同步必须把请求赛季 ID 当作响应契约的一部分；不一致时整批拒绝，禁止把该字典挂到请求赛季。当前实现还校验非空赛季名、非空赛段数组、正数且不重复的赛段 ID。

> **网络限制**：`open.tjstats.com` 在代理或 VPN 环境下可能不稳定或不可达。如果部署环境无法直连，可通过 `TJSTATS_BASE_URL` 切换到受控的境内转发服务。

## 跨赛段统计

英雄响应包含可累加计数：

- `pickCount`
- `banCount`
- `bpCount`
- `winningCount`
- `totalKills`
- `totalDeath`
- `totalAssists`

平台按单赛段采集并保存这些计数。跨赛段时先累计计数，再计算胜率、BP 率和 KDA，禁止直接平均官网百分比。

## 三类统计说明

### 英雄统计（HERO）

英雄整体的 Pick/Ban 指标来自 `/compound/public/hero`；实际登场分路与分路内的胜负、K/D/A 则来自
`/compound/heroRecord` 的逐选手、逐局记录。官网英雄聚合响应中的 `heroLocation` 是宽泛标签，不能代表
所选赛段的真实登场分路，因此平台不再用它生成分路或执行筛选。

采集发布前会校验逐局记录数等于比赛局数 × 10、每局由两支各 5 人战队组成、五个分路各出现 2 次，
并要求逐局 Pick/胜场/K/D/A 合计与官网英雄聚合完全一致。任何一项失败都会保留原始响应并拒绝覆盖
当前数据。查询支持最低出场次数过滤（默认 `minimumPickCount=10`）以及
`TOP/JUN/MID/BOT/SUP` 实际分路筛选。

禁用行为本身没有实际登场分路。选择分路时，Pick、胜负与 KDA 使用该分路独立数据，Ban 使用所选
赛段的英雄整体禁用数据，分路 BP 率按“该分路出场率 + 整体禁用率”计算；前端会明确提示这一口径。

### 战队统计（TEAM）

通过 `/compound/public/team` 接口采集。保存战队的胜场、比赛场次、胜率等指标。
查询时支持最低比赛场次过滤（默认 `minimumMatchCount=5`）。

### 选手统计（PLAYER）

通过 `/compound/public/player` 接口采集。保存选手的 KDA、场均数据等指标。
查询时支持按位置筛选和最低比赛场次过滤（默认 `minimumMatchCount=5`）。

## 快照与版本分析

每次发生变化的采集都会保留快照。后续可以将两个快照的累计计数相减，得到两个更新时间之间的增量，
用于近似分析某个版本窗口，而不是依赖官网提供逐条记录的版本号。

## 稳定性策略

- 官网连接失败时继续提供最后一次成功数据。
- 每次响应保存原文与 SHA-256 哈希。
- 响应结构校验通过后才更新当前数据。
- 相同哈希不重复生成统计快照。
- 自动调度当前默认关闭，先通过人工接口采集。
