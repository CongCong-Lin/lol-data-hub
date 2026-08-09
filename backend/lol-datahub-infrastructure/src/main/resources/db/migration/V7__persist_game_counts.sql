ALTER TABLE team_stage_stat_current
    ADD COLUMN game_count BIGINT NULL AFTER match_count;

ALTER TABLE team_stage_stat_snapshot
    ADD COLUMN game_count BIGINT NULL AFTER match_count;

ALTER TABLE player_stage_stat_current
    ADD COLUMN game_count BIGINT NULL AFTER match_count;

ALTER TABLE player_stage_stat_snapshot
    ADD COLUMN game_count BIGINT NULL AFTER match_count;

-- 旧数据没有局数。将采集指纹置为不可能的值，确保下一次采集会重写这些赛段，
-- 而不是因为官网响应内容未变化而误判为 NO_CHANGE。
UPDATE team_stage_collection_current
SET content_hash = REPEAT('0', 64);

UPDATE player_stage_collection_current
SET content_hash = REPEAT('0', 64);
