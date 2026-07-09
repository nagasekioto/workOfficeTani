-- ============================================================
-- schema-all.sql
-- 家政婦紹介事務所 人物管理システム 統合スキーマ
-- schema-update.sql 〜 schema-update-11.sql の内容をすべて統合したもの。
-- 初回セットアップ・または差分を一括適用する場合はこのファイルのみ実行すればよい。
-- （個別のschema-update-N.sqlファイルは廃止し、本ファイルに一本化した）
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

-- schema-update-5: receipts_issued テーブルに領収番号（自動採番）・求職者IDカラムを追加
ALTER TABLE receipts_issued ADD COLUMN IF NOT EXISTS receipt_number INTEGER;
ALTER TABLE receipts_issued ADD COLUMN IF NOT EXISTS person_id BIGINT;
UPDATE receipts_issued SET receipt_number = id WHERE receipt_number IS NULL;
CREATE INDEX IF NOT EXISTS idx_receipts_issued_month ON receipts_issued (TO_CHAR(created_at, 'YYYY-MM'));
CREATE INDEX IF NOT EXISTS idx_receipts_issued_detail ON receipts_issued (sales_detail_id);

-- schema-update-6: personsテーブル拡張（就職希望条件）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_location TEXT;          -- 就労場所（複数可：カンマ区切り）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_duties TEXT;           -- 職務内容（複数可：カンマ区切り）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS desired_types TEXT;         -- 希望形態（複数可）※既存desired_typeは保持
ALTER TABLE persons ADD COLUMN IF NOT EXISTS specific_days TEXT;         -- 特定日ごとの希望時間(JSON)
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_available_hours TEXT;  -- 就業可能時間
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_start_period TEXT;     -- 労働開始時期

-- schema-update-7: sales_detailsテーブルに発行日時カラムを追加
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP;
-- 既存の発行済みデータ（receipt_noがあるもの）は introduction_date を発行日として使う
UPDATE sales_details
SET issued_at = introduction_date::timestamp
WHERE receipt_no IS NOT NULL
  AND receipt_no <> ''
  AND issued_at IS NULL
  AND introduction_date IS NOT NULL;

-- schema-update-8: 紹介手数料管理簿(1-3-1)拡張・手数料収入決算表(1-3-3)用
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wage_1month INTEGER DEFAULT 0;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS temp_3month INTEGER DEFAULT 0;

CREATE TABLE IF NOT EXISTS sancare_net_monthly (
    id          BIGSERIAL PRIMARY KEY,
    year_month  VARCHAR(7) NOT NULL,
    amount      INTEGER    NOT NULL DEFAULT 0,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (year_month)
);

-- schema-update-9: 売上入力(1-1-3)拡張（売上・日給掛け率）
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wage_rate NUMERIC(5,2) DEFAULT 16.5;
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS sales_amount INTEGER DEFAULT 0;
UPDATE sales_details SET daily_wage_rate = 16.5 WHERE daily_wage_rate IS NULL;

-- schema-update-10: 求職者(1-1-6)・求人者(1-2-3)の削除→退職/取引終了への変更
ALTER TABLE persons   ADD COLUMN IF NOT EXISTS retired_at DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS retired_at DATE;

-- schema-update-11: 領収書番号専用の採番カウンターテーブル
CREATE TABLE IF NOT EXISTS receipt_no_counter (
    id      SMALLINT PRIMARY KEY DEFAULT 1,
    next_no INTEGER NOT NULL,
    CONSTRAINT receipt_no_counter_single_row CHECK (id = 1)
);
INSERT INTO receipt_no_counter (id, next_no)
SELECT 1, GREATEST(
    COALESCE((SELECT MAX(CAST(receipt_no AS INTEGER)) FROM sales_details WHERE receipt_no ~ '^[0-9]+$'), 0),
    COALESCE((SELECT MAX(CAST(SUBSTRING(ledger_remarks FROM 6) AS INTEGER)) FROM introductions WHERE ledger_remarks ~ '^RCPT:[0-9]+$'), 0)
) + 1
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 以下は DatabaseMigrationRunner.java が起動時に自動実行している内容。
-- そちらは今後もアプリ起動のたびに自動で当たるためDB運用上の実害はないが、
-- 「実際のスキーマの全体像」をこのファイル1つで把握できるよう、
-- ドキュメントとして統合しておく（IF NOT EXISTSなので重複実行しても安全）。
-- ============================================================

-- persons テーブル拡張（緊急連絡先・備考・会費 1-1-7）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emergency_relation TEXT;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS emergency_phone TEXT;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS babysitter_exp TEXT;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS babysitter_avail TEXT;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE persons ADD COLUMN IF NOT EXISTS membership_fee TEXT;           -- '有' / '無'
ALTER TABLE persons ADD COLUMN IF NOT EXISTS membership_fee_amount INTEGER; -- 1550 or 350

-- customers テーブル拡張（求人受付表 1-2-1 関連の担当者・条件情報）
ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_name TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_phone TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS staff_notes TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS job_contents TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_type TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_temp_date TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_days TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_start TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS freq_weekly_end TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS family_adults INTEGER;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS family_children INTEGER;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS introducer_name TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS intro_route TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS intro_other_text TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pet_type TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pet_other_text TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_none BOOLEAN;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_date1 TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS interview_date2 TEXT;

-- introductions テーブル拡張（求人管理簿 1-2-2・求職管理簿 1-1-4 用）
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS emp_status TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS hire_result TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS ledger_remarks TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS labor_contract TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS rishoku_status TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS henreikin TEXT;
ALTER TABLE introductions ADD COLUMN IF NOT EXISTS emp_period TEXT;

-- register_records テーブル拡張（会費・振込済みフラグ）
ALTER TABLE register_records ADD COLUMN IF NOT EXISTS membership_fee INTEGER;
ALTER TABLE register_records ADD COLUMN IF NOT EXISTS transferred BOOLEAN NOT NULL DEFAULT FALSE;

-- 会費(1-1-7)の月別・振込確認チェック
CREATE TABLE IF NOT EXISTS membership_confirmations (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL,
    work_month VARCHAR(7) NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (person_id, work_month)
);
