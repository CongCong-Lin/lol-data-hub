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
                 total_damage, collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{teamId}, #{gameCount}, #{totalAssists},
                    #{totalDamage}, #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE game_count = VALUES(game_count), total_assists = VALUES(total_assists),
                total_damage = VALUES(total_damage), collection_run_id = VALUES(collection_run_id),
                collected_at = VALUES(collected_at)
            """)
    void upsertCurrent(TeamStageDetailMetricWrite metric);

    @Insert("""
            INSERT INTO team_stage_detail_metric_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_team_id, game_count,
                 total_assists, total_damage, collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{teamId}, #{gameCount},
                    #{totalAssists}, #{totalDamage}, #{collectedAt})
            """)
    void insertSnapshot(TeamStageDetailMetricWrite metric);
}
