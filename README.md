# LoL Data Hub

英雄联盟赛事数据采集、MySQL 持久化、Redis 缓存和跨赛段分析平台。

## 当前能力

- Spring Boot 3 + MyBatis-Plus 模块化单体。
- Vue 3 + TypeScript 查询页面。
- MySQL 保存赛事目录、原始响应、当前英雄统计和历史快照。
- Redis 通过数据版本隔离缓存。
- 人工同步赛事目录和人工采集英雄统计。
- 多赛段计数求和后重新计算 BP 率、胜率和 KDA。
- 最低出场次数过滤，默认排除低样本英雄。
- 自动调度预留但默认关闭。

## 本地启动

### 1. 启动 MySQL 和 Redis

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

MySQL 默认映射到宿主机 `3307` 端口，避免与本机已有的 MySQL `3306` 冲突；可通过 `MYSQL_PORT` 修改。

### 2. 配置官网请求凭据

不要把凭据提交到仓库：

```powershell
$env:TJSTATS_AUTHORIZATION = "从官网当前前端配置中取得的值"
```

如需使用境内转发服务，可设置：

```powershell
$env:TJSTATS_BASE_URL = "https://你的服务/match-auth-app/open/v1"
```

### 3. 启动后端

先打包：

```powershell
mvn -pl backend/lol-datahub-application -am package -DskipTests
```

再启动（确保在已配置 `TJSTATS_AUTHORIZATION` 的终端中执行）：

```powershell
java -jar backend\lol-datahub-application\target\lol-datahub-application-0.1.0-SNAPSHOT.jar
```

### 4. 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

打开 `http://localhost:5173`。

## 主要接口

```text
POST /api/v1/catalog/sync?seasonId=237
GET  /api/v1/catalog/seasons
GET  /api/v1/catalog/stages?seasonId=237
POST /api/internal/collections/heroes
GET  /api/v1/statistics/champions
```

采集请求示例：

```json
{
  "seasonId": 237,
  "stageIds": [112, 113, 100]
}
```

详细数据源说明见 [docs/data-source.md](docs/data-source.md)。

参考项目的源码审计与后续产品设计候选见 [docs/reference-loldata.md](docs/reference-loldata.md)。
