package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamHeadToHeadQuery;
import com.loldatahub.infrastructure.mapper.MatchGameMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.MatchTeamGameRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 战队交锋记录服务：从对局明细聚合每场系列赛的分局胜负。
 */
@Service
public class TeamHeadToHeadStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(TeamHeadToHeadStatisticsService.class);
    private static final TypeReference<TeamHeadToHeadResult> CACHE_TYPE = new TypeReference<>() { };
    private static final int RECENT_MEETING_LIMIT = 10;

    private final MatchGameMapper matchGameMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public TeamHeadToHeadStatisticsService(MatchGameMapper matchGameMapper,
                                           SystemStateMapper systemStateMapper,
                                           StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper,
                                           @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.matchGameMapper = matchGameMapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public TeamHeadToHeadResult query(TeamHeadToHeadQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s11:v" + dataVersion + ":team-h2h:" + query.cacheFingerprint();
        TeamHeadToHeadResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<MatchTeamGameRow> games = matchGameMapper.findTeamGames(query.stages(), query.teamId());
        Map<Long, List<MatchTeamGameRow>> gamesByMatch = new TreeMap<>();
        for (MatchTeamGameRow game : games) {
            gamesByMatch.computeIfAbsent(game.sourceMatchId(), ignored -> new ArrayList<>()).add(game);
        }

        Map<Long, OpponentAccumulator> opponents = new LinkedHashMap<>();
        List<TeamHeadToHeadResult.Meeting> meetings = new ArrayList<>();
        for (Map.Entry<Long, List<MatchTeamGameRow>> entry : gamesByMatch.entrySet()) {
            List<MatchTeamGameRow> matchGames = entry.getValue();
            MatchTeamGameRow first = matchGames.get(0);
            long opponentId = first.teamAId() == query.teamId() ? first.teamBId() : first.teamAId();
            long teamGameWins = matchGames.stream().filter(g -> g.winnerTeamId() == query.teamId()).count();
            long opponentGameWins = matchGames.size() - teamGameWins;
            boolean won = teamGameWins > opponentGameWins;

            OpponentAccumulator accumulator = opponents.computeIfAbsent(
                    opponentId, ignored -> new OpponentAccumulator(
                            opponentName(first, query.teamId()), opponentLogo(first, query.teamId())));
            accumulator.matchCount++;
            if (won) {
                accumulator.matchWins++;
            } else if (opponentGameWins > teamGameWins) {
                accumulator.matchLosses++;
            }
            accumulator.gameCount += matchGames.size();
            accumulator.gameWins += teamGameWins;
            accumulator.gameLosses += opponentGameWins;

            meetings.add(new TeamHeadToHeadResult.Meeting(
                    entry.getKey(), opponentId, opponentName(first, query.teamId()),
                    opponentLogo(first, query.teamId()), first.startTime(),
                    teamGameWins, opponentGameWins, won));
        }

        List<TeamHeadToHeadResult.Opponent> opponentRows = opponents.entrySet().stream()
                .map(e -> new TeamHeadToHeadResult.Opponent(
                        e.getKey(), e.getValue().name, e.getValue().logo,
                        e.getValue().matchCount, e.getValue().matchWins, e.getValue().matchLosses,
                        e.getValue().gameCount, e.getValue().gameWins, e.getValue().gameLosses))
                .sorted(Comparator.comparingLong(TeamHeadToHeadResult.Opponent::gameCount).reversed()
                        .thenComparingLong(TeamHeadToHeadResult.Opponent::opponentTeamId))
                .toList();
        meetings.sort(Comparator.comparing(TeamHeadToHeadResult.Meeting::startTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<TeamHeadToHeadResult.Meeting> recentMeetings = meetings.stream()
                .limit(RECENT_MEETING_LIMIT)
                .toList();

        TeamHeadToHeadResult result =
                new TeamHeadToHeadResult(query.teamId(), opponentRows, recentMeetings);
        writeCache(cacheKey, result);
        return result;
    }

    private static String opponentName(MatchTeamGameRow row, long teamId) {
        return row.teamAId() == teamId ? row.teamBName() : row.teamAName();
    }

    private static String opponentLogo(MatchTeamGameRow row, long teamId) {
        return row.teamAId() == teamId ? row.teamBLogo() : row.teamALogo();
    }

    private static final class OpponentAccumulator {
        private long matchCount;
        private long matchWins;
        private long matchLosses;
        private long gameCount;
        private long gameWins;
        private long gameLosses;
        private final String name;
        private final String logo;

        private OpponentAccumulator(String name, String logo) {
            this.name = name;
            this.logo = logo;
        }
    }

    private TeamHeadToHeadResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取战队交锋 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, TeamHeadToHeadResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入战队交锋 Redis 缓存失败，key={}", key, exception);
        }
    }
}
