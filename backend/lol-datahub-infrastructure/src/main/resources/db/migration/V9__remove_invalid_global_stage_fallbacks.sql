CREATE TEMPORARY TABLE invalid_stage_seasons (
    source_season_id BIGINT NOT NULL PRIMARY KEY
);

-- /schedule/stage 对不受支持的旧赛季会返回 seasonId=0 的全局赛段字典。
-- 旧版本曾把该字典错误挂到请求赛季；正常单赛事远少于 50 个赛段。
INSERT INTO invalid_stage_seasons (source_season_id)
SELECT source_season_id
FROM stage
GROUP BY source_season_id
HAVING COUNT(*) > 50;

DELETE s
FROM stage s
JOIN invalid_stage_seasons invalid
  ON invalid.source_season_id = s.source_season_id
LEFT JOIN stage_stat_current hs
  ON hs.source_season_id = s.source_season_id
 AND hs.source_stage_id = s.source_stage_id
LEFT JOIN champion_stage_stat_current hc
  ON hc.source_season_id = s.source_season_id
 AND hc.source_stage_id = s.source_stage_id
LEFT JOIN team_stage_collection_current tc
  ON tc.source_season_id = s.source_season_id
 AND tc.source_stage_id = s.source_stage_id
LEFT JOIN team_stage_stat_current ts
  ON ts.source_season_id = s.source_season_id
 AND ts.source_stage_id = s.source_stage_id
LEFT JOIN player_stage_collection_current pc
  ON pc.source_season_id = s.source_season_id
 AND pc.source_stage_id = s.source_stage_id
LEFT JOIN player_stage_stat_current ps
  ON ps.source_season_id = s.source_season_id
 AND ps.source_stage_id = s.source_stage_id
WHERE hs.source_stage_id IS NULL
  AND hc.source_stage_id IS NULL
  AND tc.source_stage_id IS NULL
  AND ts.source_stage_id IS NULL
  AND pc.source_stage_id IS NULL
  AND ps.source_stage_id IS NULL;

DROP TEMPORARY TABLE invalid_stage_seasons;
