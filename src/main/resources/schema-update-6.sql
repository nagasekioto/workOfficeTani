-- ─── personsテーブル拡張（就職希望条件） ─────────────────────
-- 就労場所（複数可：カンマ区切り）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_location TEXT;

-- 職務内容（複数可：カンマ区切り）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_duties TEXT;

-- 希望形態（複数可：カンマ区切り）※既存desiredTypeはそのまま保持
ALTER TABLE persons ADD COLUMN IF NOT EXISTS desired_types TEXT;

-- 特定日（曜日ごとの希望時間 JSON: {"月":"09:00-17:00","火":"10:00-15:00",...}）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS specific_days TEXT;

-- 就業可能時間（例: "09:00-18:00"）
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_available_hours TEXT;

-- 労働開始時期
ALTER TABLE persons ADD COLUMN IF NOT EXISTS work_start_period TEXT;

-- 介護ヘルパー資格の有無（qualCareHelperを流用、既存カラムがあれば不要）
-- qual_care_helper は既存なのでスキップ
