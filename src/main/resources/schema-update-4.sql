-- 1-8-1 レジ計算記録テーブル
CREATE TABLE IF NOT EXISTS register_records (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL,
    work_month VARCHAR(7) NOT NULL,   -- "2025-01" 形式
    salary INTEGER NOT NULL,
    fee INTEGER NOT NULL,             -- salary * 0.15
    memo TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_register_records_work_month ON register_records(work_month);
CREATE INDEX IF NOT EXISTS idx_register_records_person_id ON register_records(person_id);
