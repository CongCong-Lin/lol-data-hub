package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.MatchGamePlayerRow;
import com.loldatahub.infrastructure.model.MatchGameRow;
import com.loldatahub.infrastructure.model.MatchTeamGameRow;
import com.loldatahub.infrastructure.model.PlayerGameRow;
import com.loldatahub.infrastructure.model.StageGameCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对局明细读取：对局赛果列表、对局详情、选手单局战绩、战队近期对局。
 * orderBy 由 Service 层从白名单枚举生成固定排序片段，不直接接受用户输入。
 */
@Mapper
public interface MatchGameMapper {
    @Select("""
            <script>
            SELECT g.source_season_id AS sourceSeasonId,
                   g.source_stage_id AS sourceStageId,
                   g.source_match_id AS sourceMatchId,
                   g.game_number AS gameNumber,
                   g.start_time AS startTime,
                   g.team_a_id AS teamAId,
                   COALESCE(ta.name, CONCAT('战队 #', g.team_a_id)) AS teamAName,
                   ta.logo_url AS teamALogo,
                   g.team_a_kills AS teamAKills, g.team_a_assists AS teamAAssists,
                   g.team_a_damage AS teamADamage, g.team_a_gold AS teamAGold,
                   g.team_a_wards_placed AS teamAWardsPlaced,
                   g.team_a_wards_killed AS teamAWardsKilled,
                   g.team_a_minion_kills AS teamAMinionKills,
                   g.team_a_dragons AS teamADragons, g.team_a_barons AS teamABarons,
                   g.team_a_turrets AS teamATurrets, g.team_a_first_blood AS teamAFirstBlood,
                   g.team_b_id AS teamBId,
                   COALESCE(tb.name, CONCAT('战队 #', g.team_b_id)) AS teamBName,
                   tb.logo_url AS teamBLogo,
                   g.team_b_kills AS teamBKills, g.team_b_assists AS teamBAssists,
                   g.team_b_damage AS teamBDamage, g.team_b_gold AS teamBGold,
                   g.team_b_wards_placed AS teamBWardsPlaced,
                   g.team_b_wards_killed AS teamBWardsKilled,
                   g.team_b_minion_kills AS teamBMinionKills,
                   g.team_b_dragons AS teamBDragons, g.team_b_barons AS teamBBarons,
                   g.team_b_turrets AS teamBTurrets, g.team_b_first_blood AS teamBFirstBlood,
                   g.win_team_id AS winnerTeamId,
                   g.game_duration_seconds AS gameDurationSeconds
              FROM match_game_current g
              LEFT JOIN team ta ON ta.source_team_id = g.team_a_id
              LEFT JOIN team tb ON tb.source_team_id = g.team_b_id
             WHERE (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY ${orderBy}
             LIMIT #{offset}, #{limit}
            </script>
            """)
    List<MatchGameRow> aggregateMatchGames(@Param("stages") List<StageKey> stages,
                                           @Param("orderBy") String orderBy,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM match_game_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    long countMatchGames(@Param("stages") List<StageKey> stages);

    @Select("""
            <script>
            SELECT g.source_season_id AS sourceSeasonId,
                   g.source_stage_id AS sourceStageId,
                   g.source_match_id AS sourceMatchId,
                   g.game_number AS gameNumber,
                   g.start_time AS startTime,
                   g.team_a_id AS teamAId,
                   COALESCE(ta.name, CONCAT('战队 #', g.team_a_id)) AS teamAName,
                   ta.logo_url AS teamALogo,
                   g.team_b_id AS teamBId,
                   COALESCE(tb.name, CONCAT('战队 #', g.team_b_id)) AS teamBName,
                   tb.logo_url AS teamBLogo,
                   g.win_team_id AS winnerTeamId
              FROM match_game_current g
              LEFT JOIN team ta ON ta.source_team_id = g.team_a_id
              LEFT JOIN team tb ON tb.source_team_id = g.team_b_id
             WHERE (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
               AND (g.team_a_id = #{teamId} OR g.team_b_id = #{teamId})
             ORDER BY g.start_time, g.source_match_id, g.game_number
            </script>
            """)
    List<MatchTeamGameRow> findTeamGames(@Param("stages") List<StageKey> stages,
                                         @Param("teamId") long teamId);

    @Select("""
            <script>
            SELECT g.source_season_id AS sourceSeasonId,
                   g.source_stage_id AS sourceStageId,
                   g.source_match_id AS sourceMatchId,
                   g.game_number AS gameNumber,
                   g.start_time AS startTime,
                   g.team_a_id AS teamAId,
                   COALESCE(ta.name, CONCAT('战队 #', g.team_a_id)) AS teamAName,
                   ta.logo_url AS teamALogo,
                   g.team_b_id AS teamBId,
                   COALESCE(tb.name, CONCAT('战队 #', g.team_b_id)) AS teamBName,
                   tb.logo_url AS teamBLogo,
                   g.win_team_id AS winnerTeamId
              FROM match_game_current g
              LEFT JOIN team ta ON ta.source_team_id = g.team_a_id
              LEFT JOIN team tb ON tb.source_team_id = g.team_b_id
             WHERE (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY g.start_time, g.source_match_id, g.game_number
            </script>
            """)
    List<MatchTeamGameRow> findAllGames(@Param("stages") List<StageKey> stages);

    @Select("""
            <script>
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId,
                   COUNT(*) AS games
              FROM match_game_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             GROUP BY source_season_id, source_stage_id
            </script>
            """)
    List<StageGameCountRow> countGamesByStage(@Param("stages") List<StageKey> stages);

    @Select("""
            <script>
            SELECT p.source_season_id AS sourceSeasonId,
                   p.source_stage_id AS sourceStageId,
                   p.source_match_id AS sourceMatchId,
                   p.game_number AS gameNumber,
                   p.start_time AS startTime,
                   p.source_player_id AS sourcePlayerId,
                   (SELECT name FROM player WHERE source_player_id = p.source_player_id
                     ORDER BY player_key LIMIT 1) AS playerName,
                   p.source_team_id AS sourceTeamId,
                   COALESCE(t.name, CONCAT('战队 #', p.source_team_id)) AS teamName,
                   p.source_champion_id AS sourceChampionId,
                   COALESCE(c.internal_name, c.chinese_name) AS championName,
                   c.chinese_name AS championChineseName,
                   c.chinese_title AS championTitle,
                   c.logo_url AS championLogo,
                   p.position AS position,
                   p.won AS won,
                   p.kills AS kills, p.deaths AS deaths, p.assists AS assists,
                   p.hero_damage AS heroDamage, p.player_gold AS playerGold,
                   p.team_kills AS teamKills, p.team_damage AS teamDamage,
                   p.team_gold AS teamGold,
                   p.kill_participant_percent AS killParticipantPercent,
                   p.damage_percent AS damagePercent,
                   p.gold_percent AS goldPercent
              FROM match_game_player_current p
              LEFT JOIN team t ON t.source_team_id = p.source_team_id
              LEFT JOIN champion c ON c.source_champion_id = p.source_champion_id
             WHERE p.source_match_id = #{matchId}
               AND (p.source_season_id, p.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY p.game_number, p.source_team_id, p.position
            </script>
            """)
    List<MatchGamePlayerRow> findMatchGamePlayers(@Param("stages") List<StageKey> stages,
                                                  @Param("matchId") long matchId);

    @Select("""
            <script>
            SELECT g.source_season_id AS sourceSeasonId,
                   g.source_stage_id AS sourceStageId,
                   g.source_match_id AS sourceMatchId,
                   g.game_number AS gameNumber,
                   g.start_time AS startTime,
                   g.team_a_id AS teamAId,
                   COALESCE(ta.name, CONCAT('战队 #', g.team_a_id)) AS teamAName,
                   ta.logo_url AS teamALogo,
                   g.team_a_kills AS teamAKills, g.team_a_assists AS teamAAssists,
                   g.team_a_damage AS teamADamage, g.team_a_gold AS teamAGold,
                   g.team_a_wards_placed AS teamAWardsPlaced,
                   g.team_a_wards_killed AS teamAWardsKilled,
                   g.team_a_minion_kills AS teamAMinionKills,
                   g.team_a_dragons AS teamADragons, g.team_a_barons AS teamABarons,
                   g.team_a_turrets AS teamATurrets, g.team_a_first_blood AS teamAFirstBlood,
                   g.team_b_id AS teamBId,
                   COALESCE(tb.name, CONCAT('战队 #', g.team_b_id)) AS teamBName,
                   tb.logo_url AS teamBLogo,
                   g.team_b_kills AS teamBKills, g.team_b_assists AS teamBAssists,
                   g.team_b_damage AS teamBDamage, g.team_b_gold AS teamBGold,
                   g.team_b_wards_placed AS teamBWardsPlaced,
                   g.team_b_wards_killed AS teamBWardsKilled,
                   g.team_b_minion_kills AS teamBMinionKills,
                   g.team_b_dragons AS teamBDragons, g.team_b_barons AS teamBBarons,
                   g.team_b_turrets AS teamBTurrets, g.team_b_first_blood AS teamBFirstBlood,
                   g.win_team_id AS winnerTeamId,
                   g.game_duration_seconds AS gameDurationSeconds
              FROM match_game_current g
              LEFT JOIN team ta ON ta.source_team_id = g.team_a_id
              LEFT JOIN team tb ON tb.source_team_id = g.team_b_id
             WHERE g.source_match_id = #{matchId}
               AND (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY g.game_number
            </script>
            """)
    List<MatchGameRow> findMatchGamesByMatchId(@Param("stages") List<StageKey> stages,
                                               @Param("matchId") long matchId);

    @Select("""
            SELECT name FROM player
            WHERE source_player_id = #{playerId}
            ORDER BY player_key LIMIT 1
            """)
    String findPlayerName(@Param("playerId") long playerId);

    @Select("""
            <script>
            SELECT p.source_season_id AS sourceSeasonId,
                   p.source_stage_id AS sourceStageId,
                   st.name AS stageName,
                   p.source_match_id AS sourceMatchId,
                   p.game_number AS gameNumber,
                   p.start_time AS startTime,
                   COALESCE(oppt.name, CONCAT('战队 #', CASE WHEN g.team_a_id = p.source_team_id
                                                              THEN g.team_b_id ELSE g.team_a_id END)) AS opponentTeamName,
                   p.source_champion_id AS sourceChampionId,
                   COALESCE(c.internal_name, c.chinese_name) AS championName,
                   c.chinese_name AS championChineseName,
                   c.logo_url AS championLogo,
                   p.position AS position,
                   p.won AS won,
                   p.kills AS kills, p.deaths AS deaths, p.assists AS assists,
                   p.hero_damage AS heroDamage,
                   p.kill_participant_percent AS killParticipantPercent,
                   p.damage_percent AS damagePercent
              FROM match_game_player_current p
              JOIN match_game_current g
                ON g.source_season_id = p.source_season_id
               AND g.source_stage_id = p.source_stage_id
               AND g.source_match_id = p.source_match_id
               AND g.game_number = p.game_number
              LEFT JOIN team oppt ON oppt.source_team_id = CASE WHEN g.team_a_id = p.source_team_id
                                                                  THEN g.team_b_id ELSE g.team_a_id END
              LEFT JOIN champion c ON c.source_champion_id = p.source_champion_id
              LEFT JOIN stage st
                ON st.source_season_id = p.source_season_id
               AND st.source_stage_id = p.source_stage_id
             WHERE p.source_player_id = #{playerId}
               AND (p.source_season_id, p.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY p.start_time DESC, p.source_match_id DESC, p.game_number DESC
             LIMIT #{limit}
            </script>
            """)
    List<PlayerGameRow> findPlayerGames(@Param("stages") List<StageKey> stages,
                                        @Param("playerId") long playerId,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT g.source_season_id AS sourceSeasonId,
                   g.source_stage_id AS sourceStageId,
                   g.source_match_id AS sourceMatchId,
                   g.game_number AS gameNumber,
                   g.start_time AS startTime,
                   g.team_a_id AS teamAId,
                   COALESCE(ta.name, CONCAT('战队 #', g.team_a_id)) AS teamAName,
                   ta.logo_url AS teamALogo,
                   g.team_a_kills AS teamAKills, g.team_a_assists AS teamAAssists,
                   g.team_a_damage AS teamADamage, g.team_a_gold AS teamAGold,
                   g.team_a_wards_placed AS teamAWardsPlaced,
                   g.team_a_wards_killed AS teamAWardsKilled,
                   g.team_a_minion_kills AS teamAMinionKills,
                   g.team_a_dragons AS teamADragons, g.team_a_barons AS teamABarons,
                   g.team_a_turrets AS teamATurrets, g.team_a_first_blood AS teamAFirstBlood,
                   g.team_b_id AS teamBId,
                   COALESCE(tb.name, CONCAT('战队 #', g.team_b_id)) AS teamBName,
                   tb.logo_url AS teamBLogo,
                   g.team_b_kills AS teamBKills, g.team_b_assists AS teamBAssists,
                   g.team_b_damage AS teamBDamage, g.team_b_gold AS teamBGold,
                   g.team_b_wards_placed AS teamBWardsPlaced,
                   g.team_b_wards_killed AS teamBWardsKilled,
                   g.team_b_minion_kills AS teamBMinionKills,
                   g.team_b_dragons AS teamBDragons, g.team_b_barons AS teamBBarons,
                   g.team_b_turrets AS teamBTurrets, g.team_b_first_blood AS teamBFirstBlood,
                   g.win_team_id AS winnerTeamId,
                   g.game_duration_seconds AS gameDurationSeconds
              FROM match_game_current g
              LEFT JOIN team ta ON ta.source_team_id = g.team_a_id
              LEFT JOIN team tb ON tb.source_team_id = g.team_b_id
             WHERE (g.team_a_id = #{teamId} OR g.team_b_id = #{teamId})
               AND (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
             ORDER BY g.start_time DESC, g.source_match_id DESC, g.game_number DESC
             LIMIT #{limit}
            </script>
            """)
    List<MatchGameRow> findRecentGames(@Param("stages") List<StageKey> stages,
                                       @Param("teamId") long teamId,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT DISTINCT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM match_game_current
             WHERE (source_season_id, source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    List<StageKey> findCollectedStageKeys(@Param("stages") List<StageKey> stages);

    @Select("""
            <script>
            SELECT MAX(g.collected_at)
              FROM match_game_current g
             WHERE (g.source_season_id, g.source_stage_id) IN
               <foreach collection="stages" item="sk" open="(" separator="," close=")">
                   (#{sk.sourceSeasonId}, #{sk.sourceStageId})
               </foreach>
            </script>
            """)
    LocalDateTime findLatestCollectedAt(@Param("stages") List<StageKey> stages);
}
