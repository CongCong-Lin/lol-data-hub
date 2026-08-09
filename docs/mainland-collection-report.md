# 真实数据采集与跨赛事验收报告

执行时间：2026-08-09

本报告不包含任何 Authorization、内部令牌或数据库密码。

## 数据范围

- 2026 职业联赛第二赛段：`237:102`
- 2026 季中冠军赛：`239:28`、`239:18`
- 原有第一赛段数据：`237:112`、`237:113`、`237:100`

三类统计均已有两个赛事、六个已采集赛段可供前端选择。

## 本轮真实采集

| runId | 类型 | 赛季 | 赛段 | 状态 | changedRecords |
|---:|---|---:|---|---|---:|
| 14 | HERO | 239 | 28,18 | SUCCESS | 179 |
| 15 | TEAM | 239 | 28,18 | SUCCESS | 14 |
| 16 | PLAYER | 239 | 28,18 | SUCCESS | 62 |
| 17 | HERO | 237 | 102 | SUCCESS | 105 |
| 18 | TEAM | 237 | 102 | SUCCESS | 15 |
| 19 | PLAYER | 237 | 102 | SUCCESS | 77 |

## 跨赛事查询验证

查询键统一使用：`237:102,239:28,239:18`。

| 类型 | minimum | total | 加总字段错误 | 派生指标错误 |
|---|---:|---:|---:|---:|
| HERO | 0 | 116 | 0 | BP 率错误 0 |
| TEAM | 0 | 23 | 0 | 场均击杀错误 0 |
| PLAYER | 0 | 121 | 0 | 场均击杀错误 0，MVP 票数错误 0 |

英雄最低出场次数设为 10 后返回 66 个英雄。相同查询连续执行两次响应一致，并生成规范化 Redis 键：

```text
loldatahub:stats:s4:v13:champion:237:102,239:18,239:28:10:bpRate:DESC
```

## 当前数据库状态

| 项目 | 数量 |
|---|---:|
| champion | 129 |
| champion_stage_stat_current | 568 |
| team_stage_stat_current | 56 |
| player_stage_stat_current | 287 |
| champion_stage_stat_snapshot | 568 |
| team_stage_stat_snapshot | 86 |
| player_stage_stat_snapshot | 438 |
| collection_run | 17 |
| source_raw_response | 30 |
| system_state.data_version | 13 |

数据不变量检查结果均为 0：非法计数、非成功任务被 current 引用、current 与批次标记不一致、悬挂 `RUNNING` 任务。

## 目录异常与修复验证

官网 `/schedule/stage` 对旧赛季 152 返回 `seasonId=0` 的全局赛段字典。旧实现曾错误写入 107 条未采集赛段。

- 新契约门禁对该响应返回 HTTP 502，并明确报告请求/返回赛季不一致。
- V9 清理后赛季 152 的污染赛段数量为 0。
- 已采集的 HERO、TEAM、PLAYER 行数未因清理发生损失。
- 赛季 239 正确同步为 2 个赛段。

## 迁移、备份与运行验证

- Flyway：V1–V10 全部成功。
- 最新备份：`lol_data_hub_20260809_062523_876_d0018944.sql`，1.42 MB，脚本已完成文件头和 SHA-256 校验。
- Maven：287 个测试用例，0 失败。
- 前端：Vitest 8/8 通过，生产构建通过。
- Compose：四个服务均为 `running + healthy`，验收脚本 19/19 通过。
- Nginx：安全头、gzip、静态资源一年缓存、缺失资源 404、限流 429、后端重建后的动态 DNS 反代均已实测通过。
- Redis：`maxmemory=256mb`、`allkeys-lru` 已生效。
