package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.MatchGameDetailResult;
import com.loldatahub.domain.statistics.MatchGameNotFoundException;
import com.loldatahub.domain.statistics.MatchGamePlayerRecord;
import com.loldatahub.domain.statistics.MatchGameQuery;
import com.loldatahub.domain.statistics.MatchGameRecord;
import com.loldatahub.domain.statistics.MatchGamesResult;
import com.loldatahub.domain.statistics.PlayerGameRecord;
import com.loldatahub.domain.statistics.PlayerGamesResult;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.infrastructure.mapper.MatchGameMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.MatchGamePlayerRow;
import com.loldatahub.infrastructure.model.MatchGameRow;
import com.loldatahub.infrastructure.model.PlayerGameRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对局明细查询：对局赛果列表、单场比赛详情、选手单局战绩。
 * 数据来自 match_game_current 回填表，更新频率低于聚合统计，沿用 Redis 缓存模式。
 */
@Service
public class MatchGameStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(MatchGameStatisticsService.class);
    private static final int MAX_PLAYER_GAME_LIMIT = 200;

    private final MatchGameMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public MatchGameStatisticsService(
            MatchGameMapper mapper,
            SystemStateMapper systemStateMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.mapper = mapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public MatchGamesResult queryMatches(MatchGameQuery query) {
        requireCollected(query.stages());
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s6:v" + dataVersion + ":match-games:" + query.cacheFingerprint();
        MatchGamesResult cached = readCache(cacheKey, new TypeReference<>() { });
        if (cached != null) {
            return cached;
        }
        long total = mapper.countMatchGames(query.stages());
        List<MatchGameRecord> items = mapper.aggregateMatchGames(
                        query.stages(), orderBy(query), query.offset(), query.limit())
                .stream().map(MatchGameStatisticsService::toGameRecord).toList();
        MatchGamesResult result = new MatchGamesResult(dataVersion, total, query.offset(), query.limit(), items);
        writeCache(cacheKey, result);
        return result;
    }

    public MatchGameDetailResult queryMatchDetail(List<StageKey> stages, long matchId) {
        requireCollected(stages);
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s6:v" + dataVersion + ":match-detail:" + stagesKey(stages) + ":" + matchId;
        MatchGameDetailResult cached = readCache(cacheKey, new TypeReference<>() { });
        if (cached != null) {
            return cached;
        }
        List<MatchGameRecord> games = mapper.findMatchGamesByMatchId(stages, matchId)
                .stream().map(MatchGameStatisticsService::toGameRecord).toList();
        if (games.isEmpty()) {
            throw new MatchGameNotFoundException(matchId);
        }
        List<MatchGamePlayerRecord> players = mapper.findMatchGamePlayers(stages, matchId)
                .stream().map(this::toPlayerRecord).toList();
        MatchGameDetailResult result = new MatchGameDetailResult(dataVersion, matchId, games, players);
        writeCache(cacheKey, result);
        return result;
    }

    public PlayerGamesResult queryPlayerGames(List<StageKey> stages, long playerId, int limit) {
        if (limit < 1 || limit > MAX_PLAYER_GAME_LIMIT) {
            throw new IllegalArgumentException("单局战绩条数必须是 1 到 " + MAX_PLAYER_GAME_LIMIT + " 之间的整数");
        }
        requireCollected(stages);
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s6:v" + dataVersion + ":player-games:" + stagesKey(stages)
                + ":" + playerId + ":" + limit;
        PlayerGamesResult cached = readCache(cacheKey, new TypeReference<>() { });
        if (cached != null) {
            return cached;
        }
        List<PlayerGameRecord> items = mapper.findPlayerGames(stages, playerId, limit)
                .stream().map(this::toPlayerGameRecord).toList();
        String rawName = mapper.findPlayerName(playerId);
        String playerName = rawName == null || rawName.isBlank() ? "选手 #" + playerId : rawName;
        PlayerGamesResult result = new PlayerGamesResult(dataVersion, playerId, playerName, items);
        writeCache(cacheKey, result);
        return result;
    }

    private void requireCollected(List<StageKey> stages) {
        var collected = new HashSet<>(mapper.findCollectedStageKeys(stages));
        List<StageKey> missing = stages.stream().filter(stage -> !collected.contains(stage)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下赛段尚未采集对局明细数据：" + missing.stream()
                    .map(StageKey::canonical).collect(Collectors.joining(", ")));
        }
    }

    /** 排序字段白名单由领域校验保证，这里只负责拼装固定 SQL 片段，不接受用户输入。 */
    private String orderBy(MatchGameQuery query) {
        String direction = query.sortDirection().name();
        return switch (query.sortBy()) {
            case "startTime" -> "g.start_time " + direction + ", g.source_match_id " + direction
                    + ", g.game_number " + direction;
            case "matchId" -> "g.source_match_id " + direction + ", g.game_number " + direction;
            default -> throw new IllegalStateException("未实现的对局排序字段：" + query.sortBy());
        };
    }

    static MatchGameRecord toGameRecord(MatchGameRow row) {
        return new MatchGameRecord(
                row.sourceSeasonId(), row.sourceStageId(), row.sourceMatchId(), row.gameNumber(),
                row.startTime(), row.teamAId(), row.teamAName(), row.teamALogo(),
                row.teamAKills(), row.teamAAssists(), row.teamADamage(), row.teamAGold(),
                row.teamAWardsPlaced(), row.teamAWardsKilled(), row.teamAMinionKills(),
                row.teamADragons(), row.teamABarons(), row.teamATurrets(), row.teamAFirstBlood(),
                row.teamBId(), row.teamBName(), row.teamBLogo(),
                row.teamBKills(), row.teamBAssists(), row.teamBDamage(), row.teamBGold(),
                row.teamBWardsPlaced(), row.teamBWardsKilled(), row.teamBMinionKills(),
                row.teamBDragons(), row.teamBBarons(), row.teamBTurrets(), row.teamBFirstBlood(),
                row.winnerTeamId(), row.gameDurationSeconds());
    }

    private MatchGamePlayerRecord toPlayerRecord(MatchGamePlayerRow row) {
        return new MatchGamePlayerRecord(
                row.sourceSeasonId(), row.sourceStageId(), row.sourceMatchId(), row.gameNumber(),
                row.startTime(), row.sourcePlayerId(), row.playerName(), row.sourceTeamId(),
                row.teamName(), row.sourceChampionId(), row.championName(),
                row.championChineseName(), row.championTitle(), row.championLogo(),
                row.position(), row.won(), row.kills(), row.deaths(), row.assists(),
                row.heroDamage(), row.playerGold(), row.teamKills(), row.teamDamage(),
                row.teamGold(), row.killParticipantPercent(), row.damagePercent(), row.goldPercent());
    }

    private PlayerGameRecord toPlayerGameRecord(PlayerGameRow row) {
        return new PlayerGameRecord(
                row.sourceSeasonId(), row.sourceStageId(), row.stageName(), row.sourceMatchId(),
                row.gameNumber(), row.startTime(), row.opponentTeamName(), row.sourceChampionId(),
                row.championName(), row.championChineseName(), row.championLogo(), row.position(),
                row.won(), row.kills(), row.deaths(), row.assists(),
                StatisticsMath.kda(row.kills(), row.assists(), row.deaths()),
                row.heroDamage(), row.killParticipantPercent(), row.damagePercent());
    }

    private static String stagesKey(List<StageKey> stages) {
        return stages.stream().map(StageKey::canonical).collect(Collectors.joining(","));
    }

    private <T> T readCache(String key, TypeReference<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取对局明细 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入对局明细 Redis 缓存失败，key={}", key, exception);
        }
    }
}
