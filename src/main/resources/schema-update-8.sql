-- schema-update-8.sql
-- 紹介手数料管理簿 (1-3-1) 拡張

-- 日雇1ヶ月（手入力）
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wage_1month INTEGER DEFAULT 0;

-- 臨時3ヶ月（手入力）
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS temp_3month INTEGER DEFAULT 0;

-- ─── 手数料収入決算表 (1-3-3) 用テーブル ─────────────────────────────
-- サンケアネット月別入力
CREATE TABLE IF NOT EXISTS sancare_net_monthly (
    id          BIGSERIAL PRIMARY KEY,
    year_month  VARCHAR(7) NOT NULL,  -- YYYY-MM
    amount      INTEGER    NOT NULL DEFAULT 0,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (year_month)
);
