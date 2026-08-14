package com.loldatahub.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroRecordSourceRecord;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.MatchPlayerGameSourceRecord;
import com.loldatahub.source.model.MatchPlayerMetricSourceRecord;
import com.loldatahub.source.model.MatchTeamMetricSourceRecord;
import com.loldatahub.source.model.PlayerHeroRecordPayload;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import com.loldatahub.source.model.MatchPlayerPositionSourceRecord;
import com.loldatahub.source.model.SeasonSourceRecord;
import com.loldatahub.source.model.SeasonStagesSourceRecord;
import com.loldatahub.source.model.StageSourceRecord;
import com.loldatahub.source.model.TeamStatSourceRecord;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TjStatsResponseParser {
    private static final Set<String> VALID_POSITIONS = Set.of("TOP", "JUG", "MID", "AD", "SUP");

    private final ObjectMapper objectMapper;

    public TjStatsResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SeasonSourceRecord> parseSeasons(String rawJson) {
        JsonNode data = validatedData(rawJson);
        if (!data.isArray() || data.isEmpty()) {
            throw new TjStatsSourceException("SEASON: data 必须是非空数组");
        }
        requireIntegralFields(data, "SEASON", "seasonId");
        List<SeasonSourceRecord> seasons = objectMapper.convertValue(
                data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, SeasonSourceRecord.class)
        );
        Set<Long> seasonIds = new HashSet<>();
        for (SeasonSourceRecord season : seasons) {
            if (season.seasonId() <= 0) {
                throw new TjStatsSourceException("SEASON: seasonId 必须大于 0，实际值: " + season.seasonId());
            }
            if (season.seasonName() == null || season.seasonName().isBlank()) {
                throw new TjStatsSourceException("SEASON: seasonName 不能为空，seasonId=" + season.seasonId());
            }
            if (!seasonIds.add(season.seasonId())) {
                throw new TjStatsSourceException("SEASON: seasonId 重复: " + season.seasonId());
            }
        }
        return seasons;
    }

    public SeasonStagesSourceRecord parseStages(String rawJson, long expectedSeasonId) {
        if (expectedSeasonId <= 0) {
            throw new IllegalArgumentException("期望赛季 ID 必须大于 0");
        }

        JsonNode data = validatedData(rawJson);
        if (!data.isObject()) {
            throw new TjStatsSourceException("STAGE: data 必须是对象");
        }
        JsonNode stageInfos = data.path("stageInfos");
        if (!stageInfos.isArray() || stageInfos.isEmpty()) {
            throw new TjStatsSourceException("STAGE: stageInfos 必须是非空数组");
        }
        requireIntegralFields(stageInfos, "STAGE", "stageId");

        SeasonStagesSourceRecord result = objectMapper.convertValue(data, SeasonStagesSourceRecord.class);
        if (result.seasonId() != expectedSeasonId) {
            throw new TjStatsSourceException(
                    "STAGE: 返回赛季 ID 与请求不一致，期望 " + expectedSeasonId + "，实际 " + result.seasonId());
        }
        if (result.seasonName() == null || result.seasonName().isBlank()) {
            throw new TjStatsSourceException("STAGE: seasonName 不能为空");
        }

        Set<Long> stageIds = new HashSet<>();
        for (StageSourceRecord stage : result.stageInfos()) {
            if (stage.stageId() <= 0) {
                throw new TjStatsSourceException("STAGE: stageId 必须大于 0，实际值: " + stage.stageId());
            }
            if (stage.stageName() == null || stage.stageName().isBlank()) {
                throw new TjStatsSourceException("STAGE: stageName 不能为空，stageId=" + stage.stageId());
            }
            if (!stageIds.add(stage.stageId())) {
                throw new TjStatsSourceException("STAGE: stageId 重复: " + stage.stageId());
            }
        }
        return result;
    }

    public HeroStagePayload parseHeroStage(String rawJson) {
        JsonNode data = validatedData(rawJson);

        if (!data.isObject()) {
            throw new TjStatsSourceException("HERO: data 必须是对象");
        }
        JsonNode listNode = data.path("list");
        if (listNode.isMissingNode() || listNode.isNull() || !listNode.isArray() || listNode.isEmpty()) {
            throw new TjStatsSourceException("HERO: data.list 必须是非空数组");
        }
        requireIntegralFields(listNode, "HERO", "heroId", "pickCount", "banCount", "bpCount",
                "winningCount", "totalKills", "totalAssists", "totalDeath");
        long boCount = data.path("boCount").asLong(0);
        if (boCount <= 0) {
            throw new TjStatsSourceException("HERO: boCount 必须大于 0，实际值: " + boCount);
        }

        List<HeroStatSourceRecord> heroes = objectMapper.convertValue(
                listNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, HeroStatSourceRecord.class)
        );

        validateHeroes(heroes, boCount);

        long updatedAt = data.path("updatedAt").asLong(0L);
        JsonNode gameVersion = data.path("gameVersion");
        if (gameVersion.isMissingNode() || gameVersion.isNull()) {
            gameVersion = objectMapper.createArrayNode();
        }
        return new HeroStagePayload(
                boCount,
                updatedAt > 0 ? Instant.ofEpochSecond(updatedAt) : null,
                gameVersion,
                heroes
        );
    }

    public List<TeamStatSourceRecord> parseTeamStage(String rawJson) {
        JsonNode data = validatedData(rawJson);

        if (!data.isArray() || data.isEmpty()) {
            throw new TjStatsSourceException("TEAM: data 必须是非空数组");
        }
        requireIntegralFields(data, "TEAM", "teamId", "matchCount", "gameCount",
                "matchWinCount", "totalKills", "totalDeath");

        List<TeamStatSourceRecord> teams = objectMapper.convertValue(
                data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TeamStatSourceRecord.class)
        );

        validateTeams(teams);
        return teams;
    }

    public List<PlayerStatSourceRecord> parsePlayerStage(String rawJson) {
        JsonNode data = validatedData(rawJson);

        if (!data.isArray() || data.isEmpty()) {
            throw new TjStatsSourceException("PLAYER: data 必须是非空数组");
        }
        // 官网历史赛事会返回进入名单但没有实际登场的选手。此类记录没有统计样本，
        // 应在契约校验前明确剔除，不能用全零指标污染结果，也不能阻塞整个赛段。
        requireIntegralFields(data, "PLAYER", "matchCount", "boCount");
        var activePlayerNodes = objectMapper.createArrayNode();
        data.forEach(player -> {
            if (player.path("matchCount").longValue() != 0
                    || player.path("boCount").longValue() != 0) {
                activePlayerNodes.add(player);
            }
        });
        if (activePlayerNodes.isEmpty()) {
            throw new TjStatsSourceException("PLAYER: data 至少需要包含一名有出场记录的选手");
        }
        requireIntegralFields(activePlayerNodes, "PLAYER", "matchCount", "boCount", "mvpCount",
                "totalKills", "totalAssists", "totalDeath");
        requireNumericFields(activePlayerNodes, "PLAYER", "mvpVotes");

        List<PlayerStatSourceRecord> players = objectMapper.convertValue(
                activePlayerNodes,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PlayerStatSourceRecord.class)
        );

        validatePlayers(players);
        return players;
    }

    public PlayerHeroRecordPayload parsePlayerHeroRecords(String rawJson, long expectedPlayerId) {
        if (expectedPlayerId <= 0) {
            throw new IllegalArgumentException("期望选手 ID 必须大于 0");
        }
        JsonNode data = validatedData(rawJson);
        if (!data.isObject()) {
            throw new TjStatsSourceException("HERO_RECORD: data 必须是对象");
        }

        JsonNode playerIdNode = data.get("playerID");
        if (playerIdNode == null || !playerIdNode.isIntegralNumber()) {
            throw new TjStatsSourceException("HERO_RECORD: 缺少整数类型关键字段 playerID");
        }
        long playerId = playerIdNode.longValue();
        if (playerId != expectedPlayerId) {
            throw new TjStatsSourceException(
                    "HERO_RECORD: 返回选手 ID 与请求不一致，期望 " + expectedPlayerId + "，实际 " + playerId);
        }

        JsonNode listNode = data.get("heroRecordList");
        if (listNode == null || !listNode.isArray() || listNode.isEmpty()) {
            throw new TjStatsSourceException("HERO_RECORD: heroRecordList 必须是非空数组，playerId=" + playerId);
        }
        requireIntegralFields(listNode, "HERO_RECORD", "heroID", "matchID", "bo", "kill", "death",
                "assist", "teamID", "winTeamID");
        List<HeroRecordSourceRecord> records = objectMapper.convertValue(
                listNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, HeroRecordSourceRecord.class)
        );

        Set<String> gameKeys = new HashSet<>();
        Set<String> validRoles = Set.of("TOP", "JUN", "MID", "BOT", "SUP");
        for (int index = 0; index < records.size(); index++) {
            HeroRecordSourceRecord record = records.get(index);
            String prefix = "HERO_RECORD[" + index + "]";
            if (record.heroId() < 0 || record.matchId() <= 0 || record.bo() <= 0
                    || record.teamId() <= 0 || record.winTeamId() <= 0) {
                throw new TjStatsSourceException(prefix + ": 英雄 ID 不得小于 0，比赛、局次与战队 ID 必须大于 0");
            }
            if (record.heroId() == 0
                    && (!isBlank(record.heroName()) || !isBlank(record.heroTitle()))) {
                throw new TjStatsSourceException(prefix + ": heroId=0 仅允许用于官网英雄信息同时缺失的占位记录");
            }
            requireNonNegative(prefix, "kill", record.kill());
            requireNonNegative(prefix, "death", record.death());
            requireNonNegative(prefix, "assist", record.assist());
            String role = record.role() == null ? "" : record.role().trim().toUpperCase(Locale.ROOT);
            // 2023—2025 的历史逐局接口会把 role 返回为空；采集层优先使用比赛详情补全，
            // 仅在详情也是官网空占位数据时使用赛段 playerLocation 兜底，并继续执行每局 5 路校验。
            if (!role.isEmpty() && !validRoles.contains(role)) {
                throw new TjStatsSourceException(
                        prefix + ": role 必须属于 TOP/JUN/MID/BOT/SUP，实际值: " + record.role());
            }
            String gameKey = record.matchId() + ":" + record.bo();
            if (!gameKeys.add(gameKey)) {
                throw new TjStatsSourceException(prefix + ": 同一选手存在重复对局记录 " + gameKey);
            }
        }
        return new PlayerHeroRecordPayload(playerId, records);
    }

    public List<MatchPlayerPositionSourceRecord> parseMatchPlayerPositions(String rawJson, long expectedMatchId) {
        return parseMatchPlayerGames(rawJson, expectedMatchId).stream()
                .map(row -> new MatchPlayerPositionSourceRecord(
                        row.matchId(), row.bo(), row.playerId(), row.position()))
                .toList();
    }

    public List<MatchPlayerGameSourceRecord> parseMatchPlayerGames(String rawJson, long expectedMatchId) {
        if (expectedMatchId <= 0) {
            throw new IllegalArgumentException("期望比赛 ID 必须大于 0");
        }
        JsonNode data = validatedData(rawJson);
        if (!data.isObject()) {
            throw new TjStatsSourceException("MATCH_DETAIL: data 必须是对象");
        }
        JsonNode matchIdNode = data.get("matchId");
        if (matchIdNode == null || !matchIdNode.isIntegralNumber()
                || matchIdNode.longValue() != expectedMatchId) {
            throw new TjStatsSourceException(
                    "MATCH_DETAIL: 返回比赛 ID 与请求不一致，期望 " + expectedMatchId);
        }
        JsonNode matchInfos = data.get("matchInfos");
        if (matchInfos == null || !matchInfos.isArray() || matchInfos.isEmpty()) {
            throw new TjStatsSourceException("MATCH_DETAIL: matchInfos 必须是非空数组，matchId=" + expectedMatchId);
        }

        List<MatchPlayerGameSourceRecord> records = new java.util.ArrayList<>();
        Set<Long> seenBos = new HashSet<>();
        for (int gameIndex = 0; gameIndex < matchInfos.size(); gameIndex++) {
            JsonNode game = matchInfos.get(gameIndex);
            JsonNode boNode = game.get("bo");
            if (boNode == null || !boNode.isIntegralNumber() || boNode.longValue() <= 0) {
                throw new TjStatsSourceException("MATCH_DETAIL[" + gameIndex + "]: bo 必须大于 0");
            }
            long bo = boNode.longValue();
            if (!seenBos.add(bo)) {
                throw new TjStatsSourceException("MATCH_DETAIL: bo 重复，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            JsonNode teamInfos = game.get("teamInfos");
            if (teamInfos == null || !teamInfos.isArray() || teamInfos.size() != 2) {
                throw new TjStatsSourceException(
                        "MATCH_DETAIL: 每局必须包含两支战队，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            if (isOfficialEmptyGamePlaceholder(teamInfos)) {
                continue;
            }
            JsonNode winTeamIdNode = game.get("matchWin");
            if (winTeamIdNode == null || !winTeamIdNode.isIntegralNumber()
                    || winTeamIdNode.longValue() <= 0) {
                throw new TjStatsSourceException(
                        "MATCH_DETAIL: matchWin 必须大于 0，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            long winTeamId = winTeamIdNode.longValue();
            Set<Long> playerIds = new HashSet<>();
            Set<Long> teamIds = new HashSet<>();
            java.util.Map<String, Integer> positionCounts = new java.util.HashMap<>();
            for (JsonNode team : teamInfos) {
                JsonNode teamIdNode = team.get("teamId");
                if (teamIdNode == null || !teamIdNode.isIntegralNumber() || teamIdNode.longValue() <= 0) {
                    throw new TjStatsSourceException("MATCH_DETAIL: teamId 必须大于 0");
                }
                long teamId = teamIdNode.longValue();
                if (!teamIds.add(teamId)) {
                    throw new TjStatsSourceException(
                            "MATCH_DETAIL: 同一局战队重复，matchId=" + expectedMatchId + "，bo=" + bo);
                }
                JsonNode playerInfos = team.get("playerInfos");
                if (playerInfos == null || !playerInfos.isArray() || playerInfos.size() != 5) {
                    throw new TjStatsSourceException(
                            "MATCH_DETAIL: 每支战队每局必须包含 5 名选手，matchId=" + expectedMatchId + "，bo=" + bo);
                }
                for (JsonNode player : playerInfos) {
                    JsonNode playerIdNode = player.get("playerId");
                    if (playerIdNode == null || !playerIdNode.isIntegralNumber() || playerIdNode.longValue() <= 0) {
                        throw new TjStatsSourceException("MATCH_DETAIL: playerId 必须大于 0");
                    }
                    long playerId = playerIdNode.longValue();
                    if (!playerIds.add(playerId)) {
                        throw new TjStatsSourceException(
                                "MATCH_DETAIL: 同一局选手重复，matchId=" + expectedMatchId + "，bo=" + bo
                                        + "，playerId=" + playerId);
                    }
                    String position = normalizeMatchPosition(player.path("playerLocation").asText(""));
                    JsonNode heroIdNode = player.get("heroId");
                    if (heroIdNode == null || !heroIdNode.isIntegralNumber() || heroIdNode.longValue() <= 0) {
                        throw new TjStatsSourceException("MATCH_DETAIL: heroId 必须大于 0");
                    }
                    JsonNode battleDetail = player.get("battleDetail");
                    if (battleDetail == null || !battleDetail.isObject()) {
                        throw new TjStatsSourceException("MATCH_DETAIL: battleDetail 必须是对象");
                    }
                    long kills = requireNonNegativeIntegral(
                            battleDetail, "kills", expectedMatchId, bo, playerId);
                    long deaths = requireNonNegativeIntegral(
                            battleDetail, "death", expectedMatchId, bo, playerId);
                    long assists = requireNonNegativeIntegral(
                            battleDetail, "assist", expectedMatchId, bo, playerId);
                    positionCounts.merge(position, 1, Integer::sum);
                    records.add(new MatchPlayerGameSourceRecord(
                            expectedMatchId, bo, playerId, position, heroIdNode.longValue(),
                            player.path("heroName").asText(""), player.path("heroTitle").asText(""),
                            teamId, winTeamId, kills, deaths, assists
                    ));
                }
            }
            if (!teamIds.contains(winTeamId)) {
                throw new TjStatsSourceException(
                        "MATCH_DETAIL: matchWin 不属于本局战队，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            if (!positionCounts.keySet().equals(Set.of("TOP", "JUN", "MID", "BOT", "SUP"))
                    || positionCounts.values().stream().anyMatch(count -> count != 2)) {
                throw new TjStatsSourceException(
                        "MATCH_DETAIL: 每局五个分路必须各出现 2 次，matchId=" + expectedMatchId + "，bo=" + bo);
            }
        }
        return List.copyOf(records);
    }

    /**
     * 从比赛详情逐局重算选手比例。
     *
     * 官网 /compound/public/player 返回的比例只有聚合后的低精度值；比赛详情同时包含
     * 选手和战队的原始击杀、伤害、经济，因此这里按单局分子/分母计算，再由采集层按局数求平均。
     */
    public List<MatchPlayerMetricSourceRecord> parseMatchPlayerMetrics(String rawJson, long expectedMatchId) {
        if (expectedMatchId <= 0) {
            throw new IllegalArgumentException("期望比赛 ID 必须大于 0");
        }
        JsonNode data = validatedData(rawJson);
        if (!data.isObject()) {
            throw new TjStatsSourceException("MATCH_DETAIL: data 必须是对象");
        }
        JsonNode matchIdNode = data.get("matchId");
        if (matchIdNode == null || !matchIdNode.isIntegralNumber()
                || matchIdNode.longValue() != expectedMatchId) {
            throw new TjStatsSourceException(
                    "MATCH_DETAIL: 返回比赛 ID 与请求不一致，期望 " + expectedMatchId);
        }
        JsonNode matchInfos = data.get("matchInfos");
        if (matchInfos == null || !matchInfos.isArray() || matchInfos.isEmpty()) {
            throw new TjStatsSourceException("MATCH_DETAIL: matchInfos 必须是非空数组，matchId=" + expectedMatchId);
        }

        List<MatchPlayerMetricSourceRecord> records = new java.util.ArrayList<>();
        Set<String> seenPlayers = new HashSet<>();
        Set<Long> seenBos = new HashSet<>();
        for (int gameIndex = 0; gameIndex < matchInfos.size(); gameIndex++) {
            JsonNode game = matchInfos.get(gameIndex);
            JsonNode boNode = game.get("bo");
            if (boNode == null || !boNode.isIntegralNumber() || boNode.longValue() <= 0) {
                throw new TjStatsSourceException("MATCH_DETAIL[" + gameIndex + "]: bo 必须大于 0");
            }
            long bo = boNode.longValue();
            if (!seenBos.add(bo)) {
                throw new TjStatsSourceException("MATCH_DETAIL: bo 重复，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            JsonNode teamInfos = game.get("teamInfos");
            if (teamInfos == null || !teamInfos.isArray() || teamInfos.size() != 2) {
                throw new TjStatsSourceException(
                        "MATCH_DETAIL: 每局必须包含两支战队，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            if (isOfficialEmptyGamePlaceholder(teamInfos)) {
                continue;
            }

            for (JsonNode team : teamInfos) {
                JsonNode teamIdNode = team.get("teamId");
                if (teamIdNode == null || !teamIdNode.isIntegralNumber() || teamIdNode.longValue() <= 0) {
                    throw new TjStatsSourceException("MATCH_DETAIL: teamId 必须大于 0");
                }
                long teamId = teamIdNode.longValue();
                long teamKills = requireNonNegativeIntegral(team, "kills", expectedMatchId, bo, teamId);
                BigDecimal teamGold = requireNonNegativeDecimal(
                        team, "golds", "MATCH_DETAIL[" + gameIndex + "]");
                JsonNode playerInfos = team.get("playerInfos");
                if (playerInfos == null || !playerInfos.isArray() || playerInfos.size() != 5) {
                    throw new TjStatsSourceException(
                            "MATCH_DETAIL: 每支战队每局必须包含 5 名选手，matchId=" + expectedMatchId + "，bo=" + bo);
                }

                BigDecimal teamDamage = BigDecimal.ZERO;
                List<JsonNode> validPlayers = new java.util.ArrayList<>();
                for (JsonNode player : playerInfos) {
                    JsonNode playerIdNode = player.get("playerId");
                    if (playerIdNode == null || !playerIdNode.isIntegralNumber() || playerIdNode.longValue() <= 0) {
                        throw new TjStatsSourceException("MATCH_DETAIL: playerId 必须大于 0");
                    }
                    JsonNode damageDetail = player.get("damageDetail");
                    if (damageDetail == null || !damageDetail.isObject()) {
                        throw new TjStatsSourceException("MATCH_DETAIL: damageDetail 必须是对象");
                    }
                    BigDecimal heroDamage = requireNonNegativeDecimal(
                            damageDetail, "heroDamage", "MATCH_DETAIL[" + gameIndex + "]");
                    teamDamage = teamDamage.add(heroDamage);
                    validPlayers.add(player);
                }

                for (JsonNode player : validPlayers) {
                    long playerId = player.get("playerId").longValue();
                    if (!seenPlayers.add(bo + ":" + playerId)) {
                        throw new TjStatsSourceException(
                                "MATCH_DETAIL: 同一比赛选手重复，matchId=" + expectedMatchId + "，playerId=" + playerId);
                    }
                    JsonNode battleDetail = player.get("battleDetail");
                    JsonNode damageDetail = player.get("damageDetail");
                    JsonNode otherDetail = player.get("otherDetail");
                    if (battleDetail == null || !battleDetail.isObject()
                            || otherDetail == null || !otherDetail.isObject()) {
                        throw new TjStatsSourceException("MATCH_DETAIL: 选手指标对象不完整");
                    }
                    long kills = requireNonNegativeIntegral(
                            battleDetail, "kills", expectedMatchId, bo, playerId);
                    long deaths = requireNonNegativeIntegral(
                            battleDetail, "death", expectedMatchId, bo, playerId);
                    long assists = requireNonNegativeIntegral(
                            battleDetail, "assist", expectedMatchId, bo, playerId);
                    BigDecimal heroDamage = requireNonNegativeDecimal(
                            damageDetail, "heroDamage", "MATCH_DETAIL[" + gameIndex + "]");
                    BigDecimal gold = requireNonNegativeDecimal(
                            otherDetail, "golds", "MATCH_DETAIL[" + gameIndex + "]");
                    BigDecimal killParticipant = teamKills > 0
                            ? BigDecimal.valueOf(kills + assists)
                            .divide(BigDecimal.valueOf(teamKills), MathContext.DECIMAL128)
                            : null;
                    BigDecimal damagePercent = teamDamage.signum() > 0
                            ? heroDamage.divide(teamDamage, MathContext.DECIMAL128)
                            : null;
                    BigDecimal goldPercent = teamGold.signum() > 0
                            ? gold.divide(teamGold, MathContext.DECIMAL128)
                            : null;
                    records.add(new MatchPlayerMetricSourceRecord(
                            expectedMatchId, bo, playerId, teamId, kills, assists, deaths,
                            teamKills, heroDamage, teamDamage, gold, teamGold,
                            killParticipant, damagePercent, goldPercent
                    ));
                }
            }
        }
        return List.copyOf(records);
    }

    /**
     * 解析单局详情中可由战队维度准确累加的指标。此方法刻意独立于选手聚合：
     * 当历史响应缺少这些扩展字段时，调用方可以保留已验证的 KDA/输出数据，而不把未知值写成 0。
     */
    public List<MatchTeamMetricSourceRecord> parseMatchTeamMetrics(String rawJson, long expectedMatchId) {
        // 先复用既有的严格十人详情校验，避免扩展指标绕过人员完整性校验。
        parseMatchPlayerMetrics(rawJson, expectedMatchId);
        JsonNode data = validatedData(rawJson);
        JsonNode matchInfos = data.get("matchInfos");
        List<MatchTeamMetricSourceRecord> records = new java.util.ArrayList<>();
        Set<String> seenTeams = new HashSet<>();
        for (int gameIndex = 0; gameIndex < matchInfos.size(); gameIndex++) {
            JsonNode game = matchInfos.get(gameIndex);
            long bo = game.path("bo").longValue();
            JsonNode teams = game.get("teamInfos");
            if (isOfficialEmptyGamePlaceholder(teams)) {
                continue;
            }
            JsonNode duration = game.get("gameTime");
            if (duration == null || !duration.isIntegralNumber() || duration.longValue() <= 0) {
                throw new TjStatsSourceException("MATCH_DETAIL: gameTime 必须是大于 0 的秒数，matchId=" + expectedMatchId + "，bo=" + bo);
            }
            for (JsonNode team : teams) {
                long teamId = team.path("teamId").longValue();
                String key = bo + ":" + teamId;
                if (teamId <= 0 || !seenTeams.add(key)) {
                    throw new TjStatsSourceException("MATCH_DETAIL: 战队记录无效或重复，matchId=" + expectedMatchId + "，key=" + key);
                }
                BigDecimal gold = requireNonNegativeDecimal(team, "golds", "MATCH_DETAIL[" + gameIndex + "]");
                long dragons = requireNonNegativeIntegral(team, "dragonAmount", expectedMatchId, bo, teamId);
                long barons = requireNonNegativeIntegral(team, "baronAmount", expectedMatchId, bo, teamId);
                long turrets = requireNonNegativeIntegral(team, "turretAmount", expectedMatchId, bo, teamId);
                long assists = 0;
                long wardsPlaced = 0;
                long wardsKilled = 0;
                long minionKills = 0;
                BigDecimal damage = BigDecimal.ZERO;
                boolean firstBlood = false;
                JsonNode players = team.get("playerInfos");
                for (JsonNode player : players) {
                    long playerId = player.path("playerId").longValue();
                    JsonNode battle = player.get("battleDetail");
                    JsonNode vision = player.get("visionDetail");
                    JsonNode damageDetail = player.get("damageDetail");
                    JsonNode other = player.get("otherDetail");
                    if (vision == null || !vision.isObject() || other == null || !other.isObject()) {
                        throw new TjStatsSourceException("MATCH_DETAIL: 缺少视野或其他指标对象，matchId=" + expectedMatchId + "，playerId=" + playerId);
                    }
                    assists += requireNonNegativeIntegral(battle, "assist", expectedMatchId, bo, playerId);
                    wardsPlaced += requireNonNegativeIntegral(vision, "wardPlaced", expectedMatchId, bo, playerId);
                    wardsKilled += requireNonNegativeIntegral(vision, "wardKilled", expectedMatchId, bo, playerId);
                    minionKills += requireNonNegativeIntegral(player, "minionKilled", expectedMatchId, bo, playerId);
                    damage = damage.add(requireNonNegativeDecimal(damageDetail, "heroDamage", "MATCH_DETAIL[" + gameIndex + "]"));
                    firstBlood |= other.path("firstBlood").asBoolean(false) || other.path("firstBloodAssists").asBoolean(false);
                }
                records.add(new MatchTeamMetricSourceRecord(expectedMatchId, bo, teamId, duration.longValue(), assists,
                        damage, gold, wardsPlaced, wardsKilled, minionKills, dragons, barons, turrets, firstBlood));
            }
        }
        return List.copyOf(records);
    }

    private static boolean isOfficialEmptyGamePlaceholder(JsonNode teamInfos) {
        for (JsonNode team : teamInfos) {
            JsonNode teamId = team.get("teamId");
            JsonNode playerInfos = team.get("playerInfos");
            if (teamId == null || !teamId.isIntegralNumber() || teamId.longValue() != 0
                    || playerInfos == null || !playerInfos.isArray() || !playerInfos.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static long requireNonNegativeIntegral(JsonNode parent,
                                                   String field,
                                                   long matchId,
                                                   long bo,
                                                   long playerId) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || value.longValue() < 0) {
            throw new TjStatsSourceException(
                    "MATCH_DETAIL: " + field + " 必须是非负整数，matchId=" + matchId
                            + "，bo=" + bo + "，playerId=" + playerId);
        }
        return value.longValue();
    }

    private static BigDecimal requireNonNegativeDecimal(JsonNode parent, String field, String prefix) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull() || !value.isNumber() || value.decimalValue().signum() < 0) {
            throw new TjStatsSourceException(prefix + ": " + field + " 必须是非负数字");
        }
        return value.decimalValue();
    }

    private static String normalizeMatchPosition(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "JUG" -> "JUN";
            case "AD" -> "BOT";
            default -> normalized;
        };
        if (!Set.of("TOP", "JUN", "MID", "BOT", "SUP").contains(normalized)) {
            throw new TjStatsSourceException("MATCH_DETAIL: 未知选手分路：" + value);
        }
        return normalized;
    }

    private JsonNode validatedData(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new TjStatsSourceException("赛事官网接口返回了空响应");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isObject()) {
                throw new TjStatsSourceException("赛事官网接口响应根节点必须是对象");
            }
            if (root.has("success") && !root.path("success").asBoolean()) {
                throw new TjStatsSourceException("赛事官网接口返回失败：" + root.path("message").asText("未知错误"));
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new TjStatsSourceException("赛事官网接口响应缺少 data 字段");
            }
            return data;
        } catch (JsonProcessingException exception) {
            throw new TjStatsSourceException("赛事官网接口返回了无效 JSON", exception);
        }
    }

    // ── HERO 业务校验 ──────────────────────────────────────────────────────

    private void validateHeroes(List<HeroStatSourceRecord> heroes, long boCount) {
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < heroes.size(); i++) {
            HeroStatSourceRecord h = heroes.get(i);
            String prefix = "HERO[" + i + "]";

            // heroId > 0
            if (h.heroId() <= 0) {
                throw new TjStatsSourceException(prefix + ": heroId 必须大于 0，实际值: " + h.heroId());
            }
            // heroId 不重复
            if (!seenIds.add(h.heroId())) {
                throw new TjStatsSourceException(prefix + ": heroId 重复: " + h.heroId());
            }
            // heroName/heroCnName 至少一个非空
            if (isBlank(h.heroName()) && isBlank(h.heroCnName())) {
                throw new TjStatsSourceException(prefix + ": heroName 和 heroCnName 不能同时为空");
            }
            // 非负计数
            requireNonNegative(prefix, "pickCount", h.pickCount());
            requireNonNegative(prefix, "banCount", h.banCount());
            requireNonNegative(prefix, "bpCount", h.bpCount());
            requireNonNegative(prefix, "winningCount", h.winningCount());
            requireNonNegative(prefix, "totalKills", h.totalKills());
            requireNonNegative(prefix, "totalAssists", h.totalAssists());
            requireNonNegative(prefix, "totalDeath", h.totalDeath());

            // winningCount <= pickCount
            if (h.winningCount() > h.pickCount()) {
                throw new TjStatsSourceException(
                        prefix + ": winningCount(" + h.winningCount() + ") 不能超过 pickCount(" + h.pickCount() + ")");
            }
            // bpCount == pickCount + banCount
            if (h.bpCount() != h.pickCount() + h.banCount()) {
                throw new TjStatsSourceException(
                        prefix + ": bpCount(" + h.bpCount() + ") 必须等于 pickCount(" + h.pickCount()
                                + ") + banCount(" + h.banCount() + ")");
            }
            // pick/ban/bp 不超过 boCount
            if (h.pickCount() > boCount) {
                throw new TjStatsSourceException(
                        prefix + ": pickCount(" + h.pickCount() + ") 不能超过 boCount(" + boCount + ")");
            }
            if (h.banCount() > boCount) {
                throw new TjStatsSourceException(
                        prefix + ": banCount(" + h.banCount() + ") 不能超过 boCount(" + boCount + ")");
            }
            if (h.bpCount() > boCount) {
                throw new TjStatsSourceException(
                        prefix + ": bpCount(" + h.bpCount() + ") 不能超过 boCount(" + boCount + ")");
            }

            // 百分比校验 (0..1)
            requireRate01(prefix, "winningRate", h.winningRate());
            requireRate01(prefix, "pickRate", h.pickRate());
            requireRate01(prefix, "banRate", h.banRate());
            requireRate01(prefix, "bPRate", h.bPRate());

            // 场均非负
            requireNonNegativeDecimal(prefix, "kDA", h.kDA());
            requireNonNegativeDecimal(prefix, "killPerGame", h.killPerGame());
            requireNonNegativeDecimal(prefix, "deathPerGame", h.deathPerGame());
            requireNonNegativeDecimal(prefix, "assistPerGame", h.assistPerGame());
        }
    }

    // ── TEAM 业务校验 ──────────────────────────────────────────────────────

    private void validateTeams(List<TeamStatSourceRecord> teams) {
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < teams.size(); i++) {
            TeamStatSourceRecord t = teams.get(i);
            String prefix = "TEAM[" + i + "]";

            if (t.teamId() <= 0) {
                throw new TjStatsSourceException(prefix + ": teamId 必须大于 0，实际值: " + t.teamId());
            }
            if (!seenIds.add(t.teamId())) {
                throw new TjStatsSourceException(prefix + ": teamId 重复: " + t.teamId());
            }
            if (isBlank(t.teamName())) {
                throw new TjStatsSourceException(prefix + ": teamName 不能为空");
            }
            if (t.matchCount() <= 0) {
                throw new TjStatsSourceException(prefix + ": matchCount 必须大于 0，实际值: " + t.matchCount());
            }
            if (t.gameCount() <= 0) {
                throw new TjStatsSourceException(prefix + ": gameCount 必须大于 0，实际值: " + t.gameCount());
            }
            if (t.gameCount() < t.matchCount()) {
                throw new TjStatsSourceException(
                        prefix + ": gameCount(" + t.gameCount() + ") 不能小于 matchCount(" + t.matchCount() + ")");
            }
            if (t.matchWinCount() < 0 || t.matchWinCount() > t.matchCount()) {
                throw new TjStatsSourceException(
                        prefix + ": matchWinCount(" + t.matchWinCount() + ") 必须在 0..matchCount(" + t.matchCount() + ") 之间");
            }
            requireNonNegative(prefix, "totalKills", t.totalKills());
            requireNonNegative(prefix, "totalDeath", t.totalDeath());

            // 百分比校验
            requireRate01(prefix, "winningRate", t.winningRate());

            // 场均非负
            requireNonNegativeDecimal(prefix, "killPerGameTeam", t.killPerGameTeam());
            requireNonNegativeDecimal(prefix, "deathPerGameTeam", t.deathPerGameTeam());
            requireNonNegativeDecimal(prefix, "wardPlacedPerGameTeam", t.wardPlacedPerGameTeam());
            requireNonNegativeDecimal(prefix, "wardKilledPerGameTeam", t.wardKilledPerGameTeam());
            requireNonNegativeDecimal(prefix, "goldPerGameTeam", t.goldPerGameTeam());
            requireNonNegativeDecimal(prefix, "baronKillPerGameTeam", t.baronKillPerGameTeam());
            requireNonNegativeDecimal(prefix, "drakeKillPerGameTeam", t.drakeKillPerGameTeam());
        }
    }

    // ── PLAYER 业务校验 ────────────────────────────────────────────────────

    private void validatePlayers(List<PlayerStatSourceRecord> players) {
        Set<String> seenIdentities = new HashSet<>();
        for (int i = 0; i < players.size(); i++) {
            PlayerStatSourceRecord p = players.get(i);
            String prefix = "PLAYER[" + i + "]";

            // playerId 若存在必须 > 0
            if (p.playerId() != null && p.playerId() <= 0) {
                throw new TjStatsSourceException(prefix + ": playerId 若存在必须大于 0，实际值: " + p.playerId());
            }
            // playerName 非空
            if (isBlank(p.playerName())) {
                throw new TjStatsSourceException(prefix + ": playerName 不能为空");
            }
            // 历史数据偶尔把系列赛数记为 0，但局数仍为正；两者同时为 0 的记录已在前面过滤。
            if (p.matchCount() < 0) {
                throw new TjStatsSourceException(prefix + ": matchCount 不得小于 0，实际值: " + p.matchCount());
            }
            if (p.boCount() <= 0) {
                throw new TjStatsSourceException(prefix + ": boCount 必须大于 0，实际值: " + p.boCount());
            }
            if (p.boCount() < p.matchCount()) {
                throw new TjStatsSourceException(
                        prefix + ": boCount(" + p.boCount() + ") 不能小于 matchCount(" + p.matchCount() + ")");
            }
            // 非负计数
            requireNonNegative(prefix, "mvpCount", p.mvpCount());
            requireNonNegativeDecimal(prefix, "mvpVotes", p.mvpVotes());
            requireNonNegative(prefix, "totalKills", p.totalKills());
            requireNonNegative(prefix, "totalAssists", p.totalAssists());
            requireNonNegative(prefix, "totalDeath", p.totalDeath());

            // mvpCount 允许大于 matchCount（官网数据可能跨赛事累计）
            // playerLocation 校验
            if (p.playerLocation() != null && !p.playerLocation().isBlank()) {
                String loc = p.playerLocation().trim().toUpperCase(Locale.ROOT);
                if (!VALID_POSITIONS.contains(loc)) {
                    throw new TjStatsSourceException(
                            prefix + ": playerLocation 必须属于 TOP/JUG/MID/AD/SUP，实际值: " + p.playerLocation());
                }
            }
            // 身份不重复
            String identity = resolvePlayerIdentity(p.playerId(), p.playerName(), prefix);
            if (!seenIdentities.add(identity)) {
                throw new TjStatsSourceException(prefix + ": 选手身份重复: " + identity);
            }

            // 百分比校验 (0..1)
            requireRate01(prefix, "killParticipantPercent", p.killParticipantPercent());
            requireRate01(prefix, "damagePercent", p.damagePercent());
            requireRate01(prefix, "goldPercent", p.goldPercent());

            // 场均非负（goldGapPerGame 允许负数）
            requireNonNegativeDecimal(prefix, "kda", p.kda());
            requireNonNegativeDecimal(prefix, "goldPerGame", p.goldPerGame());
            requireNonNegativeDecimal(prefix, "creepScorePerGame", p.creepScorePerGame());
            requireNonNegativeDecimal(prefix, "wardPlacedPerGame", p.wardPlacedPerGame());
            requireNonNegativeDecimal(prefix, "wardKilledPerGame", p.wardKilledPerGame());
            // goldGapPerGame 允许负数，不做校验
        }
    }

    private String resolvePlayerIdentity(Long playerId, String playerName, String prefix) {
        if (playerId != null && playerId > 0) {
            return "id:" + playerId;
        }
        if (playerName == null || playerName.isBlank()) {
            throw new TjStatsSourceException(prefix + ": 选手名称不能为空且 playerId 无效");
        }
        return "name:" + playerName.trim().toLowerCase(Locale.ROOT);
    }

    // ── 校验工具方法 ───────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void requireIntegralFields(JsonNode records, String type, String... fields) {
        for (int index = 0; index < records.size(); index++) {
            JsonNode record = records.get(index);
            if (!record.isObject()) {
                throw new TjStatsSourceException(type + "[" + index + "]: 记录必须是对象");
            }
            for (String field : fields) {
                JsonNode value = record.get(field);
                if (value == null || value.isNull() || !value.isIntegralNumber()) {
                    throw new TjStatsSourceException(
                            type + "[" + index + "]: 缺少整数类型关键字段 " + field);
                }
            }
        }
    }

    private static void requireNumericFields(JsonNode records, String type, String... fields) {
        for (int index = 0; index < records.size(); index++) {
            JsonNode record = records.get(index);
            if (!record.isObject()) {
                throw new TjStatsSourceException(type + "[" + index + "]: 记录必须是对象");
            }
            for (String field : fields) {
                JsonNode value = record.get(field);
                if (value == null || value.isNull() || !value.isNumber()) {
                    throw new TjStatsSourceException(
                            type + "[" + index + "]: 缺少数值类型关键字段 " + field);
                }
            }
        }
    }

    private static void requireNonNegative(String prefix, String field, long value) {
        if (value < 0) {
            throw new TjStatsSourceException(prefix + ": " + field + " 不能为负数，实际值: " + value);
        }
    }

    private static void requireNonNegativeDecimal(String prefix, String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new TjStatsSourceException(prefix + ": " + field + " 不能为负数，实际值: " + value);
        }
    }

    private static void requireRate01(String prefix, String field, BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new TjStatsSourceException(prefix + ": " + field + " 必须在 0..1 之间，实际值: " + value);
        }
    }
}
