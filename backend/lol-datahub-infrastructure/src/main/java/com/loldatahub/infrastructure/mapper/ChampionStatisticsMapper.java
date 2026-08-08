package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.ChampionAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChampionStatisticsMapper {
    @Select("""
            <script>
            SELECT c.source_champion_id AS championId,
                   c.chinese_name AS championName,
                   c.chinese_title AS championTitle,
                   c.logo_url AS championLogo,
                   GROUP_CONCAT(DISTINCT CAST(cs.positions_json AS CHAR) ORDER BY cs.source_stage_id SEPARATOR '|')
                       AS positionsCsv,
                   GROUP_CONCAT(DISTINCT
                       CASE WHEN cs.most_used_player_name IS NOT NULL AND cs.most_used_player_name != ''
                            THEN cs.most_used_player_name END
                       ORDER BY cs.most_used_player_name SEPARATOR ',') AS mostUsedPlayersCsv,
                   (SELECT COALESCE(SUM(ss.sample_base_count), 0)
                      FROM stage_stat_current ss
                      JOIN (SELECT 1 AS _dummy) _init ON TRUE
                      WHERE (ss.source_season_id, ss.source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>) AS sampleBaseCount,
                   SUM(cs.pick_count) AS pickCount,
                   SUM(cs.ban_count) AS banCount,
                   SUM(cs.bp_count) AS bpCount,
                   SUM(cs.winning_count) AS winningCount,
                   SUM(cs.total_kills) AS totalKills,
                   SUM(cs.total_deaths) AS totalDeaths,
                   SUM(cs.total_assists) AS totalAssists,
                   MAX(ss_outer.source_updated_at) AS sourceUpdatedAt
              FROM champion_stage_stat_current cs
              JOIN champion c ON c.source_champion_id = cs.source_champion_id
              JOIN stage_stat_current ss_outer
                ON ss_outer.source_season_id = cs.source_season_id AND ss_outer.source_stage_id = cs.source_stage_id
             WHERE (cs.source_season_id, cs.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY c.source_champion_id, c.chinese_name, c.chinese_title, c.logo_url
            HAVING SUM(cs.pick_count) &gt;= #{minimumPickCount}
            </script>
            """)
    List<ChampionAggregateRow> aggregateChampions(@Param("stages") List<StageKey> stages,
                                                   @Param("minimumPickCount") int minimumPickCount);

    @Select("""
            <script>
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM stage_stat_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);
}
