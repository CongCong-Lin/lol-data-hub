package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.PlayerHeroUsageAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 选手详情页的英雄使用明细查询。
 * 事实表为 champion_position_player_stage_stat_current（逐局英雄明细），
 * 过滤维度必须是 (source_player_id, position, 复合赛段键)，跨赛事禁止只按 stage_id 过滤。
 */
@Mapper
public interface PlayerHeroUsageMapper {
    @Select("""
            <script>
            SELECT ps.source_champion_id AS sourceChampionId,
                   COALESCE(c.internal_name, c.chinese_name) AS championName,
                   c.chinese_name AS championChineseName,
                   c.chinese_title AS championTitle,
                   c.logo_url AS championLogo,
                   SUM(ps.pick_count) AS pickCount,
                   SUM(ps.winning_count) AS winningCount,
                   SUM(ps.total_kills) AS totalKills,
                   SUM(ps.total_deaths) AS totalDeaths,
                   SUM(ps.total_assists) AS totalAssists
              FROM champion_position_player_stage_stat_current ps
              JOIN champion c ON c.source_champion_id = ps.source_champion_id
             WHERE ps.source_player_id = #{sourcePlayerId}
               AND ps.position = #{position}
               AND (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY ps.source_champion_id, c.internal_name, c.chinese_name, c.chinese_title, c.logo_url
            </script>
            """)
    List<PlayerHeroUsageAggregateRow> aggregateHeroUsage(@Param("stages") List<StageKey> stages,
                                                         @Param("sourcePlayerId") long sourcePlayerId,
                                                         @Param("position") String position);

    /**
     * 选手 ID 是否在库中存在（任意赛事/位置）。
     */
    @Select("SELECT COUNT(*) FROM player WHERE source_player_id = #{sourcePlayerId}")
    long countPlayersBySourceId(@Param("sourcePlayerId") long sourcePlayerId);

    /**
     * 选手在所选赛段中指定位置的选手统计行数，用于区分“无该位置数据”与“未达样本门槛”。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM player_stage_stat_current ps
              JOIN player p ON p.player_key = ps.player_key
             WHERE p.source_player_id = #{sourcePlayerId}
               AND UPPER(ps.player_position) = #{position}
               AND (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    long countPlayerPositionRows(@Param("stages") List<StageKey> stages,
                                 @Param("sourcePlayerId") long sourcePlayerId,
                                 @Param("position") String position);

    @Select("""
            <script>
            SELECT UPPER(ps.player_position)
              FROM player_stage_stat_current ps
              JOIN player p ON p.player_key = ps.player_key
             WHERE p.source_player_id = #{sourcePlayerId}
               AND (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY UPPER(ps.player_position)
            HAVING SUM(ps.match_count) &gt;= #{minimumMatchCount}
             ORDER BY FIELD(MIN(UPPER(ps.player_position)), 'TOP', 'JUG', 'MID', 'AD', 'SUP')
            </script>
            """)
    List<String> findQualifiedPlayerPositions(@Param("stages") List<StageKey> stages,
                                              @Param("sourcePlayerId") long sourcePlayerId,
                                              @Param("minimumMatchCount") int minimumMatchCount);

    /**
     * 所选赛段选手数据的最近采集时间，用于详情页展示数据新鲜度。
     */
    @Select("""
            <script>
            SELECT MAX(ps.collected_at)
              FROM player_stage_stat_current ps
             WHERE (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    LocalDateTime findLatestCollectedAt(@Param("stages") List<StageKey> stages);
}
