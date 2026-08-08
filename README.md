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
# 编辑 .env，填入 TJSTATS_AUTHORIZATION
```

或者直接设置系统环境变量：

```powershell
$env:TJSTATS_AUTHORIZATION = "你的官网 Authorization 值"
```

> **注意**：`TJSTATS_BASE_URL` 默认使用 `https://open.tjstats.com/match-auth-app/open/v1`。如果部署环境无法直连（如使用代理或 VPN 导致出口 IP 不稳定），可设置为境内转发服务地址。

### 2. 启动全部服务

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

启动后访问 `http://localhost:${FRONTEND_PORT:-8081}`。

### 3. 初始化赛季目录

```powershell
curl -X POST http://localhost:${BACKEND_PORT:-8080}/api/internal/catalog/sync
```

### 4. 手动采集数据

英雄统计采集示例：

```powershell
curl -X POST http://localhost:${BACKEND_PORT:-8080}/api/internal/collections/heroes `
  -H "Content-Type: application/json" `
  -d '{"seasonId": 237, "stageIds": [112, 113, 100]}'
```

战队统计采集：

```powershell
curl -X POST http://localhost:${BACKEND_PORT:-8080}/api/internal/collections/teams `
  -H "Content-Type: application/json" `
  -d '{"seasonId": 237, "stageIds": [112]}'
```

选手统计采集：

```powershell
curl -X POST http://localhost:${BACKEND_PORT:-8080}/api/internal/collections/players `
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
| GET | `/api/v1/statistics/champions?seasonId=237&stageIds=112,113` | 英雄统计查询 |
| GET | `/api/v1/statistics/teams?seasonId=237&stageIds=112` | 战队统计查询 |
| GET | `/api/v1/statistics/players?seasonId=237&stageIds=112&position=mid` | 选手统计查询 |

### 内部采集接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/internal/catalog/sync?seasonId=237` | 同步赛季/赛段目录 |
| POST | `/api/internal/collections/heroes` | 采集英雄统计 |
| POST | `/api/internal/collections/teams` | 采集战队统计 |
| POST | `/api/internal/collections/players` | 采集选手统计 |

> **注意**：内部接口（`/api/internal/`）需要直接访问后端端口，Nginx 代理层会对该路径返回 404。

### 查询参数说明

- `minimumPickCount` / `minimumMatchCount`：最低出场次数过滤阈值。
- `sortBy`：排序字段（如 `bpRate`、`winningRate`、`kda`）。
- `sortDirection`：排序方向，`asc` 或 `desc`。

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `TJSTATS_AUTHORIZATION` | 是 | - | 官网请求凭据 |
| `TJSTATS_BASE_URL` | 否 | `https://open.tjstats.com/match-auth-app/open/v1` | 数据源地址 |
| `FRONTEND_PORT` | 否 | `8081` | 前端宿主端口 |
| `BACKEND_PORT` | 否 | `8080` | 后端宿主端口（仅绑定 127.0.0.1） |
| `MYSQL_PORT` | 否 | `3307` | MySQL 宿主端口 |
| `REDIS_PORT` | 否 | `6379` | Redis 宿主端口 |
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

## 技术栈

- **后端**：Spring Boot 3.5 + MyBatis-Plus + Flyway + Redis
- **前端**：Vue 3 + TypeScript + Vite
- **数据库**：MySQL 8.4 + Redis 7.4
- **部署**：Docker Compose + Nginx 反向代理
