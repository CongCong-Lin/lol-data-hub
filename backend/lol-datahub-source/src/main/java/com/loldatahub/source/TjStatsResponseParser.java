package com.loldatahub.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import com.loldatahub.source.model.SeasonSourceRecord;
import com.loldatahub.source.model.SeasonStagesSourceRecord;
import com.loldatahub.source.model.StageSourceRecord;
import com.loldatahub.source.model.TeamStatSourceRecord;

import java.math.BigDecimal;
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
        requireIntegralFields(data, "PLAYER", "matchCount", "boCount", "mvpCount",
                "totalKills", "totalAssists", "totalDeath");
        requireNumericFields(data, "PLAYER", "mvpVotes");

        List<PlayerStatSourceRecord> players = objectMapper.convertValue(
                data,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PlayerStatSourceRecord.class)
        );

        validatePlayers(players);
        return players;
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
            // matchCount > 0
            if (p.matchCount() <= 0) {
                throw new TjStatsSourceException(prefix + ": matchCount 必须大于 0，实际值: " + p.matchCount());
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
