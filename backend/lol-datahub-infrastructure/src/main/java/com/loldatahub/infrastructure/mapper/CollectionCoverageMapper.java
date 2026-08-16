package com.loldatahub.infrastructure.mapper;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.model.StageGameCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采集覆盖矩阵读取：各数据类型 current 表的已采集赛段集合与对局数量。
 */
@Mapper
public interface CollectionCoverageMapper {
    @Select("""
            SELECT DISTINCT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM stage_stat_current
            """)
    List<StageKey> findHeroCollectedStages();

    @Select("""
            SELECT DISTINCT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM team_stage_collection_current
            """)
    List<StageKey> findTeamCollectedStages();

    @Select("""
            SELECT DISTINCT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId
              FROM player_stage_collection_current
            """)
    List<StageKey> findPlayerCollectedStages();

    @Select("""
            SELECT source_season_id AS sourceSeasonId, source_stage_id AS sourceStageId,
                   COUNT(*) AS games
              FROM match_game_current
             GROUP BY source_season_id, source_stage_id
            """)
    List<StageGameCountRow> countMatchGamesByStage();
}
