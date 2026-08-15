package com.loldatahub.infrastructure.mapper;

import com.loldatahub.infrastructure.model.MatchGamePlayerWrite;
import com.loldatahub.infrastructure.model.MatchGameWrite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 对局明细表写入：回填服务按赛段重建 current，并追加 snapshot 留痕。
 */
@Mapper
public interface MatchGameWriteMapper {
    @Delete("""
            DELETE FROM match_game_current
             WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deleteCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Delete("""
            DELETE FROM match_game_player_current
             WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deletePlayerCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Insert("""
            INSERT INTO match_game_current
                (source_season_id, source_stage_id, source_match_id, game_number, start_time,
                 team_a_id, team_b_id, win_team_id, game_duration_seconds,
                 team_a_kills, team_a_assists, team_a_damage, team_a_gold,
                 team_a_wards_placed, team_a_wards_killed, team_a_minion_kills,
                 team_a_dragons, team_a_barons, team_a_turrets, team_a_first_blood,
                 team_b_kills, team_b_assists, team_b_damage, team_b_gold,
                 team_b_wards_placed, team_b_wards_killed, team_b_minion_kills,
                 team_b_dragons, team_b_barons, team_b_turrets, team_b_first_blood,
                 collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{matchId}, #{gameNumber}, #{startTime},
                    #{teamAId}, #{teamBId}, #{winTeamId}, #{gameDurationSeconds},
                    #{teamAKills}, #{teamAAssists}, #{teamADamage}, #{teamAGold},
                    #{teamAWardsPlaced}, #{teamAWardsKilled}, #{teamAMinionKills},
                    #{teamADragons}, #{teamABarons}, #{teamATurrets}, #{teamAFirstBlood},
                    #{teamBKills}, #{teamBAssists}, #{teamBDamage}, #{teamBGold},
                    #{teamBWardsPlaced}, #{teamBWardsKilled}, #{teamBMinionKills},
                    #{teamBDragons}, #{teamBBarons}, #{teamBTurrets}, #{teamBFirstBlood},
                    #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE start_time = VALUES(start_time),
                team_a_id = VALUES(team_a_id), team_b_id = VALUES(team_b_id),
                win_team_id = VALUES(win_team_id),
                game_duration_seconds = VALUES(game_duration_seconds),
                team_a_kills = VALUES(team_a_kills), team_a_assists = VALUES(team_a_assists),
                team_a_damage = VALUES(team_a_damage), team_a_gold = VALUES(team_a_gold),
                team_a_wards_placed = VALUES(team_a_wards_placed),
                team_a_wards_killed = VALUES(team_a_wards_killed),
                team_a_minion_kills = VALUES(team_a_minion_kills),
                team_a_dragons = VALUES(team_a_dragons), team_a_barons = VALUES(team_a_barons),
                team_a_turrets = VALUES(team_a_turrets),
                team_a_first_blood = VALUES(team_a_first_blood),
                team_b_kills = VALUES(team_b_kills), team_b_assists = VALUES(team_b_assists),
                team_b_damage = VALUES(team_b_damage), team_b_gold = VALUES(team_b_gold),
                team_b_wards_placed = VALUES(team_b_wards_placed),
                team_b_wards_killed = VALUES(team_b_wards_killed),
                team_b_minion_kills = VALUES(team_b_minion_kills),
                team_b_dragons = VALUES(team_b_dragons), team_b_barons = VALUES(team_b_barons),
                team_b_turrets = VALUES(team_b_turrets),
                team_b_first_blood = VALUES(team_b_first_blood),
                collection_run_id = VALUES(collection_run_id), collected_at = VALUES(collected_at)
            """)
    void upsertMatchGame(MatchGameWrite game);

    @Insert("""
            INSERT INTO match_game_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_match_id,
                 game_number, start_time, team_a_id, team_b_id, win_team_id,
                 game_duration_seconds, team_a_kills, team_a_assists, team_a_damage, team_a_gold,
                 team_a_wards_placed, team_a_wards_killed, team_a_minion_kills,
                 team_a_dragons, team_a_barons, team_a_turrets, team_a_first_blood,
                 team_b_kills, team_b_assists, team_b_damage, team_b_gold,
                 team_b_wards_placed, team_b_wards_killed, team_b_minion_kills,
                 team_b_dragons, team_b_barons, team_b_turrets, team_b_first_blood,
                 collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{matchId}, #{gameNumber}, #{startTime},
                    #{teamAId}, #{teamBId}, #{winTeamId}, #{gameDurationSeconds},
                    #{teamAKills}, #{teamAAssists}, #{teamADamage}, #{teamAGold},
                    #{teamAWardsPlaced}, #{teamAWardsKilled}, #{teamAMinionKills},
                    #{teamADragons}, #{teamABarons}, #{teamATurrets}, #{teamAFirstBlood},
                    #{teamBKills}, #{teamBAssists}, #{teamBDamage}, #{teamBGold},
                    #{teamBWardsPlaced}, #{teamBWardsKilled}, #{teamBMinionKills},
                    #{teamBDragons}, #{teamBBarons}, #{teamBTurrets}, #{teamBFirstBlood},
                    #{collectedAt})
            """)
    void insertMatchGameSnapshot(MatchGameWrite game);

    @Insert("""
            INSERT INTO match_game_player_current
                (source_season_id, source_stage_id, source_match_id, game_number, start_time,
                 source_player_id, source_team_id, source_champion_id, position, won,
                 kills, deaths, assists, hero_damage, player_gold,
                 team_kills, team_damage, team_gold,
                 kill_participant_percent, damage_percent, gold_percent,
                 collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{matchId}, #{gameNumber}, #{startTime},
                    #{playerId}, #{teamId}, #{championId}, #{position}, #{won},
                    #{kills}, #{deaths}, #{assists}, #{heroDamage}, #{playerGold},
                    #{teamKills}, #{teamDamage}, #{teamGold},
                    #{killParticipantPercent}, #{damagePercent}, #{goldPercent},
                    #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE start_time = VALUES(start_time),
                source_team_id = VALUES(source_team_id),
                source_champion_id = VALUES(source_champion_id),
                position = VALUES(position), won = VALUES(won),
                kills = VALUES(kills), deaths = VALUES(deaths), assists = VALUES(assists),
                hero_damage = VALUES(hero_damage), player_gold = VALUES(player_gold),
                team_kills = VALUES(team_kills), team_damage = VALUES(team_damage),
                team_gold = VALUES(team_gold),
                kill_participant_percent = VALUES(kill_participant_percent),
                damage_percent = VALUES(damage_percent), gold_percent = VALUES(gold_percent),
                collection_run_id = VALUES(collection_run_id), collected_at = VALUES(collected_at)
            """)
    void upsertMatchGamePlayer(MatchGamePlayerWrite player);

    @Insert("""
            INSERT INTO match_game_player_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_match_id,
                 game_number, start_time, source_player_id, source_team_id,
                 source_champion_id, position, won, kills, deaths, assists,
                 hero_damage, player_gold, team_kills, team_damage, team_gold,
                 kill_participant_percent, damage_percent, gold_percent, collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{matchId}, #{gameNumber}, #{startTime},
                    #{playerId}, #{teamId}, #{championId}, #{position}, #{won},
                    #{kills}, #{deaths}, #{assists}, #{heroDamage}, #{playerGold},
                    #{teamKills}, #{teamDamage}, #{teamGold},
                    #{killParticipantPercent}, #{damagePercent}, #{goldPercent}, #{collectedAt})
            """)
    void insertMatchGamePlayerSnapshot(MatchGamePlayerWrite player);
}
