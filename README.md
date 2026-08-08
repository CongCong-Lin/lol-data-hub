# LoL Data Hub

英雄联盟赛事数据采集、MySQL 持久化、Redis 缓存和跨赛段分析平台。

## 功能概述

### 三类赛事统计

- **HERO（英雄统计）**：Pick/Ban 次数、胜率、KDA，支持跨赛段累计重算。
- **TEAM（战队统计）**：战队胜场、比赛场次、胜率等指标，支持跨赛段汇总。
- **PLAYER（选手统计）**：选手 KDA、场均数据等指标，支持按位置筛选。

### 核心特性

- **样本过滤**：查询时可设置最低出场次数阈值（英雄默认 10，战队/选手默认 5），数据库保留全部原始数据。
- **跨赛段重算**：多赛段查询时，先累计原始计数（pickCount、banCount、winningCount 等），再计算胜率、BP 率和 KDA，不直接平均官网百分比。
- **手动采集**：通过内部接口手动触发英雄、战队、选手数据采集；自动调度已预留但默认关闭。
- **数据快照**：每次变化的采集保留快照，支持版本窗口增量分析。

## 快速启动（Docker Compose 一键部署）

### 1. 配置环境变量

```powershell
cp .env.example .env
# 编辑 .env，填入 TJSTATS_AUTHORIZATION 和 INTERNAL_API_TOKEN
```

或者直接设置系统环境变量：

```powershell
$env:TJSTATS_AUTHORIZATION = "你的官网 Authorization 值"
$env:INTERNAL_API_TOKEN = "你的内部接口令牌"
```

> **注意**：`TJSTATS_BASE_URL` 默认使用 `https://open.tjstats.com/match-auth-app/open/v1`。如果部署环境无法直连（如使用代理或 VPN 导致出口 IP 不稳定），可设置为境内转发服务地址。
>
> `INTERNAL_API_TOKEN` 为内部接口访问令牌，为空时内部接口返回 503 不可用。建议使用 `openssl rand -hex 32` 生成。

### 2. 启动全部服务

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

使用默认配置时，启动后访问 `http://localhost:8081`；修改 `FRONTEND_PORT` 后请使用对应端口。

### 3. 验收检查

启动后运行验收脚本，验证服务状态、接口可用性和数据完整性：

```powershell
.\scripts\verify-compose.ps1
```

可选参数：`-StageKeys`（英雄统计 stageKeys，默认 `237:112,237:113,237:100`）和 `-MinimumPickCount`（最低出场次数，默认 `10`）。

### 4. 初始化赛季目录

```powershell
curl -X POST http://localhost:8080/api/internal/catalog/sync `
  -H "X-Internal-Token: $env:INTERNAL_API_TOKEN"
```

### 5. 手动采集数据

英雄统计采集示例：

```powershell
curl -X POST http://localhost:8080/api/internal/collections/heroes `
  -H "X-Internal-Token: $env:INTERNAL_API_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"seasonId": 237, "stageIds": [112, 113, 100]}'
```

战队统计采集：

```powershell
curl -X POST http://localhost:8080/api/internal/collections/teams `
  -H "X-Internal-Token: $env:INTERNAL_API_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"seasonId": 237, "stageIds": [112]}'
```

选手统计采集：

```powershell
curl -X POST http://localhost:8080/api/internal/collections/players `
  -H "X-Internal-Token: $env:INTERNAL_API_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"seasonId": 237, "stageIds": [112]}'
```

## 本地开发

### 后端

```powershell
# 启动 MySQL 和 Redis
docker compose -f deploy/docker-compose.yml up -d mysql redis

# 设置凭据
$env:TJSTATS_AUTHORIZATION = "你的值"

# 打包并运行
mvn -pl backend/lol-datahub-application -am package -DskipTests
java -jar backend/lol-datahub-application/target/lol-datahub-application-0.1.0-SNAPSHOT.jar
```

后端默认端口 `8080`，MySQL 默认映射到宿主机 `3307` 端口（可通过 `MYSQL_PORT` 修改）。

> **端口安全**：MySQL、Redis 和后端端口均仅绑定 `127.0.0.1`，不会暴露到外部网络。前端端口（默认 `8081`）绑定 `0.0.0.0`，通过 Nginx 反向代理访问后端 API。

### 前端

```powershell
cd frontend
npm install
npm run dev
```

开发模式下 Vite 自动将 `/api` 请求代理到 `http://localhost:8080`。打开 `http://localhost:5173`。

### 测试与构建验证

```powershell
mvn test
cd frontend && npm run build
```

## 主要接口

