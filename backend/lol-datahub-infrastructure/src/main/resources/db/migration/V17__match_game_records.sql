CREATE TABLE match_game_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    start_time DATETIME(3) NULL,
    team_a_id BIGINT NOT NULL,
    team_b_id BIGINT NOT NULL,
    win_team_id BIGINT NOT NULL,
    game_duration_seconds BIGINT NOT NULL,
    team_a_kills BIGINT NOT NULL,
    team_a_assists BIGINT NOT NULL,
    team_a_damage DECIMAL(24,8) NOT NULL,
    team_a_gold DECIMAL(24,8) NOT NULL,
    team_a_wards_placed BIGINT NOT NULL,
    team_a_wards_killed BIGINT NOT NULL,
    team_a_minion_kills BIGINT NOT NULL,
    team_a_dragons BIGINT NOT NULL,
    team_a_barons BIGINT NOT NULL,
    team_a_turrets BIGINT NOT NULL,
    team_a_first_blood BOOLEAN NOT NULL,
    team_b_kills BIGINT NOT NULL,
    team_b_assists BIGINT NOT NULL,
    team_b_damage DECIMAL(24,8) NOT NULL,
    team_b_gold DECIMAL(24,8) NOT NULL,
    team_b_wards_placed BIGINT NOT NULL,
    team_b_wards_killed BIGINT NOT NULL,
    team_b_minion_kills BIGINT NOT NULL,
    team_b_dragons BIGINT NOT NULL,
    team_b_barons BIGINT NOT NULL,
    team_b_turrets BIGINT NOT NULL,
    team_b_first_blood BOOLEAN NOT NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_match_id, game_number),
    KEY idx_match_game_stage (source_season_id, source_stage_id),
    KEY idx_match_game_team_a (team_a_id),
    KEY idx_match_game_team_b (team_b_id),
    KEY idx_match_game_start_time (start_time),
    KEY idx_match_game_run (collection_run_id),
    CONSTRAINT fk_match_game_stage
        FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_match_game_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_match_game_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND team_a_id > 0
               AND team_b_id > 0 AND win_team_id > 0 AND game_duration_seconds > 0
               AND team_a_id <> team_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE match_game_player_current (
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    start_time DATETIME(3) NULL,
    source_player_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    position VARCHAR(10) NOT NULL,
    won BOOLEAN NOT NULL,
    kills BIGINT NOT NULL,
    deaths BIGINT NOT NULL,
    assists BIGINT NOT NULL,
    hero_damage DECIMAL(24,8) NOT NULL,
    player_gold DECIMAL(24,8) NOT NULL,
    team_kills BIGINT NOT NULL,
    team_damage DECIMAL(24,8) NOT NULL,
    team_gold DECIMAL(24,8) NOT NULL,
    kill_participant_percent DECIMAL(18,8) NULL,
    damage_percent DECIMAL(18,8) NULL,
    gold_percent DECIMAL(18,8) NULL,
    collection_run_id BIGINT NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (source_season_id, source_stage_id, source_match_id, game_number, source_player_id),
    KEY idx_match_game_player_lookup (source_player_id, position),
    KEY idx_match_game_player_team (source_team_id),
    KEY idx_match_game_player_champion (source_champion_id, position),
    KEY idx_match_game_player_run (collection_run_id),
    CONSTRAINT fk_match_game_player_stage
        FOREIGN KEY (source_season_id, source_stage_id)
        REFERENCES stage (source_season_id, source_stage_id),
    CONSTRAINT fk_match_game_player_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_match_game_player_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND source_player_id > 0
               AND source_team_id > 0 AND source_champion_id > 0
               AND position IN ('TOP', 'JUN', 'MID', 'BOT', 'SUP')
               AND kills >= 0 AND deaths >= 0 AND assists >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE match_game_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    start_time DATETIME(3) NULL,
    team_a_id BIGINT NOT NULL,
    team_b_id BIGINT NOT NULL,
    win_team_id BIGINT NOT NULL,
    game_duration_seconds BIGINT NOT NULL,
    team_a_kills BIGINT NOT NULL,
    team_a_assists BIGINT NOT NULL,
    team_a_damage DECIMAL(24,8) NOT NULL,
    team_a_gold DECIMAL(24,8) NOT NULL,
    team_a_wards_placed BIGINT NOT NULL,
    team_a_wards_killed BIGINT NOT NULL,
    team_a_minion_kills BIGINT NOT NULL,
    team_a_dragons BIGINT NOT NULL,
    team_a_barons BIGINT NOT NULL,
    team_a_turrets BIGINT NOT NULL,
    team_a_first_blood BOOLEAN NOT NULL,
    team_b_kills BIGINT NOT NULL,
    team_b_assists BIGINT NOT NULL,
    team_b_damage DECIMAL(24,8) NOT NULL,
    team_b_gold DECIMAL(24,8) NOT NULL,
    team_b_wards_placed BIGINT NOT NULL,
    team_b_wards_killed BIGINT NOT NULL,
    team_b_minion_kills BIGINT NOT NULL,
    team_b_dragons BIGINT NOT NULL,
    team_b_barons BIGINT NOT NULL,
    team_b_turrets BIGINT NOT NULL,
    team_b_first_blood BOOLEAN NOT NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_match_game_snapshot
        (collection_run_id, source_season_id, source_stage_id, source_match_id, game_number),
    KEY idx_match_game_snapshot_lookup (source_season_id, source_stage_id, start_time),
    CONSTRAINT fk_match_game_snapshot_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_match_game_snapshot_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND team_a_id > 0
               AND team_b_id > 0 AND win_team_id > 0 AND game_duration_seconds > 0
               AND team_a_id <> team_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE match_game_player_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_run_id BIGINT NOT NULL,
    source_season_id BIGINT NOT NULL,
    source_stage_id BIGINT NOT NULL,
    source_match_id BIGINT NOT NULL,
    game_number INT NOT NULL,
    start_time DATETIME(3) NULL,
    source_player_id BIGINT NOT NULL,
    source_team_id BIGINT NOT NULL,
    source_champion_id BIGINT NOT NULL,
    position VARCHAR(10) NOT NULL,
    won BOOLEAN NOT NULL,
    kills BIGINT NOT NULL,
    deaths BIGINT NOT NULL,
    assists BIGINT NOT NULL,
    hero_damage DECIMAL(24,8) NOT NULL,
    player_gold DECIMAL(24,8) NOT NULL,
    team_kills BIGINT NOT NULL,
    team_damage DECIMAL(24,8) NOT NULL,
    team_gold DECIMAL(24,8) NOT NULL,
    kill_participant_percent DECIMAL(18,8) NULL,
    damage_percent DECIMAL(18,8) NULL,
    gold_percent DECIMAL(18,8) NULL,
    collected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_match_game_player_snapshot
        (collection_run_id, source_season_id, source_stage_id, source_match_id, game_number, source_player_id),
    KEY idx_match_game_player_snapshot_lookup (source_player_id, position),
    CONSTRAINT fk_match_game_player_snapshot_run
        FOREIGN KEY (collection_run_id) REFERENCES collection_run (id),
    CONSTRAINT chk_match_game_player_snapshot_ids
        CHECK (source_match_id > 0 AND game_number > 0 AND source_player_id > 0
               AND source_team_id > 0 AND source_champion_id > 0
               AND position IN ('TOP', 'JUN', 'MID', 'BOT', 'SUP')
               AND kills >= 0 AND deaths >= 0 AND assists >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
