package com.loldatahub.infrastructure.mapper;

import com.loldatahub.infrastructure.model.ChampionStageStatWrite;
import com.loldatahub.infrastructure.model.ChampionWrite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.OffsetDateTime;

@Mapper
public interface ChampionStatWriteMapper {
    @Delete("""
            DELETE FROM champion_stage_stat_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    int deleteCurrentForStage(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Insert("""
            INSERT INTO champion
                (source_champion_id, internal_name, chinese_name, chinese_title, logo_url, positions_json)
            VALUES (#{championId}, #{internalName}, #{chineseName}, #{chineseTitle}, #{logoUrl},
                    CAST(#{positionsJson} AS JSON))
            ON DUPLICATE KEY UPDATE internal_name = VALUES(internal_name), chinese_name = VALUES(chinese_name),
                chinese_title = VALUES(chinese_title), logo_url = VALUES(logo_url),
                positions_json = VALUES(positions_json)
            """)
    void upsertChampion(ChampionWrite champion);

    @Insert("""
            INSERT INTO stage_stat_current
                (source_season_id, source_stage_id, sample_base_count, game_versions_json,
                 source_updated_at, content_hash, collected_at, collection_run_id)
            VALUES (#{seasonId}, #{stageId}, #{sampleBaseCount}, CAST(#{gameVersionsJson} AS JSON),
                    #{sourceUpdatedAt}, #{contentHash}, #{collectedAt}, #{runId})
            ON DUPLICATE KEY UPDATE sample_base_count = VALUES(sample_base_count),
                game_versions_json = VALUES(game_versions_json), source_updated_at = VALUES(source_updated_at),
                content_hash = VALUES(content_hash), collected_at = VALUES(collected_at),
                collection_run_id = VALUES(collection_run_id)
            """)
    void upsertStageCurrent(@Param("seasonId") long seasonId,
                            @Param("stageId") long stageId,
                            @Param("sampleBaseCount") long sampleBaseCount,
                            @Param("gameVersionsJson") String gameVersionsJson,
                            @Param("sourceUpdatedAt") OffsetDateTime sourceUpdatedAt,
                            @Param("contentHash") String contentHash,
                            @Param("collectedAt") OffsetDateTime collectedAt,
                            @Param("runId") long runId);

    @Insert("""
            INSERT INTO champion_stage_stat_current
                (source_season_id, source_stage_id, source_champion_id, pick_count, ban_count, bp_count,
                 winning_count, total_kills, total_deaths, total_assists, source_pick_rate,
                 source_ban_rate, source_bp_rate, source_winning_rate, most_used_player_id,
                 most_used_player_name, collection_run_id, collected_at)
            VALUES (#{seasonId}, #{stageId}, #{championId}, #{pickCount}, #{banCount}, #{bpCount},
                    #{winningCount}, #{totalKills}, #{totalDeaths}, #{totalAssists}, #{sourcePickRate},
                    #{sourceBanRate}, #{sourceBpRate}, #{sourceWinningRate}, #{mostUsedPlayerId},
                    #{mostUsedPlayerName}, #{runId}, #{collectedAt})
            ON DUPLICATE KEY UPDATE pick_count = VALUES(pick_count), ban_count = VALUES(ban_count),
                bp_count = VALUES(bp_count), winning_count = VALUES(winning_count),
                total_kills = VALUES(total_kills), total_deaths = VALUES(total_deaths),
                total_assists = VALUES(total_assists), source_pick_rate = VALUES(source_pick_rate),
                source_ban_rate = VALUES(source_ban_rate), source_bp_rate = VALUES(source_bp_rate),
                source_winning_rate = VALUES(source_winning_rate),
                most_used_player_id = VALUES(most_used_player_id),
                most_used_player_name = VALUES(most_used_player_name),
                collection_run_id = VALUES(collection_run_id), collected_at = VALUES(collected_at)
            """)
    void upsertCurrent(ChampionStageStatWrite stat);

    @Insert("""
            INSERT INTO champion_stage_stat_snapshot
                (collection_run_id, source_season_id, source_stage_id, source_champion_id,
                 pick_count, ban_count, bp_count, winning_count, total_kills, total_deaths,
                 total_assists, collected_at)
            VALUES (#{runId}, #{seasonId}, #{stageId}, #{championId}, #{pickCount}, #{banCount},
                    #{bpCount}, #{winningCount}, #{totalKills}, #{totalDeaths}, #{totalAssists}, #{collectedAt})
            """)
    void insertSnapshot(ChampionStageStatWrite stat);
}
