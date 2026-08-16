package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.EloRatingQuery;
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

/**
 * 战队 Elo 评分服务：无状态重放所选赛段的全部小局。
 * 标准 Elo：起始 1500，K=32，胜方得分期望差即转移量；平局按 0.5 处理。
 */
@Service
public class EloRatingService {
    private static final Logger log = LoggerFactory.getLogger(EloRatingService.class);
    private static final TypeReference<EloRatingResult> CACHE_TYPE = new TypeReference<>() { };
    private static final double INITIAL_RATING = 1500.0;
    private static final double K_FACTOR = 32.0;

    private final MatchGameMapper matchGameMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public EloRatingService(MatchGameMapper matchGameMapper,
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

    public EloRatingResult query(EloRatingQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s13:v" + dataVersion + ":elo:" + query.cacheFingerprint();
        EloRatingResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<MatchTeamGameRow> games = matchGameMapper.findAllGames(query.stages());
        Map<Long, TeamState> teams = new LinkedHashMap<>();
        for (MatchTeamGameRow game : games) {
            TeamState teamA = teams.computeIfAbsent(game.teamAId(),
                    id -> new TeamState(id, game.teamAName(), game.teamALogo()));
            TeamState teamB = teams.computeIfAbsent(game.teamBId(),
                    id -> new TeamState(id, game.teamBName(), game.teamBLogo()));
            double expectedA = 1.0 / (1.0 + Math.pow(10.0, (teamB.rating - teamA.rating) / 400.0));
            double scoreA;
            if (game.winnerTeamId() == game.teamAId()) {
                scoreA = 1.0;
            } else if (game.winnerTeamId() == game.teamBId()) {
                scoreA = 0.0;
            } else {
                scoreA = 0.5;
            }
            double delta = K_FACTOR * (scoreA - expectedA);
            teamA.apply(delta, scoreA == 1.0);
            teamB.apply(-delta, scoreA == 0.0);
        }

        Comparator<EloRatingResult.TeamRating> ordering = Comparator
                .comparingInt(EloRatingResult.TeamRating::rating).reversed()
                .thenComparingLong(EloRatingResult.TeamRating::games)
                .thenComparingLong(EloRatingResult.TeamRating::teamId);
        List<EloRatingResult.TeamRating> sorted = teams.values().stream()
                .map(state -> new EloRatingResult.TeamRating(
                        state.teamId, state.name, state.logo,
                        (int) Math.round(state.rating), 0,
                        state.games, state.wins, state.games - state.wins,
                        List.copyOf(state.history)))
                .sorted(ordering)
                .toList();
        List<EloRatingResult.TeamRating> ranked = new ArrayList<>(sorted.size());
        int previousRating = Integer.MIN_VALUE;
        int previousRank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            EloRatingResult.TeamRating row = sorted.get(i);
            int rank = row.rating() == previousRating ? previousRank : i + 1;
            ranked.add(new EloRatingResult.TeamRating(row.teamId(), row.teamName(), row.teamLogo(),
                    row.rating(), rank, row.games(), row.wins(), row.losses(), row.ratingHistory()));
            previousRating = row.rating();
            previousRank = rank;
        }

        EloRatingResult result = new EloRatingResult(games.size(), ranked);
        writeCache(cacheKey, result);
        return result;
    }

    private static final class TeamState {
        private final long teamId;
        private final String name;
        private final String logo;
        private double rating = INITIAL_RATING;
        private long games;
        private long wins;
        private final List<Double> history = new ArrayList<>();

        private TeamState(long teamId, String name, String logo) {
            this.teamId = teamId;
            this.name = name;
            this.logo = logo;
        }

        private void apply(double delta, boolean won) {
            rating += delta;
            games++;
            if (won) {
                wins++;
            }
            history.add(Math.round(rating * 10.0) / 10.0);
        }
    }

    private EloRatingResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取 Elo 评分 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, EloRatingResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入 Elo 评分 Redis 缓存失败，key={}", key, exception);
        }
    }
}
