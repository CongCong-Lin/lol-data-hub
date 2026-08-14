ALTER TABLE team_stage_detail_metric_current
    ADD COLUMN total_game_seconds BIGINT NULL AFTER total_damage,
    ADD COLUMN total_gold DECIMAL(24,8) NULL AFTER total_game_seconds,
    ADD COLUMN total_wards_placed BIGINT NULL AFTER total_gold,
    ADD COLUMN total_wards_killed BIGINT NULL AFTER total_wards_placed,
    ADD COLUMN total_minion_kills BIGINT NULL AFTER total_wards_killed,
    ADD COLUMN total_dragons BIGINT NULL AFTER total_minion_kills,
    ADD COLUMN total_dragon_opportunities BIGINT NULL AFTER total_dragons,
    ADD COLUMN total_barons BIGINT NULL AFTER total_dragon_opportunities,
    ADD COLUMN total_baron_opportunities BIGINT NULL AFTER total_barons,
    ADD COLUMN total_turrets BIGINT NULL AFTER total_baron_opportunities,
    ADD COLUMN total_turrets_lost BIGINT NULL AFTER total_turrets,
    ADD COLUMN first_blood_games BIGINT NULL AFTER total_turrets_lost;

ALTER TABLE team_stage_detail_metric_snapshot
    ADD COLUMN total_game_seconds BIGINT NULL AFTER total_damage,
    ADD COLUMN total_gold DECIMAL(24,8) NULL AFTER total_game_seconds,
    ADD COLUMN total_wards_placed BIGINT NULL AFTER total_gold,
    ADD COLUMN total_wards_killed BIGINT NULL AFTER total_wards_placed,
    ADD COLUMN total_minion_kills BIGINT NULL AFTER total_wards_killed,
    ADD COLUMN total_dragons BIGINT NULL AFTER total_minion_kills,
    ADD COLUMN total_dragon_opportunities BIGINT NULL AFTER total_dragons,
    ADD COLUMN total_barons BIGINT NULL AFTER total_dragon_opportunities,
    ADD COLUMN total_baron_opportunities BIGINT NULL AFTER total_barons,
    ADD COLUMN total_turrets BIGINT NULL AFTER total_baron_opportunities,
    ADD COLUMN total_turrets_lost BIGINT NULL AFTER total_turrets,
    ADD COLUMN first_blood_games BIGINT NULL AFTER total_turrets_lost;
