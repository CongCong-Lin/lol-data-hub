package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.TeamCombinationAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeamCombinationStatisticsMapper {
    @Select("""
            <script>
            WITH selected_lineups AS (
                SELECT l.source_team_id AS team_id,
                       CASE WHEN #{combinationType} = 'MID_JUNGLE'
                            THEN l.jungle_champion_id ELSE l.bot_champion_id END AS first_champion_id,
                       CASE WHEN #{combinationType} = 'MID_JUNGLE'
                            THEN l.mid_champion_id ELSE l.support_champion_id END AS second_champion_id,
                       l.won
                  FROM team_game_lineup_current l
                 WHERE (l.source_season_id, l.source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
            ),
            team_totals AS (
                SELECT team_id, COUNT(*) AS valid_game_count
                  FROM selected_lineups
                 GROUP BY team_id
            )
            SELECT l.team_id AS teamId,
                   COALESCE(t.name, CONCAT('战队 #', l.team_id)) AS teamName,
                   t.logo_url AS teamLogo,
                   l.first_champion_id AS firstChampionId,
                   c1.chinese_name AS firstChampionName,
                   c1.chinese_title AS firstChampionTitle,
                   c1.logo_url AS firstChampionLogo,
                   l.second_champion_id AS secondChampionId,
                   c2.chinese_name AS secondChampionName,
                   c2.chinese_title AS secondChampionTitle,
                   c2.logo_url AS secondChampionLogo,
                   COUNT(*) AS pickCount,
                   totals.valid_game_count AS validGameCount,
                   SUM(CASE WHEN l.won THEN 1 ELSE 0 END) AS winningCount
              FROM selected_lineups l
              JOIN team_totals totals ON totals.team_id = l.team_id
              LEFT JOIN team t ON t.source_team_id = l.team_id
              JOIN champion c1 ON c1.source_champion_id = l.first_champion_id
              JOIN champion c2 ON c2.source_champion_id = l.second_champion_id
             GROUP BY l.team_id, t.name, t.logo_url, totals.valid_game_count,
                      l.first_champion_id, c1.chinese_name, c1.chinese_title, c1.logo_url,
                      l.second_champion_id, c2.chinese_name, c2.chinese_title, c2.logo_url
            HAVING COUNT(*) &gt;= #{minimumPickCount}
            </script>
            """)
    List<TeamCombinationAggregateRow> aggregate(
            @Param("stages") List<StageKey> stages,
            @Param("combinationType") String combinationType,
            @Param("minimumPickCount") int minimumPickCount);

    @Select("""
            <script>
            SELECT DISTINCT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM team_game_lineup_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);
}
