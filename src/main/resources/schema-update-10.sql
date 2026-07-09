-- schema-update-10.sql
-- 求職者(1-1-6)・求人者(1-2-3)の「削除」を「退職/取引終了」に変更する対応
-- retired_at が null なら在職中/取引中、値が入っていれば退職者リスト(1-1-8)/元求人先(1-2-4)に表示される

ALTER TABLE persons   ADD COLUMN IF NOT EXISTS retired_at DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS retired_at DATE;
