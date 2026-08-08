CREATE TABLE team (
    source_team_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    logo_url VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (source_team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_stage_collection_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    collection_run_id BIGINT NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id),
    CONSTRAINT fk_team_coll_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_team_coll_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_stage_stat_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    match_count BIGINT NOT NULL,
    match_win_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    source_ward_placed_per_game DECIMAL(18,8) NULL,
    source_ward_killed_per_game DECIMAL(18,8) NULL,
    source_gold_per_game DECIMAL(18,8) NULL,
    source_baron_kill_per_game DECIMAL(18,8) NULL,
    source_drake_kill_per_game DECIMAL(18,8) NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_team_id),
    CONSTRAINT fk_team_stat_team FOREIGN KEY (source_team_id) REFERENCES team (source_team_id),
    CONSTRAINT fk_team_stat_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_team_stat_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_stage_stat_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    match_count BIGINT NOT NULL,
    match_win_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    source_ward_placed_per_game DECIMAL(18,8) NULL,
    source_ward_killed_per_game DECIMAL(18,8) NULL,
    source_gold_per_game DECIMAL(18,8) NULL,
    source_baron_kill_per_game DECIMAL(18,8) NULL,
    source_drake_kill_per_game DECIMAL(18,8) NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_snapshot_run (collection_run_id, source_stage_id, source_team_id),
    KEY idx_team_snapshot_lookup (source_season_id, source_stage_id, source_team_id, collected_at),
    CONSTRAINT fk_team_snapshot_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
