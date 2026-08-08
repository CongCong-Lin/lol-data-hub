package com.loldatahub.infrastructure.mapper;

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
                   SUM(ts.match_win_count) AS matchWinCount,
                   SUM(ts.total_kills) AS totalKills,
                   SUM(ts.total_deaths) AS totalDeaths,
                   COALESCE(SUM(ts.source_ward_placed_per_game * ts.match_count)
                       / NULLIF(SUM(CASE WHEN ts.source_ward_placed_per_game IS NOT NULL THEN ts.match_count ELSE 0 END), 0), 0)
                       AS weightedWardPlacedPerGame,
                   COALESCE(SUM(ts.source_ward_killed_per_game * ts.match_count)
                       / NULLIF(SUM(CASE WHEN ts.source_ward_killed_per_game IS NOT NULL THEN ts.match_count ELSE 0 END), 0), 0)
                       AS weightedWardKilledPerGame,
                   COALESCE(SUM(ts.source_gold_per_game * ts.match_count)
                       / NULLIF(SUM(CASE WHEN ts.source_gold_per_game IS NOT NULL THEN ts.match_count ELSE 0 END), 0), 0)
                       AS weightedGoldPerGame,
                   COALESCE(SUM(ts.source_baron_kill_per_game * ts.match_count)
                       / NULLIF(SUM(CASE WHEN ts.source_baron_kill_per_game IS NOT NULL THEN ts.match_count ELSE 0 END), 0), 0)
                       AS weightedBaronKillPerGame,
                   COALESCE(SUM(ts.source_drake_kill_per_game * ts.match_count)
                       / NULLIF(SUM(CASE WHEN ts.source_drake_kill_per_game IS NOT NULL THEN ts.match_count ELSE 0 END), 0), 0)
                       AS weightedDrakeKillPerGame
              FROM team_stage_stat_current ts
              JOIN team t ON t.source_team_id = ts.source_team_id
             WHERE ts.source_season_id = #{seasonId}
               AND ts.source_stage_id IN
               <foreach collection="stageIds" item="stageId" open="(" separator="," close=")">
                   #{stageId}
               </foreach>
             GROUP BY t.source_team_id, t.name, t.logo_url
            HAVING SUM(ts.match_count) >= #{minimumMatchCount}
            </script>
            """)
    List<TeamAggregateRow> aggregateTeams(@Param("seasonId") long seasonId,
                                          @Param("stageIds") List<Long> stageIds,
                                          @Param("minimumMatchCount") int minimumMatchCount);

    @Select("""
            <script>
            SELECT source_stage_id
              FROM team_stage_collection_current
             WHERE source_season_id = #{seasonId}
               AND source_stage_id IN
               <foreach collection="stageIds" item="stageId" open="(" separator="," close=")">
                   #{stageId}
               </foreach>
            </script>
            """)
    List<Long> findCollectedStageIds(@Param("seasonId") long seasonId,
                                     @Param("stageIds") List<Long> stageIds);

    @Select("""
            SELECT content_hash FROM team_stage_collection_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    String findCurrentContentHash(@Param("seasonId") long seasonId, @Param("stageId") long stageId);
}
