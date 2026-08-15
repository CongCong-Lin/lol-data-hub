package com.loldatahub.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CollectionMapper {
    @Select("""
            SELECT r.response_body
            FROM source_raw_response r
            JOIN (
                SELECT JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.matchId')) AS match_id,
                       MAX(id) AS latest_id
                FROM source_raw_response
                WHERE endpoint = '/compound/matchDetail'
                  AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.seasonId')) = CAST(#{seasonId} AS CHAR)
                  AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.stageIds')) = CAST(#{stageId} AS CHAR)
                GROUP BY JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.matchId'))
            ) latest ON latest.latest_id = r.id
            ORDER BY r.id
            """)
    List<String> findMatchDetailResponses(@Param("seasonId") long seasonId,
                                          @Param("stageId") long stageId);

    /**
     * 返回指定赛段每个 matchId 最近一次保存的 matchDetail 响应（含 matchId 与响应 ID），
     * 供对局明细回填使用。
     */
    @Select("""
            SELECT r.id AS id,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(r.request_parameters, '$.matchId')) AS UNSIGNED) AS matchId,
                   r.response_body AS body
            FROM source_raw_response r
            JOIN (
                SELECT JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.matchId')) AS match_id,
                       MAX(id) AS latest_id
                FROM source_raw_response
                WHERE endpoint = '/compound/matchDetail'
                  AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.seasonId')) = CAST(#{seasonId} AS CHAR)
                  AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.stageIds')) = CAST(#{stageId} AS CHAR)
                GROUP BY JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.matchId'))
            ) latest ON latest.latest_id = r.id
            ORDER BY r.id
            """)
    List<com.loldatahub.infrastructure.model.MatchDetailSourceRow> findLatestMatchDetails(
            @Param("seasonId") long seasonId,
            @Param("stageId") long stageId);

    /** 返回指定赛段和选手最近一次保存的逐局英雄记录。 */
    @Select("""
            SELECT response_body
            FROM source_raw_response
            WHERE endpoint = '/compound/heroRecord'
              AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.seasonId')) = CAST(#{seasonId} AS CHAR)
              AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.stageIds')) = CAST(#{stageId} AS CHAR)
              AND JSON_UNQUOTE(JSON_EXTRACT(request_parameters, '$.playerId')) = CAST(#{playerId} AS CHAR)
            ORDER BY id DESC
            LIMIT 1
            """)
    String findPlayerHeroRecordResponse(@Param("seasonId") long seasonId,
                                        @Param("stageId") long stageId,
                                        @Param("playerId") long playerId);

    @Insert("""
            INSERT INTO collection_run (collection_type, source_season_id, requested_stage_ids, status, started_at)
            VALUES (#{type}, #{seasonId}, CAST(#{stageIdsJson} AS JSON), 'RUNNING', #{startedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "holder.id")
    void insertRun(@Param("type") String type,
                   @Param("seasonId") Long seasonId,
                   @Param("stageIdsJson") String stageIdsJson,
                   @Param("startedAt") OffsetDateTime startedAt,
                   @Param("holder") GeneratedId holder);

    @Update("""
            UPDATE collection_run SET status = #{status}, finished_at = #{finishedAt},
                changed_records = #{changedRecords}, error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    void finishRun(@Param("id") long id,
                   @Param("status") String status,
                   @Param("finishedAt") OffsetDateTime finishedAt,
                   @Param("changedRecords") int changedRecords,
                   @Param("errorMessage") String errorMessage);

    @Insert("""
            INSERT INTO source_raw_response
                (collection_run_id, endpoint, request_parameters, response_body, content_hash, collected_at)
            VALUES (#{runId}, #{endpoint}, CAST(#{parametersJson} AS JSON), #{responseBody},
                    #{contentHash}, #{collectedAt})
            """)
    void insertRawResponse(@Param("runId") long runId,
                           @Param("endpoint") String endpoint,
                           @Param("parametersJson") String parametersJson,
                           @Param("responseBody") String responseBody,
                           @Param("contentHash") String contentHash,
                           @Param("collectedAt") OffsetDateTime collectedAt);

    @Select("""
            SELECT content_hash FROM stage_stat_current
            WHERE source_season_id = #{seasonId} AND source_stage_id = #{stageId}
            """)
    String findCurrentContentHash(@Param("seasonId") long seasonId, @Param("stageId") long stageId);

    @Update("""
            UPDATE collection_run
            SET status = 'FAILED',
                finished_at = UTC_TIMESTAMP(3),
                error_message = '应用启动时回收超过 30 分钟的悬挂采集任务'
            WHERE status = 'RUNNING'
              AND started_at < UTC_TIMESTAMP(3) - INTERVAL 30 MINUTE
            """)
    int recoverStaleRunningRuns();

    @Select("""
            SELECT id, collection_type AS collectionType,
                   source_season_id AS sourceSeasonId,
                   CAST(requested_stage_ids AS CHAR) AS requestedStageIds,
                   status, started_at AS startedAt, finished_at AS finishedAt,
                   changed_records AS changedRecords, error_message AS errorMessage
              FROM collection_run
             ORDER BY id DESC
             LIMIT #{limit}
            """)
    List<com.loldatahub.infrastructure.model.CollectionStatusRow> findRecentRuns(
            @Param("limit") int limit);

    final class GeneratedId {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
