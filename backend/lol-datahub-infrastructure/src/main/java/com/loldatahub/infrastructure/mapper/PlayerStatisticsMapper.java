package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.PlayerAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlayerStatisticsMapper {
    @Select("""
            <script>
            WITH selected_stats AS (
                SELECT ps.*
                  FROM player_stage_stat_current ps
                 WHERE (ps.source_season_id, ps.source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                   <if test="position != null">
                       AND UPPER(ps.player_position) = #{position}
                   </if>
            ),
            season_votes AS (
                SELECT source_season_id, player_key, MAX(mvp_votes) AS mvp_votes
                  FROM selected_stats
                 GROUP BY source_season_id, player_key
            ),
            player_votes AS (
                SELECT player_key, SUM(mvp_votes) AS mvp_votes
                  FROM season_votes
                 GROUP BY player_key
            )
            SELECT p.player_key AS playerKey,
                   p.source_player_id AS sourcePlayerId,
                   p.name AS playerName,
                   p.avatar_url AS avatarUrl,
                   GROUP_CONCAT(DISTINCT ps.team_name ORDER BY ps.team_name SEPARATOR ',') AS teamNamesCsv,
                   GROUP_CONCAT(DISTINCT ps.player_position ORDER BY ps.player_position SEPARATOR ',') AS positionsCsv,
                   SUM(ps.match_count) AS matchCount,
                   SUM(COALESCE(ps.game_count, ps.match_count)) AS gameCount,
                   SUM(ps.mvp_count) AS mvpCount,
                   COALESCE(pv.mvp_votes, 0) AS mvpVotes,
                   SUM(ps.total_kills) AS totalKills,
                   SUM(ps.total_assists) AS totalAssists,
                   SUM(ps.total_deaths) AS totalDeaths,
                   COALESCE(SUM(ps.source_gold_per_game * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_gold_per_game IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedGoldPerGame,
                   COALESCE(SUM(ps.source_creep_score_per_game * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_creep_score_per_game IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedCreepScorePerGame,
                   COALESCE(SUM(ps.source_ward_placed_per_game * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_ward_placed_per_game IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedWardPlacedPerGame,
                   COALESCE(SUM(ps.source_ward_killed_per_game * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_ward_killed_per_game IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedWardKilledPerGame,
                   COALESCE(SUM(ps.source_kill_participant_percent * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_kill_participant_percent IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedKillParticipantPercent,
                   COALESCE(SUM(ps.source_gold_gap_per_game * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_gold_gap_per_game IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedGoldGapPerGame,
                   COALESCE(SUM(ps.source_damage_percent * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_damage_percent IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedDamagePercent,
                   COALESCE(SUM(ps.source_gold_percent * COALESCE(ps.game_count, ps.match_count))
                       / NULLIF(SUM(CASE WHEN ps.source_gold_percent IS NOT NULL THEN COALESCE(ps.game_count, ps.match_count) ELSE 0 END), 0), 0)
                       AS weightedGoldPercent
              FROM selected_stats ps
              JOIN player p ON p.player_key = ps.player_key
              LEFT JOIN player_votes pv ON pv.player_key = ps.player_key
             GROUP BY p.player_key, p.source_player_id, p.name, p.avatar_url, pv.mvp_votes
            HAVING SUM(ps.match_count) >= #{minimumMatchCount}
            </script>
            """)
    List<PlayerAggregateRow> aggregatePlayers(@Param("stages") List<StageKey> stages,
                                              @Param("minimumMatchCount") int minimumMatchCount,
                                              @Param("position") String position);

    @Select("""
            <script>
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM player_stage_collection_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);

    @Select("""
            SELECT content_hash FROM player_stage_collection_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    String findCurrentContentHash(@Param("seasonId") long seasonId, @Param("stageId") long stageId);
}
