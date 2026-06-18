-- ============================================================
-- schema-all.sql
-- 家政婦紹介事務所 人物管理システム 統合スキーマ
-- （schema-update.sql / schema-update-2.sql / schema-update-3.sql / schema-update-4.sql を統合）
-- 初回セットアップ・または差分を一括適用する場合に使用
-- ============================================================

-- ─── personsテーブル拡張 ───────────────────────────────
ALTER TABLE persons ADD COLUMN IF NOT EXISTS line_works BOOLEAN DEFAULT FALSE;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS dispatch_customer_id BIGINT;

-- ─── customersテーブル拡張 ────────────────────────────
ALTER TABLE customers ADD COLUMN IF NOT EXISTS access_info TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS access_time TEXT;

-- ─── sales_detailsテーブル拡張 ───────────────────────
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS work_start_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS work_end_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS reception_fee INTEGER DEFAULT 710;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS customer_fee INTEGER;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS hourly_wage_overtime INTEGER;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wages TEXT;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS remarks TEXT;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS introduction_date DATE;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS receipt_no VARCHAR(10);

-- ─── 稼働管理簿テーブル ───────────────────────────────
CREATE TABLE IF NOT EXISTS working_ledger (
    id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(10) NOT NULL UNIQUE,
    sales_detail_id BIGINT,
    person_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── 受付番号シーケンス管理 ──────────────────────────
CREATE TABLE IF NOT EXISTS receipt_sequence (
    id INT PRIMARY KEY DEFAULT 1,
    current_no INT DEFAULT 0
);
INSERT INTO receipt_sequence (id, current_no) VALUES (1, 0) ON CONFLICT DO NOTHING;

-- ─── 求人受付表 (1-2-1) ──────────────────────────────
CREATE TABLE IF NOT EXISTS customer_requests (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT,
    postal_code VARCHAR(10),
    address TEXT,
    work_address TEXT,
    job_cooking BOOLEAN DEFAULT FALSE,
    job_laundry BOOLEAN DEFAULT FALSE,
    job_cleaning BOOLEAN DEFAULT FALSE,
    job_ironing BOOLEAN DEFAULT FALSE,
    job_babysitting BOOLEAN DEFAULT FALSE,
    job_nursing BOOLEAN DEFAULT FALSE,
    job_other BOOLEAN DEFAULT FALSE,
    job_other_text TEXT,
    freq_type VARCHAR(20),
    freq_temp_date DATE,
    freq_weekly_days TEXT,
    freq_weekly_start TIME,
    freq_weekly_end TIME,
    family_adults INTEGER DEFAULT 0,
    family_children INTEGER DEFAULT 0,
    introducer_name TEXT,
    intro_internet BOOLEAN DEFAULT FALSE,
    intro_townpage BOOLEAN DEFAULT FALSE,
    intro_other BOOLEAN DEFAULT FALSE,
    intro_other_text TEXT,
    pet_none BOOLEAN DEFAULT TRUE,
    pet_dog BOOLEAN DEFAULT FALSE,
    pet_cat BOOLEAN DEFAULT FALSE,
    pet_other BOOLEAN DEFAULT FALSE,
    pet_other_text TEXT,
    remarks TEXT,
    interview_none BOOLEAN DEFAULT TRUE,
    interview_date1 TIMESTAMP,
    interview_date2 TIMESTAMP,
    contact_history TEXT,
    candidate_person_id BIGINT,
    printed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── 求人管理簿 (1-2-2) ──────────────────────────────
CREATE TABLE IF NOT EXISTS customer_ledgers (
    id BIGSERIAL PRIMARY KEY,
    customer_request_id BIGINT,
    customer_id BIGINT,
    job_type VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── 領収書発行テーブル ───────────────────────────────
CREATE TABLE IF NOT EXISTS receipts_issued (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT,
    sales_detail_id BIGINT,
    receipt_type VARCHAR(20),
    amount INTEGER,
    printed BOOLEAN DEFAULT FALSE,
    printed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ─── 紹介状テーブル (1-6) ────────────────────────────
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

-- ─── レジ計算記録テーブル (1-8-1) ────────────────────
CREATE TABLE IF NOT EXISTS register_records (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL,
    work_month VARCHAR(7) NOT NULL,
    salary INTEGER NOT NULL,
    fee INTEGER NOT NULL,
    memo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_register_records_work_month ON register_records(work_month);
CREATE INDEX IF NOT EXISTS idx_register_records_person_id ON register_records(person_id);
