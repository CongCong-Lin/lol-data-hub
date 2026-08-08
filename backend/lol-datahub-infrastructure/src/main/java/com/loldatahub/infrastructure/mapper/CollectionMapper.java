package com.loldatahub.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
public interface CollectionMapper {
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
            VALUES (#{runId}, #{endpoint}, CAST(#{parametersJson} AS JSON), CAST(#{responseBody} AS JSON),
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

