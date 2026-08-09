CREATE TABLE champion_position_player_stage_stat_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    position VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_player_id BIGINT NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    pick_count BIGINT NOT NULL,
    winning_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_champion_id, position, source_player_id),
    KEY idx_champion_position_lookup (source_champion_id, position),
    KEY idx_champion_position_run (collection_run_id),
    CONSTRAINT fk_champion_position_current_champion
        FOREIGN KEY (source_champion_id) REFERENCES champion (source_champion_id),
    CONSTRAINT fk_champion_position_current_stage
        FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_champion_position_current_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_champion_position_current_position
        CHECK (position IN ('TOP', 'JUN', 'MID', 'BOT', 'SUP')),
    CONSTRAINT chk_champion_position_current_counts
        CHECK (pick_count > 0 AND winning_count >= 0 AND winning_count <= pick_count
               AND total_kills >= 0 AND total_deaths >= 0 AND total_assists >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE champion_position_player_stage_stat_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    position VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_player_id BIGINT NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    pick_count BIGINT NOT NULL,
    winning_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_champion_position_snapshot_run
        (collection_run_id, source_stage_id, source_champion_id, position, source_player_id),
    KEY idx_champion_position_snapshot_lookup
        (source_season_id, source_stage_id, source_champion_id, position, collected_at),
    CONSTRAINT fk_champion_position_snapshot_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_champion_position_snapshot_position
        CHECK (position IN ('TOP', 'JUN', 'MID', 'BOT', 'SUP')),
    CONSTRAINT chk_champion_position_snapshot_counts
        CHECK (pick_count > 0 AND winning_count >= 0 AND winning_count <= pick_count
               AND total_kills >= 0 AND total_deaths >= 0 AND total_assists >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
