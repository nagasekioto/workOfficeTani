-- schema-update-9.sql
-- 売上入力 (1-1-3) 拡張
-- 「売上」= 時給×勤務時間 + 日給×掛け率(%) + チェックした手数料(求人受付手数料1000円/求職受付手数料710円)
-- ※monthlyTotal（賃金総額。決算表1-3-1/1-3-3の紹介手数料計算に使用）とは別の値として保持する。

-- 日給への掛け率（%）。未指定時のデフォルトは16.5。
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS daily_wage_rate NUMERIC(5,2) DEFAULT 16.5;

-- 売上（上記計算式の結果）
ALTER TABLE sales_details ADD COLUMN IF NOT EXISTS sales_amount INTEGER DEFAULT 0;

-- 既存データは掛け率未設定のため16.5で初期化しておく
UPDATE sales_details SET daily_wage_rate = 16.5 WHERE daily_wage_rate IS NULL;
