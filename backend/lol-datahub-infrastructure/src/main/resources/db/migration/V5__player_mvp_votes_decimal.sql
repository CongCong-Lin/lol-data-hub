ALTER TABLE player_stage_stat_current
    MODIFY COLUMN mvp_votes DECIMAL(18,8) NOT NULL DEFAULT 0;

ALTER TABLE player_stage_stat_snapshot
    MODIFY COLUMN mvp_votes DECIMAL(18,8) NOT NULL DEFAULT 0;
