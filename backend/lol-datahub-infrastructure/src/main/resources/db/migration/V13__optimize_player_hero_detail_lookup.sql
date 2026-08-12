-- 选手详情页按 (选手, 位置, 复合赛段) 高频查询逐局英雄明细。
-- 现有主键以 (赛季, 赛段, 英雄) 开头，无法服务以 source_player_id 为前导的查询。
-- 本迁移只新增二级索引，不改动任何现有数据。
CREATE INDEX idx_champion_position_player_detail
ON champion_position_player_stage_stat_current
(
    source_player_id,
    position,
    source_season_id,
    source_stage_id,
    source_champion_id
);
