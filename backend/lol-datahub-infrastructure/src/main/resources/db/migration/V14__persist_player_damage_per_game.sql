ALTER TABLE player_stage_stat_current
    ADD COLUMN source_damage_per_game DECIMAL(18,8) NULL AFTER source_gold_gap_per_game;

ALTER TABLE player_stage_stat_snapshot
    ADD COLUMN source_damage_per_game DECIMAL(18,8) NULL AFTER source_gold_gap_per_game;
