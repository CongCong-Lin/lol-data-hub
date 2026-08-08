package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.domain.catalog.Stage;
import com.loldatahub.infrastructure.model.CrossSeasonStageAvailabilityRow;
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

    @Select("""
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN pc.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   NULL AS sampleBaseCount,
                   pc.collected_at AS collectedAt
            FROM stage s
            LEFT JOIN player_stage_collection_current pc
              ON pc.source_season_id = s.source_season_id
             AND pc.source_stage_id = s.source_stage_id
            WHERE s.source_season_id = #{seasonId}
            ORDER BY s.start_time, s.source_stage_id
            """)
    List<StageAvailabilityRow> findPlayerStageAvailability(@Param("seasonId") long seasonId);

    // ── 跨赛事目录可用性（全部赛季） ─────────────────────────────

    @Select("""
            <script>
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   se.name AS seasonName,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN ss.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   ss.sample_base_count AS sampleBaseCount,
                   ss.collected_at AS collectedAt
            FROM stage s
            JOIN season se ON se.source_season_id = s.source_season_id
            LEFT JOIN stage_stat_current ss
              ON ss.source_season_id = s.source_season_id
             AND ss.source_stage_id = s.source_stage_id
            <if test="collectedOnly">
            WHERE ss.source_stage_id IS NOT NULL
            </if>
            ORDER BY se.start_time DESC, se.source_season_id DESC, s.start_time, s.source_stage_id
            </script>
            """)
    List<CrossSeasonStageAvailabilityRow> findAllHeroStageAvailability(
            @Param("collectedOnly") boolean collectedOnly);

    @Select("""
            <script>
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   se.name AS seasonName,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN tc.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   NULL AS sampleBaseCount,
                   tc.collected_at AS collectedAt
            FROM stage s
            JOIN season se ON se.source_season_id = s.source_season_id
            LEFT JOIN team_stage_collection_current tc
              ON tc.source_season_id = s.source_season_id
             AND tc.source_stage_id = s.source_stage_id
            <if test="collectedOnly">
            WHERE tc.source_stage_id IS NOT NULL
            </if>
            ORDER BY se.start_time DESC, se.source_season_id DESC, s.start_time, s.source_stage_id
            </script>
            """)
    List<CrossSeasonStageAvailabilityRow> findAllTeamStageAvailability(
            @Param("collectedOnly") boolean collectedOnly);

    @Select("""
            <script>
            SELECT s.source_season_id AS sourceSeasonId,
                   s.source_stage_id AS sourceStageId,
                   se.name AS seasonName,
                   s.name,
                   s.start_time AS startTime,
                   s.end_time AS endTime,
                   CASE WHEN pc.source_stage_id IS NULL THEN FALSE ELSE TRUE END AS collected,
                   NULL AS sampleBaseCount,
                   pc.collected_at AS collectedAt
            FROM stage s
            JOIN season se ON se.source_season_id = s.source_season_id
            LEFT JOIN player_stage_collection_current pc
              ON pc.source_season_id = s.source_season_id
             AND pc.source_stage_id = s.source_stage_id
            <if test="collectedOnly">
            WHERE pc.source_stage_id IS NOT NULL
            </if>
            ORDER BY se.start_time DESC, se.source_season_id DESC, s.start_time, s.source_stage_id
            </script>
            """)
    List<CrossSeasonStageAvailabilityRow> findAllPlayerStageAvailability(
            @Param("collectedOnly") boolean collectedOnly);
}