### 公开查询接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/catalog/seasons` | 获取所有赛季列表 |
| GET | `/api/v1/catalog/stages?seasonId=237&statisticType=HERO` | 获取指定赛季的赛段列表（可选 HERO/TEAM/PLAYER） |
| GET | `/api/v1/catalog/stages/availability?statisticType=HERO&collectedOnly=false` | 获取全赛事赛段可用性（含 seasonName/collected/sampleBaseCount） |
| GET | `/api/v1/statistics/champions?stageKeys=237:102,239:28&minimumPickCount=10&sortBy=bpRate&sortDirection=desc` | 英雄统计查询（跨赛事，stageKeys 为 `seasonId:stageId` 复合键） |
| GET | `/api/v1/statistics/teams?stageKeys=237:102,239:28&minimumMatchCount=5` | 战队统计查询（跨赛事） |
| GET | `/api/v1/statistics/players?stageKeys=237:102,239:28&minimumMatchCount=5` | 选手统计查询（跨赛事） |

> **跨赛事查询说明**：`stageKeys` 参数使用 `seasonId:stageId` 复合键格式，多个用逗号分隔。旧参数 `seasonId` + `stageIds` 仍向后兼容，但推荐使用 `stageKeys` 以支持跨赛事选择。

### 内部采集接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/internal/catalog/sync?seasonId=237` | 同步赛季/赛段目录 |
| POST | `/api/internal/collections/heroes` | 采集英雄统计 |
| POST | `/api/internal/collections/teams` | 采集战队统计 |
| POST | `/api/internal/collections/players` | 采集选手统计 |

> **注意**：内部接口（`/api/internal/`）需要在请求头中携带 `X-Internal-Token` 进行鉴权，且必须直连后端端口（仅绑定 `127.0.0.1`），Nginx 不代理该路径（返回 404）。`INTERNAL_API_TOKEN` 为手动同步和采集的必填环境变量。

### 查询参数说明

- `minimumPickCount` / `minimumMatchCount`：最低出场次数过滤阈值。
- `sortBy`：排序字段（如 `bpRate`、`winningRate`、`kda`）。
- `sortDirection`：排序方向，`asc` 或 `desc`。

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `TJSTATS_AUTHORIZATION` | 是 | - | 官网请求凭据 |
| `INTERNAL_API_TOKEN` | 是 | - | 内部接口访问令牌（手动同步/采集必填，为空则内部接口返回 503） |
| `TJSTATS_BASE_URL` | 否 | `https://open.tjstats.com/match-auth-app/open/v1` | 数据源地址 |
| `FRONTEND_PORT` | 否 | `8081` | 前端宿主端口 |
| `BACKEND_PORT` | 否 | `8080` | 后端宿主端口（仅绑定 127.0.0.1） |
| `MYSQL_PORT` | 否 | `3307` | MySQL 宿主端口（仅绑定 127.0.0.1） |
| `REDIS_PORT` | 否 | `6379` | Redis 宿主端口（仅绑定 127.0.0.1） |
| `MYSQL_DATABASE` | 否 | `lol_data_hub` | MySQL 数据库名 |
| `MYSQL_USERNAME` | 否 | `loldatahub` | MySQL 应用账号 |
| `MYSQL_PASSWORD` | 否 | `loldatahub` | MySQL 应用账号密码（生产环境必须修改） |
| `MYSQL_ROOT_PASSWORD` | 否 | `root` | MySQL root 密码（生产环境必须修改） |

## 关闭自动调度

自动采集调度已预留但默认关闭。如需确认，查看 `application.yml` 中：

```yaml
lol-datahub:
  collector:
    scheduling:
      enabled: false
```

## 已知限制

- **tjstats 数据源可达性**：`open.tjstats.com` 在代理或 VPN 环境下可能不可用，需通过 `TJSTATS_BASE_URL` 切换到境内转发服务。
- **当前已实现的采集**：英雄统计已完整实现并可采集。战队和选手统计的采集接口已就绪，但具体数据尚需通过采集接口触发后才能查询。
- **官网接口稳定性**：使用的接口是官网前端内部接口，不等同于有稳定性承诺的开放 API。

## 文档

- [数据源说明](docs/data-source.md)
- [参考项目审计记录](docs/reference-loldata.md)

## Nginx 限流

公开查询接口（`/api/` 前缀，不包括 `/api/internal/`）内置 Nginx 请求限流：

- **速率**：每 IP 每秒 10 个请求
- **突发**：允许瞬时突发 20 个请求（`burst=20 nodelay`）
- **超限响应**：返回 `503 Service Temporarily Unavailable`

内部采集接口（`/api/internal/`）不受限流影响，仍由 Nginx 直接返回 404 隔离。

## 技术栈

- **后端**：Spring Boot 3.5 + MyBatis-Plus + Flyway + Redis
- **前端**：Vue 3 + TypeScript + Vite
- **数据库**：MySQL 8.4 + Redis 7.4
- **部署**：Docker Compose + Nginx 反向代理
