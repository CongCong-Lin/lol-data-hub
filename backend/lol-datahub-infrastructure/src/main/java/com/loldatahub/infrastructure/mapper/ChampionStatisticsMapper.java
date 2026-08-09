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
            WITH official_totals AS (
                SELECT cs.source_champion_id,
                       SUM(cs.ban_count) AS ban_count,
                       MAX(ss.source_updated_at) AS source_updated_at
                  FROM champion_stage_stat_current cs
                  JOIN stage_stat_current ss
                    ON ss.source_season_id = cs.source_season_id
                   AND ss.source_stage_id = cs.source_stage_id
                 WHERE (cs.source_season_id, cs.source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                 GROUP BY cs.source_champion_id
            ),
            detail_rows AS (
                SELECT ps.source_champion_id, ps.position, ps.source_player_id, ps.player_name,
                       ps.pick_count, ps.winning_count, ps.total_kills, ps.total_deaths, ps.total_assists
                  FROM champion_position_player_stage_stat_current ps
                 WHERE (ps.source_season_id, ps.source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                 <if test="position != null">
                   AND ps.position = #{position}
                 </if>
            ),
            detail_totals AS (
                SELECT source_champion_id,
                       GROUP_CONCAT(DISTINCT position
                           ORDER BY FIELD(position, 'TOP', 'JUN', 'MID', 'BOT', 'SUP') SEPARATOR ',')
                           AS positions_csv,
                       SUM(pick_count) AS pick_count,
                       SUM(winning_count) AS winning_count,
                       SUM(total_kills) AS total_kills,
                       SUM(total_deaths) AS total_deaths,
                       SUM(total_assists) AS total_assists
                  FROM detail_rows
                 GROUP BY source_champion_id
            ),
            player_totals AS (
                SELECT source_champion_id, source_player_id, MAX(player_name) AS player_name,
                       SUM(pick_count) AS use_count
                  FROM detail_rows
                 GROUP BY source_champion_id, source_player_id
            ),
            ranked_players AS (
                SELECT source_champion_id, player_name, use_count,
                       DENSE_RANK() OVER (PARTITION BY source_champion_id ORDER BY use_count DESC) AS use_rank
                  FROM player_totals
            ),
            popular_players AS (
                SELECT source_champion_id,
                       GROUP_CONCAT(player_name ORDER BY player_name SEPARATOR ',') AS player_names
                  FROM ranked_players
                 WHERE use_rank = 1
                 GROUP BY source_champion_id
            ),
            selected_sample AS (
                SELECT COALESCE(SUM(ss.sample_base_count), 0) AS sample_base_count
                  FROM stage_stat_current ss
                 WHERE (ss.source_season_id, ss.source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
            )
            SELECT c.source_champion_id AS championId,
                   c.chinese_name AS championName,
                   c.chinese_title AS championTitle,
                   c.logo_url AS championLogo,
                   dt.positions_csv AS positionsCsv,
                   pp.player_names AS mostUsedPlayersCsv,
                   sample.sample_base_count AS sampleBaseCount,
                   COALESCE(dt.pick_count, 0) AS pickCount,
                   ot.ban_count AS banCount,
                   COALESCE(dt.pick_count, 0) + ot.ban_count AS bpCount,
                   COALESCE(dt.winning_count, 0) AS winningCount,
                   COALESCE(dt.total_kills, 0) AS totalKills,
                   COALESCE(dt.total_deaths, 0) AS totalDeaths,
                   COALESCE(dt.total_assists, 0) AS totalAssists,
                   ot.source_updated_at AS sourceUpdatedAt
              FROM official_totals ot
              JOIN champion c ON c.source_champion_id = ot.source_champion_id
              CROSS JOIN selected_sample sample
              LEFT JOIN detail_totals dt ON dt.source_champion_id = ot.source_champion_id
              LEFT JOIN popular_players pp ON pp.source_champion_id = ot.source_champion_id
             WHERE COALESCE(dt.pick_count, 0) &gt;= #{minimumPickCount}
             <if test="position != null">
               AND dt.source_champion_id IS NOT NULL
             </if>
            </script>
            """)
    List<ChampionAggregateRow> aggregateChampions(@Param("stages") List<StageKey> stages,
                                                   @Param("minimumPickCount") int minimumPickCount,
                                                   @Param("position") String position);

    @Select("""
            <script>
            SELECT ss.source_season_id AS sourceSeasonId, ss.source_stage_id AS sourceStageId
              FROM stage_stat_current ss
             WHERE (ss.source_season_id, ss.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
               AND EXISTS (
                   SELECT 1
                     FROM champion_position_player_stage_stat_current ps
                    WHERE ps.source_season_id = ss.source_season_id
                      AND ps.source_stage_id = ss.source_stage_id
               )
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);
}
