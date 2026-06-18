-- customers テーブルに access_time カラム追加（駅からの所要時間）
ALTER TABLE customers ADD COLUMN IF NOT EXISTS access_time TEXT;

-- 紹介状保存テーブル
CREATE TABLE IF NOT EXISTS introductions (
    id BIGSERIAL PRIMARY KEY,
    ref_no VARCHAR(10) NOT NULL UNIQUE,
    person_id BIGINT,
    customer_id BIGINT,
    intro_date DATE,
    start_date DATE,
    form_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
