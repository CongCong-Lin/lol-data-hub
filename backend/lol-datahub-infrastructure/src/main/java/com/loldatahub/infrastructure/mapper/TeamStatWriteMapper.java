package com.loldatahub.infrastructure.mapper;

import com.loldatahub.infrastructure.model.TeamStageStatWrite;
import com.loldatahub.infrastructure.model.TeamWrite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

@Mapper
public interface TeamStatWriteMapper {
    @Insert("""
            INSERT INTO team (source_team_id, name, logo_url)
            VALUES (#{teamId}, #{name}, #{logoUrl})
            ON DUPLICATE KEY UPDATE name = VALUES(name), logo_url = VALUES(logo_url)
            """)
    void upsertTeam(TeamWrite team);

    @Insert("""
            INSERT INTO team_stage_collection_current
                (source_season_id, source_stage_id, content_hash, collected_at, collection_run_id)
            VALUES (#{seasonId}, #{stageId}, #{contentHash}, #{collectedAt}, #{runId})
            ON DUPLICATE KEY UPDATE content_hash = VALUES(content_hash),
                collected_at = VALUES(collected_at), collection_run_id = VALUES(collection_run_id)
            """)
    void upsertCollectionCurrent(@Param("seasonId") long seasonId,
                                 @Param("stageId") long stageId,
                                 @Param("contentHash") String contentHash,
                                 @Param("collectedAt") OffsetDateTime collectedAt,
                                 @Param("runId") long runId);

    @Delete("""
            DELETE FROM team_stage_stat_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deleteCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Insert("""
            INSERT INTO team_stage_stat_current
                (source_season_id, source_stage_id, source_team_id, match_count, match_win_count,
                 total_kills, total_deaths, source_ward_placed_per_game, source_ward_killed_per_game,
                 source_gold_per_game, source_baron_kill_per_game, source_drake_kill_per_game,
                 collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{teamId}, #{matchCount}, #{matchWinCount},
                    #{totalKills}, #{totalDeaths}, #{sourceWardPlacedPerGame}, #{sourceWardKilledPerGame},
                    #{sourceGoldPerGame}, #{sourceBaronKillPerGame}, #{sourceDrakeKillPerGame},
                    #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE match_count = VALUES(match_count),
                match_win_count = VALUES(match_win_count), total_kills = VALUES(total_kills),
                total_deaths = VALUES(total_deaths),
                source_ward_placed_per_game = VALUES(source_ward_placed_per_game),
                source_ward_killed_per_game = VALUES(source_ward_killed_per_game),
                source_gold_per_game = VALUES(source_gold_per_game),
                source_baron_kill_per_game = VALUES(source_baron_kill_per_game),
                source_drake_kill_per_game = VALUES(source_drake_kill_per_game),
                collection_run_id = VALUES(collection_run_id), collected_at = VALUES(collected_at)
            """)
    void upsertCurrent(TeamStageStatWrite stat);

    @Insert("""
            INSERT INTO team_stage_stat_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_team_id,
                 match_count, match_win_count, total_kills, total_deaths,
                 source_ward_placed_per_game, source_ward_killed_per_game,
                 source_gold_per_game, source_baron_kill_per_game, source_drake_kill_per_game,
                 collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{teamId},
                    #{matchCount}, #{matchWinCount}, #{totalKills}, #{totalDeaths},
                    #{sourceWardPlacedPerGame}, #{sourceWardKilledPerGame},
                    #{sourceGoldPerGame}, #{sourceBaronKillPerGame}, #{sourceDrakeKillPerGame},
                    #{collectedAt})
            """)
    void insertSnapshot(TeamStageStatWrite stat);
}
