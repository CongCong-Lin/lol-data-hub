UPDATE champion
SET logo_url = CONCAT('https://', SUBSTRING(logo_url, 8))
WHERE logo_url LIKE 'http://game.gtimg.cn/%';

UPDATE system_state
SET data_version = data_version + 1
WHERE id = 1;
