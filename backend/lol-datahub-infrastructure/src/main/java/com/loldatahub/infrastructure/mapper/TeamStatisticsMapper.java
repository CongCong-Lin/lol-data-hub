package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.TeamAggregateRow;
import com.loldatahub.infrastructure.model.TeamLineupPreferenceRow;
import com.loldatahub.infrastructure.model.TeamPlayerUsageRow;
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
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_game_seconds) ELSE NULL END AS totalGameSeconds,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_gold) ELSE NULL END AS totalGold,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_wards_placed) ELSE NULL END AS totalWardsPlaced,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_wards_killed) ELSE NULL END AS totalWardsKilled,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_minion_kills) ELSE NULL END AS totalMinionKills,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_dragons) ELSE NULL END AS totalDragons,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_dragon_opportunities) ELSE NULL END AS totalDragonOpportunities,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_barons) ELSE NULL END AS totalBarons,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_baron_opportunities) ELSE NULL END AS totalBaronOpportunities,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_turrets) ELSE NULL END AS totalTurrets,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.total_turrets_lost) ELSE NULL END AS totalTurretsLost,
                   CASE WHEN SUM(CASE WHEN tdm.source_team_id IS NOT NULL
                                           AND tdm.game_count = COALESCE(ts.game_count, ts.match_count)
                                           AND tdm.total_game_seconds IS NOT NULL
                                      THEN 1 ELSE 0 END) = COUNT(*)
                        THEN SUM(tdm.first_blood_games) ELSE NULL END AS firstBloodGames,
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

    /**
     * 战队阵容偏好：把单局完整阵容的五个英雄列展开为 (位置, 英雄) 后按英雄聚合。
     * pickRate/winningRate 由 Service 层按该队小局数计算。
     */
    @Select("""
            <script>
            SELECT expanded.source_team_id AS teamId,
                   expanded.position AS position,
                   expanded.source_champion_id AS sourceChampionId,
                   COALESCE(c.internal_name, c.chinese_name) AS championName,
                   c.chinese_name AS championChineseName,
                   c.logo_url AS championLogo,
                   COUNT(*) AS pickCount,
                   SUM(CASE WHEN expanded.won THEN 1 ELSE 0 END) AS winningCount
              FROM (
                   SELECT source_team_id, source_match_id, game_number, 'TOP' AS position,
                          top_champion_id AS source_champion_id, won
                     FROM team_game_lineup_current
                    WHERE (source_season_id, source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>
                   UNION ALL
                   SELECT source_team_id, source_match_id, game_number, 'JUN',
                          jungle_champion_id, won
                     FROM team_game_lineup_current
                    WHERE (source_season_id, source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>
                   UNION ALL
                   SELECT source_team_id, source_match_id, game_number, 'MID',
                          mid_champion_id, won
                     FROM team_game_lineup_current
                    WHERE (source_season_id, source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>
                   UNION ALL
                   SELECT source_team_id, source_match_id, game_number, 'BOT',
                          bot_champion_id, won
                     FROM team_game_lineup_current
                    WHERE (source_season_id, source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>
                   UNION ALL
                   SELECT source_team_id, source_match_id, game_number, 'SUP',
                          support_champion_id, won
                     FROM team_game_lineup_current
                    WHERE (source_season_id, source_stage_id) IN
                      <foreach collection="stages" item="sk" open="(" separator="," close=")">
                          (#{sk.sourceSeasonId}, #{sk.sourceStageId})
                      </foreach>
              ) expanded
              LEFT JOIN champion c ON c.source_champion_id = expanded.source_champion_id
             WHERE expanded.source_team_id = #{teamId}
             GROUP BY expanded.source_team_id, expanded.position, expanded.source_champion_id,
                      c.internal_name, c.chinese_name, c.logo_url
             ORDER BY FIELD(expanded.position, 'TOP', 'JUN', 'MID', 'BOT', 'SUP'),
                      pickCount DESC, expanded.source_champion_id
            </script>
            """)
    List<TeamLineupPreferenceRow> aggregateLineupPreferences(@Param("stages") List<StageKey> stages,
                                                             @Param("teamId") long teamId);

    /**
     * 战队选手名单：从对局明细回填表按战队聚合出战选手。
     * 对局明细未回填的赛段返回空列表，不影响战队核心指标展示。
     */
    @Select("""
            <script>
            SELECT p.source_player_id AS sourcePlayerId,
                   (SELECT name FROM player WHERE source_player_id = p.source_player_id
                     ORDER BY player_key LIMIT 1) AS playerName,
                   (SELECT avatar_url FROM player WHERE source_player_id = p.source_player_id
                     ORDER BY player_key LIMIT 1) AS playerAvatar,
                   p.position AS position,
                   COUNT(DISTINCT p.source_match_id) AS matchCount,
                   COUNT(*) AS gameCount
              FROM match_game_player_current p
             WHERE p.source_team_id = #{teamId}
               AND (p.source_season_id, p.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY p.source_player_id, p.position
             ORDER BY gameCount DESC, p.source_player_id
            </script>
            """)
    List<TeamPlayerUsageRow> aggregateTeamPlayers(@Param("stages") List<StageKey> stages,
                                                  @Param("teamId") long teamId);
}
