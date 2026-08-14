package com.loldatahub.infrastructure.mapper;

import com.loldatahub.infrastructure.model.TeamStageDetailMetricWrite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeamStageDetailMetricWriteMapper {
    @Delete("""
            DELETE FROM team_stage_detail_metric_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deleteCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Insert("""
            INSERT INTO team_stage_detail_metric_current
                (source_season_id, source_stage_id, source_team_id, game_count, total_assists,
                 total_damage, total_game_seconds, total_gold, total_wards_placed, total_wards_killed,
                 total_minion_kills, total_dragons, total_dragon_opportunities, total_barons,
                 total_baron_opportunities, total_turrets, total_turrets_lost, first_blood_games,
                 collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{teamId}, #{gameCount}, #{totalAssists},
                    #{totalDamage}, #{totalGameSeconds}, #{totalGold}, #{totalWardsPlaced}, #{totalWardsKilled},
                    #{totalMinionKills}, #{totalDragons}, #{totalDragonOpportunities}, #{totalBarons},
                    #{totalBaronOpportunities}, #{totalTurrets}, #{totalTurretsLost}, #{firstBloodGames},
                    #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE game_count = VALUES(game_count), total_assists = VALUES(total_assists),
                total_damage = VALUES(total_damage), total_game_seconds = VALUES(total_game_seconds),
                total_gold = VALUES(total_gold), total_wards_placed = VALUES(total_wards_placed),
                total_wards_killed = VALUES(total_wards_killed), total_minion_kills = VALUES(total_minion_kills),
                total_dragons = VALUES(total_dragons), total_dragon_opportunities = VALUES(total_dragon_opportunities),
                total_barons = VALUES(total_barons), total_baron_opportunities = VALUES(total_baron_opportunities),
                total_turrets = VALUES(total_turrets), total_turrets_lost = VALUES(total_turrets_lost),
                first_blood_games = VALUES(first_blood_games), collection_run_id = VALUES(collection_run_id),
                collected_at = VALUES(collected_at)
            """)
    void upsertCurrent(TeamStageDetailMetricWrite metric);

    @Insert("""
            INSERT INTO team_stage_detail_metric_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_team_id, game_count,
                 total_assists, total_damage, total_game_seconds, total_gold, total_wards_placed,
                 total_wards_killed, total_minion_kills, total_dragons, total_dragon_opportunities,
                 total_barons, total_baron_opportunities, total_turrets, total_turrets_lost, first_blood_games, collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{teamId}, #{gameCount},
                    #{totalAssists}, #{totalDamage}, #{totalGameSeconds}, #{totalGold}, #{totalWardsPlaced},
                    #{totalWardsKilled}, #{totalMinionKills}, #{totalDragons}, #{totalDragonOpportunities},
                    #{totalBarons}, #{totalBaronOpportunities}, #{totalTurrets}, #{totalTurretsLost}, #{firstBloodGames}, #{collectedAt})
            """)
    void insertSnapshot(TeamStageDetailMetricWrite metric);
}
