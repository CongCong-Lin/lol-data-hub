package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.ChampionAggregateRow;
import com.loldatahub.infrastructure.model.ChampionPlayerUsageRow;
import com.loldatahub.infrastructure.model.ChampionPositionStatRow;
import com.loldatahub.infrastructure.model.ChampionTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
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

    /** 英雄在全部五个分路的实际使用合计，供详情页分路统计展示。 */
    @Select("""
            <script>
            SELECT ps.position AS position,
                   SUM(ps.pick_count) AS pickCount,
                   SUM(ps.winning_count) AS winningCount,
                   SUM(ps.total_kills) AS totalKills,
                   SUM(ps.total_deaths) AS totalDeaths,
                   SUM(ps.total_assists) AS totalAssists
              FROM champion_position_player_stage_stat_current ps
             WHERE ps.source_champion_id = #{championId}
               AND (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY ps.position
             ORDER BY FIELD(ps.position, 'TOP', 'JUN', 'MID', 'BOT', 'SUP')
            </script>
            """)
    List<ChampionPositionStatRow> aggregatePositionStats(@Param("stages") List<StageKey> stages,
                                                         @Param("championId") long championId);

    /**
     * 英雄常用选手榜：按 (选手, 分路) 聚合逐局明细，出场次数达到门槛的选手。
     * position 为空表示不限分路。
     */
    @Select("""
            <script>
            SELECT ps.source_player_id AS sourcePlayerId,
                   MAX(COALESCE(p.name, ps.player_name)) AS playerName,
                   (SELECT avatar_url FROM player
                     WHERE source_player_id = ps.source_player_id
                     ORDER BY player_key LIMIT 1) AS playerAvatar,
                   ps.position AS position,
                   SUM(ps.pick_count) AS pickCount,
                   SUM(ps.winning_count) AS winningCount,
                   SUM(ps.total_kills) AS totalKills,
                   SUM(ps.total_deaths) AS totalDeaths,
                   SUM(ps.total_assists) AS totalAssists
              FROM champion_position_player_stage_stat_current ps
              LEFT JOIN player p ON p.source_player_id = ps.source_player_id
             WHERE ps.source_champion_id = #{championId}
               <if test="position != null">
               AND ps.position = #{position}
               </if>
               AND (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY ps.source_player_id, ps.position
            HAVING SUM(ps.pick_count) &gt;= #{minimumPickCount}
             ORDER BY pickCount DESC, ps.position, ps.source_player_id
            </script>
            """)
    List<ChampionPlayerUsageRow> aggregatePlayerUsage(@Param("stages") List<StageKey> stages,
                                                      @Param("championId") long championId,
                                                      @Param("position") String position,
                                                      @Param("minimumPickCount") int minimumPickCount);

    /** 英雄按赛段的指标趋势，按赛段时间升序返回。 */
    @Select("""
            <script>
            SELECT cs.source_season_id AS sourceSeasonId,
                   cs.source_stage_id AS sourceStageId,
                   st.name AS stageName,
                   cs.pick_count AS pickCount,
                   cs.ban_count AS banCount,
                   cs.winning_count AS winningCount,
                   cs.source_pick_rate AS pickRate,
                   cs.source_ban_rate AS banRate,
                   cs.source_winning_rate AS winningRate
              FROM champion_stage_stat_current cs
              JOIN stage st
                ON st.source_season_id = cs.source_season_id
               AND st.source_stage_id = cs.source_stage_id
             WHERE cs.source_champion_id = #{championId}
               AND (cs.source_season_id, cs.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY st.start_time, cs.source_stage_id
            </script>
            """)
    List<ChampionTrendRow> findChampionTrends(@Param("stages") List<StageKey> stages,
                                              @Param("championId") long championId);

    /** 英雄基本信息（目录表），供详情页头部展示。 */
    @Select("""
            SELECT internal_name AS internalName,
                   chinese_name AS chineseName,
                   chinese_title AS chineseTitle,
                   logo_url AS logoUrl
              FROM champion
             WHERE source_champion_id = #{championId}
            """)
    com.loldatahub.infrastructure.model.ChampionProfileRow findChampionProfile(
            @Param("championId") long championId);

    /** 所选赛段逐局英雄明细的最近采集时间，用于详情页展示数据新鲜度。 */
    @Select("""
            <script>
            SELECT MAX(ps.collected_at)
              FROM champion_position_player_stage_stat_current ps
             WHERE (ps.source_season_id, ps.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    LocalDateTime findLatestCollectedAt(@Param("stages") List<StageKey> stages);

    /**
     * 英雄对位克制：把每局双方五位置阵容展开后按指定分路自连接，
     * 统计目标英雄对该分路对手英雄的场次与获胜场次。
     */
    @Select("""
            <script>
            WITH expanded AS (
                SELECT source_season_id AS season_id, source_stage_id AS stage_id,
                       source_team_id AS team_id, source_match_id AS match_id,
                       game_number AS game_number, 'TOP' AS position,
                       top_champion_id AS champion_id, won
                  FROM team_game_lineup_current
                 WHERE (source_season_id, source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                UNION ALL
                SELECT source_season_id, source_stage_id, source_team_id, source_match_id,
                       game_number, 'JUN', jungle_champion_id, won
                  FROM team_game_lineup_current
                 WHERE (source_season_id, source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                UNION ALL
                SELECT source_season_id, source_stage_id, source_team_id, source_match_id,
                       game_number, 'MID', mid_champion_id, won
                  FROM team_game_lineup_current
                 WHERE (source_season_id, source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                UNION ALL
                SELECT source_season_id, source_stage_id, source_team_id, source_match_id,
                       game_number, 'BOT', bot_champion_id, won
                  FROM team_game_lineup_current
                 WHERE (source_season_id, source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
                UNION ALL
                SELECT source_season_id, source_stage_id, source_team_id, source_match_id,
                       game_number, 'SUP', support_champion_id, won
                  FROM team_game_lineup_current
                 WHERE (source_season_id, source_stage_id) IN
                   <foreach collection="stages" item="sk" open="(" separator="," close=")">
                       (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                   </foreach>
            )
            SELECT b.champion_id AS opponentChampionId,
                   c.internal_name AS championName,
                   c.chinese_name AS championChineseName,
                   c.chinese_title AS championTitle,
                   c.logo_url AS championLogo,
                   COUNT(*) AS games,
                   SUM(CASE WHEN a.won THEN 1 ELSE 0 END) AS wins
              FROM expanded a
              JOIN expanded b
                ON b.season_id = a.season_id AND b.stage_id = a.stage_id
               AND b.match_id = a.match_id AND b.game_number = a.game_number
               AND b.team_id != a.team_id
              JOIN champion c ON c.source_champion_id = b.champion_id
             WHERE a.position = #{position} AND b.position = #{position}
               AND a.champion_id = #{championId}
             GROUP BY b.champion_id, c.internal_name, c.chinese_name, c.chinese_title, c.logo_url
            HAVING COUNT(*) &gt;= #{minimumGames}
             ORDER BY games DESC, b.champion_id
            </script>
            """)
    List<com.loldatahub.infrastructure.model.ChampionCounterRow> aggregateChampionCounters(
            @Param("stages") List<StageKey> stages,
            @Param("championId") long championId,
            @Param("position") String position,
            @Param("minimumGames") int minimumGames);

    /** 版本窗口对比：读取所选赛段的全部英雄统计快照精简行，窗口还原在 Service 层完成。 */
    @Select("""
            <script>
            SELECT source_season_id AS sourceSeasonId,
                   source_stage_id AS sourceStageId,
                   source_champion_id AS sourceChampionId,
                   pick_count AS pickCount,
                   winning_count AS winningCount,
                   collected_at AS collectedAt
              FROM champion_stage_stat_snapshot
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<com.loldatahub.infrastructure.model.ChampionSnapshotRow> findChampionSnapshots(
            @Param("stages") List<StageKey> stages);

    /** 版本窗口对比的英雄名称目录。 */
    @Select("""
            SELECT source_champion_id AS sourceChampionId,
                   internal_name AS internalName,
                   chinese_name AS chineseName,
                   logo_url AS logoUrl
              FROM champion
            """)
    List<com.loldatahub.infrastructure.model.ChampionCatalogRow> findChampionCatalog();
}
