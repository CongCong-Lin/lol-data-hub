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
| 赛段目录 | `/schedule/stage` | `seasonId` |
| 比赛列表 | `/schedule/match` | `seasonId` |
| 英雄聚合 | `/compound/public/hero` | `seasonId`, `stageIds` |
| 选手聚合 | `/compound/public/player` | `seasonId`, `stageIds` |
| 战队聚合 | `/compound/public/team` | `seasonId`, `stageIds` |
| 选手英雄记录 | `/compound/heroRecord` | `playerId`, `seasonId`, `stageIds` |

这些接口是官网前端使用的内部接口，不等同于有稳定性承诺的开放 API。请求凭据必须通过
`TJSTATS_AUTHORIZATION` 环境变量注入，禁止写入源码或下发给浏览器。

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

通过 `/compound/public/hero` 接口采集。保存英雄的 Pick/Ban 次数、胜率、KDA 等指标。
查询时支持最低出场次数过滤（默认 `minimumPickCount=10`），数据库保留全部原始数据。

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
