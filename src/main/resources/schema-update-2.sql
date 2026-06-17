-- 求人受付表（1-2-1）
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

-- 求人管理簿（1-2-2）
CREATE TABLE IF NOT EXISTS customer_ledgers (
    id BIGSERIAL PRIMARY KEY,
    customer_request_id BIGINT,
    customer_id BIGINT,
    job_type VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 領収書テーブル（1-4-1）
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

-- 1-1-5管理簿：求職者の出向先
ALTER TABLE persons ADD COLUMN IF NOT EXISTS dispatch_customer_id BIGINT;

-- 連絡履歴（JSON形式でcustomer_requestsに格納のため別テーブル不要）
-- 候補者はcustomer_requests.candidate_person_idで対応

-- customers テーブルに accessInfo 追加
ALTER TABLE customers ADD COLUMN IF NOT EXISTS access_info TEXT;
