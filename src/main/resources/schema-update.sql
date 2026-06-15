-- personsテーブルにLINE WORKS列追加
ALTER TABLE persons ADD COLUMN IF NOT EXISTS line_works BOOLEAN DEFAULT FALSE;

-- sales_detailsテーブルの拡張
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS work_start_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS work_end_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS reception_fee INTEGER DEFAULT 710;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS customer_fee INTEGER;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS hourly_wage_overtime INTEGER;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wages TEXT;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS remarks TEXT;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS introduction_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS receipt_no VARCHAR(10);

-- salesテーブルの領収書番号を sales_details へ移動するため、
-- salesテーブル自体はそのまま残す（後方互換）

-- 稼働管理簿テーブル（領収書No採番用）
CREATE TABLE IF NOT EXISTS working_ledger (
    id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(10) NOT NULL UNIQUE,
    sales_detail_id BIGINT,
    person_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初期データ（受付番号の最大値追跡用）
CREATE TABLE IF NOT EXISTS receipt_sequence (
    id INT PRIMARY KEY DEFAULT 1,
    current_no INT DEFAULT 0
);
INSERT INTO receipt_sequence (id, current_no) VALUES (1, 0) ON CONFLICT DO NOTHING;
