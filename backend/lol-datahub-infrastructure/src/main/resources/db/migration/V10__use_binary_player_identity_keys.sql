ALTER TABLE player_stage_stat_current
    DROP FOREIGN KEY fk_player_stat_player;

ALTER TABLE player
    MODIFY COLUMN player_key VARCHAR(220)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE player_stage_stat_current
    MODIFY COLUMN player_key VARCHAR(220)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE player_stage_stat_snapshot
    MODIFY COLUMN player_key VARCHAR(220)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE player_stage_stat_current
    ADD CONSTRAINT fk_player_stat_player
        FOREIGN KEY (player_key) REFERENCES player (player_key);
