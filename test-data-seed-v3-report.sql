-- ============================================================
-- test-data-seed-v3-report.sql
-- 「事業報告書月別表（労働局用年度別）」確認用の追加テストデータ
--
-- 対象年度: 労働局年度2026（2026年4月 〜 2027年3月）
-- 目的:
--   これまでのテストデータは7月にほぼ集中していたため、
--   1-3-3画面の「事業報告書月別表」タブで月ごとの違いが
--   分かりにくい状態でした。本スクリプトは月をまたいで
--   データが分散するよう追加投入します。
--
-- 含まれるパターン:
--   ・求人-臨時: 同じ月に複数件「臨時3ヶ月」に金額が入っているケース
--     （例: 4月に1,000円と3,000円の2件 → 件数は"2"と表示されるはず）
--   ・求職-有効: 受付料710円が複数月にわたって発生するケース
--   ・手数料-臨時/日雇: 複数月にまたがる金額の違い
--   ・サン・ケアネット: 複数月に手入力があるケース
--
-- 使い方:
--   pgAdminの新しいクエリタブに全文貼り付けて実行（F5）
--   ※ IDは自動採番されるIDをその場で使うため、既存データがあっても衝突しません
-- ============================================================

DO $$
DECLARE
    p1_id BIGINT; -- テスト太郎(求職者1)
    p2_id BIGINT; -- テスト花子(求職者2)
    p3_id BIGINT; -- テスト次郎(求職者3)

    c1_id BIGINT; -- テスト商事(求人者1)
    c2_id BIGINT; -- テスト興産(求人者2)

    s_id  BIGINT; -- salesレコード用の一時変数
BEGIN

    -- ========================================================
    -- 求職者・求人者（このテストデータ専用に3名・2社作成）
    -- ========================================================
    INSERT INTO persons (no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji, mobile_phone)
    VALUES ((SELECT COALESCE(MAX(no),0)+1 FROM persons), 'テスト', 'タロウ', 'テスト', '太郎', '090-0000-0001')
    RETURNING id INTO p1_id;

    INSERT INTO persons (no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji, mobile_phone)
    VALUES ((SELECT COALESCE(MAX(no),0)+1 FROM persons), 'テスト', 'ハナコ', 'テスト', '花子', '090-0000-0002')
    RETURNING id INTO p2_id;

    INSERT INTO persons (no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji, mobile_phone)
    VALUES ((SELECT COALESCE(MAX(no),0)+1 FROM persons), 'テスト', 'ジロウ', 'テスト', '次郎', '090-0000-0003')
    RETURNING id INTO p3_id;

    INSERT INTO customers (no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji, mobile_phone)
    VALUES ((SELECT COALESCE(MAX(no),0)+1 FROM customers), 'テストショウジ', '', 'テスト商事', '', '090-1000-0001')
    RETURNING id INTO c1_id;

    INSERT INTO customers (no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji, mobile_phone)
    VALUES ((SELECT COALESCE(MAX(no),0)+1 FROM customers), 'テストコウサン', '', 'テスト興産', '', '090-1000-0002')
    RETURNING id INTO c2_id;

    -- ========================================================
    -- ヘルパー的に使う共通sales(親レコード)を1件作成
    -- ========================================================
    INSERT INTO sales (person_id, introduction_date, reception_fee, receipt_no, created_at)
    VALUES (p1_id, '2026-04-01', 710,
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales), '2026-04-01 09:00:00')
    RETURNING id INTO s_id;

    -- ========================================================
    -- 4月: 臨時3ヶ月が2件（1,000円 + 3,000円 → 件数は"2"、金額合計は4,000円）
    --      求職-有効(受付料710円)も1件
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 1000, 10.0, 10000, 1650, 165, 1,
        '2026-04-05', '2026-04-05', 710, '2026-04-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 1000
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 1200, 15.0, 18000, 2970, 297, 1,
        '2026-04-12', '2026-04-12', 0, '2026-04-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 3000
    );

    -- ========================================================
    -- 5月: 臨時3ヶ月なし、日雇1ヶ月が1件、受付料710円が2件
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 900, 20.0, 18000, 2970, 297, 1,
        '2026-05-03', '2026-05-03', 710, '2026-05-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        5000, 0
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 1000, 8.0, 8000, 1320, 132, 1,
        '2026-05-20', '2026-05-20', 710, '2026-05-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 0
    );

    -- ========================================================
    -- 6月: 臨時3ヶ月1件のみ(件数=1)、受付料なし
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 1100, 12.0, 13200, 2178, 217, 1,
        '2026-06-10', '2026-06-10', 0, '2026-06-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 2500
    );

    -- ========================================================
    -- 9月: 日雇1ヶ月2件、受付料710円1件
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 950, 18.0, 17100, 2821, 282, 1,
        '2026-09-05', '2026-09-05', 710, '2026-09-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        6000, 0
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 1050, 9.0, 9450, 1559, 155, 1,
        '2026-09-22', '2026-09-22', 0, '2026-09-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        4000, 0
    );

    -- ========================================================
    -- 12月: 臨時3ヶ月3件（件数=3, 合計は 1500+2500+500=4500円）
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 1000, 5.0, 5000, 825, 82, 1,
        '2026-12-03', '2026-12-03', 710, '2026-12-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 1500
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 1000, 6.0, 6000, 990, 99, 1,
        '2026-12-15', '2026-12-15', 0, '2026-12-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 2500
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 800, 4.0, 3200, 528, 52, 1,
        '2026-12-28', '2026-12-28', 0, '2026-12-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 500
    );

    -- ========================================================
    -- 2月・3月（労働局年度末）: 受付料710円のみ数件
    -- ========================================================
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 1000, 10.0, 10000, 1650, 165, 1,
        '2027-02-10', '2027-02-10', 710, '2027-02-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 0
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c1_id, 1000, 10.0, 10000, 1650, 165, 1,
        '2027-03-05', '2027-03-05', 710, '2027-03-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 0
    );
    INSERT INTO sales_details (
        sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date, reception_fee, introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s_id, c2_id, 1000, 10.0, 10000, 1650, 165, 1,
        '2027-03-20', '2027-03-20', 710, '2027-03-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 0
    );

    -- ========================================================
    -- サン・ケアネット（手入力欄）を複数月ぶん投入
    -- ========================================================
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2026-04', 3000)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2026-06', 1500)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2026-09', 4000)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2026-12', 2000)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;

    RAISE NOTICE '投入完了: persons=%,%,% / customers=%,% / sales=%',
        p1_id, p2_id, p3_id, c1_id, c2_id, s_id;

END $$;
