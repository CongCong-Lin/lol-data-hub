CREATE TABLE player (
    player_key VARCHAR(220) NOT NULL,
    source_player_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_key),
    KEY idx_player_source_id (source_player_id),
    KEY idx_player_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE player_stage_collection_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    collection_run_id BIGINT NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id),
    CONSTRAINT fk_player_coll_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_player_coll_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE player_stage_stat_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    player_key VARCHAR(220) NOT NULL,
    team_name VARCHAR(200) NULL,
    team_logo VARCHAR(1000) NULL,
    player_position VARCHAR(20) NULL,
    match_count BIGINT NOT NULL,
    mvp_count BIGINT NOT NULL,
    mvp_votes BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    source_gold_per_game DECIMAL(18,8) NULL,
    source_creep_score_per_game DECIMAL(18,8) NULL,
    source_ward_placed_per_game DECIMAL(18,8) NULL,
    source_ward_killed_per_game DECIMAL(18,8) NULL,
    source_kill_participant_percent DECIMAL(18,8) NULL,
    source_gold_gap_per_game DECIMAL(18,8) NULL,
    source_damage_percent DECIMAL(18,8) NULL,
    source_gold_percent DECIMAL(18,8) NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, player_key),
    CONSTRAINT fk_player_stat_player FOREIGN KEY (player_key) REFERENCES player (player_key),
    CONSTRAINT fk_player_stat_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_player_stat_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE player_stage_stat_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    player_key VARCHAR(220) NOT NULL,
    team_name VARCHAR(200) NULL,
    team_logo VARCHAR(1000) NULL,
    player_position VARCHAR(20) NULL,
    match_count BIGINT NOT NULL,
    mvp_count BIGINT NOT NULL,
    mvp_votes BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    source_gold_per_game DECIMAL(18,8) NULL,
    source_creep_score_per_game DECIMAL(18,8) NULL,
    source_ward_placed_per_game DECIMAL(18,8) NULL,
    source_ward_killed_per_game DECIMAL(18,8) NULL,
    source_kill_participant_percent DECIMAL(18,8) NULL,
    source_gold_gap_per_game DECIMAL(18,8) NULL,
    source_damage_percent DECIMAL(18,8) NULL,
    source_gold_percent DECIMAL(18,8) NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_snapshot_run (collection_run_id, source_stage_id, player_key),
    KEY idx_player_snapshot_lookup (source_season_id, source_stage_id, player_key, collected_at),
    CONSTRAINT fk_player_snapshot_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
