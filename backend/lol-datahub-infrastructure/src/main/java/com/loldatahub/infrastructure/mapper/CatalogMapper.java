package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.domain.catalog.Stage;
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
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId,
                   name, start_time AS startTime, end_time AS endTime
            FROM stage WHERE source_season_id = #{seasonId}
            ORDER BY start_time, source_stage_id
            """)
    List<Stage> findStages(@Param("seasonId") long seasonId);
}

