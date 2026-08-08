package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.domain.catalog.Stage;
import com.loldatahub.infrastructure.model.StageAvailabilityRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CatalogMapper {
    @Insert("""
            INSERT INTO season (source_season_id, name, start_time, end_time, open_status)
            VALUES (#{sourceSeasonId}, #{name}, #{startTime}, #{endTime}, #{open})
            ON DUPLICATE KEY UPDATE name = VALUES(name), start_time = VALUES(start_time),
                end_time = VALUES(end_time), open_status = VALUES(open_status)
            """)
    void upsertSeason(Season season);

    @Insert("""
            INSERT INTO stage (source_season_id, source_stage_id, name, start_time, end_time)
            VALUES (#{sourceSeasonId}, #{sourceStageId}, #{name}, #{startTime}, #{endTime})
            ON DUPLICATE KEY UPDATE name = VALUES(name), start_time = VALUES(start_time), end_time = VALUES(end_time)
            """)
    void upsertStage(Stage stage);

    @Select("""
            SELECT source_season_id AS sourceSeasonId, name, start_time AS startTime,
                   end_time AS endTime, open_status AS open
            FROM season ORDER BY start_time DESC, source_season_id DESC
            """)
    List<Season> findSeasons();

    @Select("""
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN ss.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   ss.sample_base_count AS sampleBaseCount,
                   ss.collected_at AS collectedAt
            FROM stage s
            LEFT JOIN stage_stat_current ss
              ON ss.source_season_id = s.source_season_id
             AND ss.source_stage_id = s.source_stage_id
            WHERE s.source_season_id = #{seasonId}
            ORDER BY s.start_time, s.source_stage_id
            """)
    List<StageAvailabilityRow> findHeroStageAvailability(@Param("seasonId") long seasonId);

    @Select("""
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN tc.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   NULL AS sampleBaseCount,
                   tc.collected_at AS collectedAt
            FROM stage s
            LEFT JOIN team_stage_collection_current tc
              ON tc.source_season_id = s.source_season_id
             AND tc.source_stage_id = s.source_stage_id
            WHERE s.source_season_id = #{seasonId}
            ORDER BY s.start_time, s.source_stage_id
            """)
    List<StageAvailabilityRow> findTeamStageAvailability(@Param("seasonId") long seasonId);
}
