-- ============================================================
-- test-data-seed-v4-emp-period.sql
-- テスト太郎/花子/次郎 × テスト商事/テスト興産 の組み合わせに対して
-- 雇用期間区分(emp_period)を持つ紹介状(introductions)を投入する。
--
-- ⚠️ 重要な制約:
--   紹介手数料管理簿(1-3-1)の自動計算は「求職者×求人者」の組み合わせごとに
--   最新の紹介状1件の雇用期間区分だけを見る仕様です。月ごとの使い分けは
--   できないため、このSQLでは「求人者ごとに区分を1つ」に決め打ちしています。
--     ・テスト商事 → 臨時
--     ・テスト興産 → 日雇い
--   test-data-seed-v3-report.sql で月ごとに臨時/日雇いを混在させていた
--   部分は、完全には再現されません（後述）。
--
-- 使い方:
--   pgAdminの新しいクエリタブに全文貼り付けて実行（F5）
-- ============================================================

DO $$
DECLARE
    p_id  BIGINT;
    c_shoji_id BIGINT; -- テスト商事
    c_kosan_id BIGINT; -- テスト興産
BEGIN
    -- ── テスト太郎のIDを取得（無ければ何もしない） ──
    SELECT id INTO p_id FROM persons
    WHERE last_name_kanji = 'テスト' AND first_name_kanji = '太郎'
    ORDER BY id LIMIT 1;

    SELECT id INTO c_shoji_id FROM customers
    WHERE last_name_kanji = 'テスト商事' ORDER BY id LIMIT 1;

    SELECT id INTO c_kosan_id FROM customers
    WHERE last_name_kanji = 'テスト興産' ORDER BY id LIMIT 1;

    IF p_id IS NULL OR c_shoji_id IS NULL OR c_kosan_id IS NULL THEN
        RAISE NOTICE '対象データが見つかりませんでした(persons/customersにテスト太郎・テスト商事・テスト興産が必要です)';
    ELSE
        -- テスト太郎 × テスト商事 → 有期・臨時
        INSERT INTO introductions (ref_no, person_id, customer_id, intro_date, form_data, emp_period, created_at)
        VALUES (
            (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
            p_id, c_shoji_id, '2026-04-01',
            '{"empPeriod":"有期","empSubType":"臨時"}',
            '臨時',
            '2026-04-01 09:00:00'
        );

        -- テスト太郎 × テスト興産 → 有期・日雇い
        INSERT INTO introductions (ref_no, person_id, customer_id, intro_date, form_data, emp_period, created_at)
        VALUES (
            (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
            p_id, c_kosan_id, '2026-04-01',
            '{"empPeriod":"有期","empSubType":"日雇い"}',
            '日雇い',
            '2026-04-01 09:00:00'
        );

        RAISE NOTICE '投入完了: person=% / テスト商事=%(臨時) / テスト興産=%(日雇い)',
            p_id, c_shoji_id, c_kosan_id;
    END IF;
END $$;
