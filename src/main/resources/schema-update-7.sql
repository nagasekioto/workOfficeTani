-- sales_detailsテーブルに発行日時カラムを追加
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP;

-- 既存の発行済みデータ（receipt_noがあるもの）は introduction_date を発行日として使う
UPDATE sales_details
SET issued_at = introduction_date::timestamp
WHERE receipt_no IS NOT NULL
  AND receipt_no <> ''
  AND issued_at IS NULL
  AND introduction_date IS NOT NULL;
