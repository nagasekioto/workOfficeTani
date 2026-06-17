-- customers テーブルに access_time カラム追加（駅からの所要時間）
ALTER TABLE customers ADD COLUMN IF NOT EXISTS access_time TEXT;
