ALTER TABLE champion_stage_stat_current
    ADD COLUMN positions_json JSON NULL AFTER most_used_player_name;

ALTER TABLE champion_stage_stat_snapshot
    ADD COLUMN positions_json JSON NULL AFTER total_assists;

UPDATE champion_stage_stat_current cs
JOIN champion c ON c.source_champion_id = cs.source_champion_id
SET cs.positions_json = c.positions_json
WHERE c.positions_json IS NOT NULL;

UPDATE champion_stage_stat_snapshot ss
JOIN champion c ON c.source_champion_id = ss.source_champion_id
SET ss.positions_json = c.positions_json
WHERE c.positions_json IS NOT NULL;
