CREATE TABLE season (
    source_season_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_time DATETIME(3) NULL,
    end_time DATETIME(3) NULL,
    open_status BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (source_season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stage (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_time DATETIME(3) NULL,
    end_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (source_season_id, source_stage_id),
    CONSTRAINT fk_stage_season FOREIGN KEY (source_season_id) REFERENCES season (source_season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE collection_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_type VARCHAR(40) NOT NULL,
    source_season_id BIGINT NULL,
    requested_stage_ids JSON NULL,
    status VARCHAR(30) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    changed_records INT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    KEY idx_collection_run_started_at (started_at),
    KEY idx_collection_run_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE source_raw_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    endpoint VARCHAR(300) NOT NULL,
    request_parameters JSON NOT NULL,
    response_body JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_raw_response_run (collection_run_id),
    KEY idx_raw_response_hash (content_hash),
    CONSTRAINT fk_raw_response_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE champion (
    source_champion_id BIGINT NOT NULL,
    internal_name VARCHAR(100) NULL,
    chinese_name VARCHAR(100) NOT NULL,
    chinese_title VARCHAR(100) NULL,
    logo_url VARCHAR(1000) NULL,
    positions_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (source_champion_id),
    KEY idx_champion_chinese_name (chinese_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stage_stat_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    sample_base_count BIGINT NOT NULL,
    game_versions_json JSON NULL,
    source_updated_at DATETIME(3) NULL,
    content_hash CHAR(64) NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    collection_run_id BIGINT NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id),
    CONSTRAINT fk_stage_stat_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_stage_stat_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE champion_stage_stat_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    pick_count BIGINT NOT NULL,
    ban_count BIGINT NOT NULL,
    bp_count BIGINT NOT NULL,
    winning_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    source_pick_rate DECIMAL(12,8) NULL,
    source_ban_rate DECIMAL(12,8) NULL,
    source_bp_rate DECIMAL(12,8) NULL,
    source_winning_rate DECIMAL(12,8) NULL,
    most_used_player_id BIGINT NULL,
    most_used_player_name VARCHAR(100) NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_champion_id),
    KEY idx_champion_stat_champion (source_champion_id),
    CONSTRAINT fk_champion_stat_champion FOREIGN KEY (source_champion_id) REFERENCES champion (source_champion_id),
    CONSTRAINT fk_champion_stat_stage FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_champion_stat_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE champion_stage_stat_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    pick_count BIGINT NOT NULL,
    ban_count BIGINT NOT NULL,
    bp_count BIGINT NOT NULL,
    winning_count BIGINT NOT NULL,
    total_kills BIGINT NOT NULL,
    total_deaths BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_champion_snapshot_run (collection_run_id, source_stage_id, source_champion_id),
    KEY idx_champion_snapshot_lookup (source_season_id, source_stage_id, source_champion_id, collected_at),
    CONSTRAINT fk_champion_snapshot_run FOREIGN KEY (collection_run_id) REFERENCES collection_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE system_state (
    id TINYINT NOT NULL,
    data_version BIGINT NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO system_state (id, data_version) VALUES (1, 1);

