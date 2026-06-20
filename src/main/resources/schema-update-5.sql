-- schema-update-5.sql
-- receipts_issued テーブルに領収番号（自動採番）・求職者IDカラムを追加

ALTER TABLE receipts_issued ADD COLUMN IF NOT EXISTS receipt_number INTEGER;
ALTER TABLE receipts_issued ADD COLUMN IF NOT EXISTS person_id BIGINT;

-- 既存レコードに仮の領収番号を採番
UPDATE receipts_issued SET receipt_number = id WHERE receipt_number IS NULL;

CREATE INDEX IF NOT EXISTS idx_receipts_issued_month ON receipts_issued (TO_CHAR(created_at, 'YYYY-MM'));
CREATE INDEX IF NOT EXISTS idx_receipts_issued_detail ON receipts_issued (sales_detail_id);
