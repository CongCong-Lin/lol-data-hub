package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.TeamAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeamStatisticsMapper {
    @Select("""
            <script>
            SELECT t.source_team_id AS teamId,
                   t.name AS teamName,
                   t.logo_url AS teamLogo,
                   SUM(ts.match_count) AS matchCount,
                   SUM(COALESCE(ts.game_count, ts.match_count)) AS gameCount,
                   SUM(ts.match_win_count) AS matchWinCount,
                   SUM(ts.total_kills) AS totalKills,
                   SUM(ts.total_deaths) AS totalDeaths,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_assists) ELSE NULL END AS totalAssists,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_damage) ELSE NULL END AS totalDamage,
                   COALESCE(SUM(ts.source_ward_placed_per_game * COALESCE(ts.game_count, ts.match_count))
                       / NULLIF(SUM(CASE WHEN ts.source_ward_placed_per_game IS NOT NULL THEN COALESCE(ts.game_count, ts.match_count) ELSE 0 END), 0), 0)
                       AS weightedWardPlacedPerGame,
                   COALESCE(SUM(ts.source_ward_killed_per_game * COALESCE(ts.game_count, ts.match_count))
                       / NULLIF(SUM(CASE WHEN ts.source_ward_killed_per_game IS NOT NULL THEN COALESCE(ts.game_count, ts.match_count) ELSE 0 END), 0), 0)
                       AS weightedWardKilledPerGame,
                   COALESCE(SUM(ts.source_gold_per_game * COALESCE(ts.game_count, ts.match_count))
                       / NULLIF(SUM(CASE WHEN ts.source_gold_per_game IS NOT NULL THEN COALESCE(ts.game_count, ts.match_count) ELSE 0 END), 0), 0)
                       AS weightedGoldPerGame,
                   COALESCE(SUM(ts.source_baron_kill_per_game * COALESCE(ts.game_count, ts.match_count))
                       / NULLIF(SUM(CASE WHEN ts.source_baron_kill_per_game IS NOT NULL THEN COALESCE(ts.game_count, ts.match_count) ELSE 0 END), 0), 0)
                       AS weightedBaronKillPerGame,
                   COALESCE(SUM(ts.source_drake_kill_per_game * COALESCE(ts.game_count, ts.match_count))
                       / NULLIF(SUM(CASE WHEN ts.source_drake_kill_per_game IS NOT NULL THEN COALESCE(ts.game_count, ts.match_count) ELSE 0 END), 0), 0)
                       AS weightedDrakeKillPerGame
              FROM team_stage_stat_current ts
              JOIN team t ON t.source_team_id = ts.source_team_id
              LEFT JOIN team_stage_detail_metric_current tdm
                ON tdm.source_season_id = ts.source_season_id
               AND tdm.source_stage_id = ts.source_stage_id
               AND tdm.source_team_id = ts.source_team_id
             WHERE (ts.source_season_id, ts.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY t.source_team_id, t.name, t.logo_url
            HAVING SUM(ts.match_count) >= #{minimumMatchCount}
            </script>
            """)
    List<TeamAggregateRow> aggregateTeams(@Param("stages") List<StageKey> stages,
                                          @Param("minimumMatchCount") int minimumMatchCount);

    @Select("""
            <script>
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM team_stage_collection_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);

    @Select("""
            SELECT content_hash FROM team_stage_collection_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    String findCurrentContentHash(@Param("seasonId") long seasonId, @Param("stageId") long stageId);
}
