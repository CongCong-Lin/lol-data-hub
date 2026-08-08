package com.loldatahub.infrastructure.mapper;

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
                   c.positions_json AS positionsJson,
                   (SELECT COALESCE(SUM(ss.sample_base_count), 0)
                      FROM stage_stat_current ss
                     WHERE ss.source_season_id = #{seasonId}
                       AND ss.source_stage_id IN
                       <foreach collection="stageIds" item="stageId" open="(" separator="," close=")">
                           #{stageId}
                       </foreach>) AS sampleBaseCount,
                   SUM(cs.pick_count) AS pickCount,
                   SUM(cs.ban_count) AS banCount,
                   SUM(cs.bp_count) AS bpCount,
                   SUM(cs.winning_count) AS winningCount,
                   SUM(cs.total_kills) AS totalKills,
                   SUM(cs.total_deaths) AS totalDeaths,
                   SUM(cs.total_assists) AS totalAssists,
                   MAX(ss.source_updated_at) AS sourceUpdatedAt
              FROM champion_stage_stat_current cs
              JOIN champion c ON c.source_champion_id = cs.source_champion_id
              JOIN stage_stat_current ss
                ON ss.source_season_id = cs.source_season_id AND ss.source_stage_id = cs.source_stage_id
             WHERE cs.source_season_id = #{seasonId}
               AND cs.source_stage_id IN
               <foreach collection="stageIds" item="stageId" open="(" separator="," close=")">
                   #{stageId}
               </foreach>
             GROUP BY c.source_champion_id, c.chinese_name, c.chinese_title, c.logo_url, c.positions_json
            HAVING SUM(cs.pick_count) &gt;= #{minimumPickCount}
            </script>
            """)
    List<ChampionAggregateRow> aggregateChampions(@Param("seasonId") long seasonId,
                                                   @Param("stageIds") List<Long> stageIds,
                                                   @Param("minimumPickCount") int minimumPickCount);

    @Select("""
            <script>
            SELECT source_stage_id
              FROM stage_stat_current
             WHERE source_season_id = #{seasonId}
               AND source_stage_id IN
               <foreach collection="stageIds" item="stageId" open="(" separator="," close=")">
                   #{stageId}
               </foreach>
            </script>
            """)
    List<Long> findCollectedStageIds(@Param("seasonId") long seasonId,
                                     @Param("stageIds") List<Long> stageIds);

}
