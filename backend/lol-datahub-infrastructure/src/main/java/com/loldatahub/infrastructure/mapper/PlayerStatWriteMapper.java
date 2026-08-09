package com.loldatahub.infrastructure.mapper;

import com.loldatahub.infrastructure.model.PlayerStageStatWrite;
import com.loldatahub.infrastructure.model.PlayerWrite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

@Mapper
public interface PlayerStatWriteMapper {
    @Insert("""
            INSERT INTO player (player_key, source_player_id, name, avatar_url)
            VALUES (#{playerKey}, #{sourcePlayerId}, #{name}, #{avatarUrl})
            ON DUPLICATE KEY UPDATE source_player_id = COALESCE(VALUES(source_player_id), source_player_id),
                name = VALUES(name), avatar_url = VALUES(avatar_url)
            """)
    void upsertPlayer(PlayerWrite player);

    @Insert("""
            INSERT INTO player_stage_collection_current
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
            DELETE FROM player_stage_stat_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deleteCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Insert("""
            INSERT INTO player_stage_stat_current
                 (source_season_id, source_stage_id, player_key, team_name, team_logo, player_position,
                 match_count, game_count, mvp_count, mvp_votes, total_kills, total_assists, total_deaths,
                 source_gold_per_game, source_creep_score_per_game, source_ward_placed_per_game,
                 source_ward_killed_per_game, source_kill_participant_percent, source_gold_gap_per_game,
                 source_damage_percent, source_gold_percent, collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{playerKey}, #{teamName}, #{teamLogo}, #{playerPosition},
                    #{matchCount}, #{gameCount}, #{mvpCount}, #{mvpVotes}, #{totalKills}, #{totalAssists}, #{totalDeaths},
                    #{sourceGoldPerGame}, #{sourceCreepScorePerGame}, #{sourceWardPlacedPerGame},
                    #{sourceWardKilledPerGame}, #{sourceKillParticipantPercent}, #{sourceGoldGapPerGame},
                    #{sourceDamagePercent}, #{sourceGoldPercent}, #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE team_name = VALUES(team_name), team_logo = VALUES(team_logo),
                player_position = VALUES(player_position), match_count = VALUES(match_count),
                game_count = VALUES(game_count),
                mvp_count = VALUES(mvp_count), mvp_votes = VALUES(mvp_votes),
                total_kills = VALUES(total_kills), total_assists = VALUES(total_assists),
                total_deaths = VALUES(total_deaths),
                source_gold_per_game = VALUES(source_gold_per_game),
                source_creep_score_per_game = VALUES(source_creep_score_per_game),
                source_ward_placed_per_game = VALUES(source_ward_placed_per_game),
                source_ward_killed_per_game = VALUES(source_ward_killed_per_game),
                source_kill_participant_percent = VALUES(source_kill_participant_percent),
                source_gold_gap_per_game = VALUES(source_gold_gap_per_game),
                source_damage_percent = VALUES(source_damage_percent),
                source_gold_percent = VALUES(source_gold_percent),
                collection_run_id = VALUES(collection_run_id), collected_at = VALUES(collected_at)
            """)
    void upsertCurrent(PlayerStageStatWrite stat);

    @Insert("""
            INSERT INTO player_stage_stat_snapshot
                (collection_run_id, source_season_id, source_stage_id, player_key,
                 team_name, team_logo, player_position,
                 match_count, game_count, mvp_count, mvp_votes, total_kills, total_assists, total_deaths,
                 source_gold_per_game, source_creep_score_per_game, source_ward_placed_per_game,
                 source_ward_killed_per_game, source_kill_participant_percent, source_gold_gap_per_game,
                 source_damage_percent, source_gold_percent, collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{playerKey},
                    #{teamName}, #{teamLogo}, #{playerPosition},
                    #{matchCount}, #{gameCount}, #{mvpCount}, #{mvpVotes}, #{totalKills}, #{totalAssists}, #{totalDeaths},
                    #{sourceGoldPerGame}, #{sourceCreepScorePerGame}, #{sourceWardPlacedPerGame},
                    #{sourceWardKilledPerGame}, #{sourceKillParticipantPercent}, #{sourceGoldGapPerGame},
                    #{sourceDamagePercent}, #{sourceGoldPercent}, #{collectedAt})
            """)
    void insertSnapshot(PlayerStageStatWrite stat);
}
