CREATE TABLE team_game_lineup_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    source_team_id BIGINT NOT NULL,
    won BOOLEAN NOT NULL,
    top_champion_id BIGINT NOT NULL,
    jungle_champion_id BIGINT NOT NULL,
    mid_champion_id BIGINT NOT NULL,
    bot_champion_id BIGINT NOT NULL,
    support_champion_id BIGINT NOT NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_match_id, game_number, source_team_id),
    KEY idx_team_game_lineup_stage_team (source_season_id, source_stage_id, source_team_id),
    KEY idx_team_game_lineup_mid_jungle (mid_champion_id, jungle_champion_id),
    KEY idx_team_game_lineup_bot_support (bot_champion_id, support_champion_id),
    KEY idx_team_game_lineup_run (collection_run_id),
    CONSTRAINT fk_team_game_lineup_stage
        FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_team_game_lineup_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_team_game_lineup_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND source_team_id > 0
               AND top_champion_id > 0 AND jungle_champion_id > 0
               AND mid_champion_id > 0 AND bot_champion_id > 0
               AND support_champion_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_game_lineup_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    source_team_id BIGINT NOT NULL,
    won BOOLEAN NOT NULL,
    top_champion_id BIGINT NOT NULL,
    jungle_champion_id BIGINT NOT NULL,
    mid_champion_id BIGINT NOT NULL,
    bot_champion_id BIGINT NOT NULL,
    support_champion_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_game_lineup_snapshot
        (collection_run_id, source_stage_id, source_match_id, game_number, source_team_id),
    KEY idx_team_game_lineup_snapshot_lookup
        (source_season_id, source_stage_id, source_team_id, collected_at),
    CONSTRAINT fk_team_game_lineup_snapshot_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_team_game_lineup_snapshot_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND source_team_id > 0
               AND top_champion_id > 0 AND jungle_champion_id > 0
               AND mid_champion_id > 0 AND bot_champion_id > 0
               AND support_champion_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
