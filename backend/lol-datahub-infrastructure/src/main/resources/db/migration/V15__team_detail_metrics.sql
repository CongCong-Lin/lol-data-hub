CREATE TABLE team_stage_detail_metric_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    game_count BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    total_damage DECIMAL(24,8) NOT NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_team_id),
    KEY idx_team_detail_metric_run (collection_run_id),
    CONSTRAINT fk_team_detail_metric_stage
        FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_team_detail_metric_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_team_detail_metric_values
        CHECK (source_team_id > 0 AND game_count > 0 AND total_assists >= 0 AND total_damage >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_stage_detail_metric_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    game_count BIGINT NOT NULL,
    total_assists BIGINT NOT NULL,
    total_damage DECIMAL(24,8) NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_detail_metric_snapshot
        (collection_run_id, source_stage_id, source_team_id),
    KEY idx_team_detail_metric_snapshot_lookup
        (source_season_id, source_stage_id, source_team_id, collected_at),
    CONSTRAINT fk_team_detail_metric_snapshot_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_team_detail_metric_snapshot_values
        CHECK (source_team_id > 0 AND game_count > 0 AND total_assists >= 0 AND total_damage >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
