-- ============================================================
-- test-data-seed.sql
-- 家政婦紹介事務所 人物管理システム テスト用データ投入スクリプト（統合版）
--
-- これまで test-data-seed.sql(v1・廃止) / -v2.sql / -v3-report.sql /
-- -v4-emp-period.sql と分かれていたものを1ファイルに統合した。
-- 実行順は「基本データ(旧v2) → 事業報告書月別表確認用追加データ(旧v3) →
-- 雇用期間区分確認用追加データ(旧v4)」で、この並び順のまま上から
-- 全文を一度に実行すること（v4は旧v2で投入した「テスト太郎」等を
-- 名前で検索して参照するため、単独では動作しない）。
--
-- 使い方:
--   psql -U postgres -h 127.0.0.1 -p 5432 -d kaseihu -f test-data-seed.sql
--   (pgAdminのクエリエディタに全文貼り付けて実行してもOK)
--
-- 注意:
--   - クリーンな状態のDBで実行することを推奨します。
--   - 旧v3は新しいテスト用の求職者・求人者・売上データを追加で
--     INSERTするため、実行するたびにテストデータが増えます。
-- ============================================================

DO $$
DECLARE
    -- persons
    p1_id BIGINT; -- 田中 花子（フルパターン）
    p2_id BIGINT; -- 佐藤 一郎（最小パターン）
    p3_id BIGINT; -- 鈴木 真理（ペットアレルギー）
    p4_id BIGINT; -- 山本 健二（喫煙・ベビーシッター経験）
    p5_id BIGINT; -- 伊藤 文子（高齢・長期登録）

    -- customers
    c1_id BIGINT; -- 高橋 優子（毎週・犬OK）
    c2_id BIGINT; -- 渡辺 次郎（臨時・ペットなし）
    c3_id BIGINT; -- 中村 明子（猫アレルギー配慮）
    c4_id BIGINT; -- 小林 太郎（その他ペット）
    c5_id BIGINT; -- 加藤 美咲（単身世帯）

    -- sales / sales_details
    s1_id BIGINT;
    s2_id BIGINT;
    s3_id BIGINT;
    sd1_id BIGINT;
    sd2_id BIGINT;
    sd3_id BIGINT;
BEGIN

    -- ========================================================
    -- 1. persons（求職者）― 1-1-4 求職者情報 / 求職管理簿 用
    -- ========================================================

    -- (1) フルパターン: 資格全部あり、ペットOK、複数希望職種、備考あり
    INSERT INTO persons (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1, address2, address3,
        nearest_line, nearest_station,
        home_phone, fax_phone, mobile_phone,
        desired_job, desired_type, desired_types, introducer,
        qual_nursery, qual_cook, qual_care_worker, qual_care_helper,
        animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy,
        cooking, smoking, childcare_exp,
        birth_date, registered_date, line_works,
        work_location, work_duties, specific_days, work_available_hours, work_start_period,
        emergency_relation, emergency_phone,
        babysitter_exp, babysitter_avail, notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM persons),
        'タナカ', 'ハナコ', '田中', '花子',
        '150-0001', '東京都渋谷区神宮前1-1-1', 'サンハイツ201', '',
        'JR山手線', '原宿',
        '03-1111-1111', '03-1111-1112', '090-1111-1111',
        '家事代行,介護', '家事代行', '家事代行,介護,ベビーシッター', '知人紹介',
        TRUE, TRUE, TRUE, TRUE,
        TRUE, TRUE, FALSE, FALSE,
        '得意', '吸わない', 'あり',
        '1975-04-12', '2024-01-10', TRUE,
        '家庭,病院', '付添,家事,家庭介護', '{"月":"09:00-17:00","水":"09:00-17:00"}', '09:00-18:00', '即日可',
        '夫', '090-1111-2222',
        'あり', 'できる', '経験豊富で評判が良い'
    ) RETURNING id INTO p1_id;

    -- (2) 最小パターン: 必須項目のみ、資格なし、備考なし、登録日NULL
    INSERT INTO persons (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1,
        mobile_phone,
        desired_job, desired_type, desired_types,
        qual_nursery, qual_cook, qual_care_worker, qual_care_helper,
        animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy,
        birth_date, registered_date, line_works
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM persons),
        'サトウ', 'イチロウ', '佐藤', '一郎',
        '160-0022', '東京都新宿区新宿3-3-3',
        '080-2222-2222',
        '家事代行', '家事代行', '家事代行',
        FALSE, FALSE, FALSE, FALSE,
        FALSE, FALSE, FALSE, FALSE,
        '1990-08-01', NULL, FALSE
    ) RETURNING id INTO p2_id;

    -- (3) ペットアレルギーあり(NG)パターン
    INSERT INTO persons (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1, address2,
        nearest_line, nearest_station,
        home_phone, mobile_phone,
        desired_job, desired_type, desired_types, introducer,
        qual_nursery, qual_cook, qual_care_worker, qual_care_helper,
        animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy,
        cooking, smoking, childcare_exp,
        birth_date, registered_date, line_works,
        work_location, work_duties, work_available_hours, work_start_period,
        emergency_relation, emergency_phone,
        babysitter_exp, babysitter_avail, notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM persons),
        'スズキ', 'マリ', '鈴木', '真理',
        '170-0013', '東京都豊島区東池袋2-2-2', 'メゾン池袋502',
        '東武東上線', '池袋',
        '03-3333-3333', '090-3333-3333',
        '介護', '介護', '介護', 'ハローワーク',
        FALSE, FALSE, TRUE, TRUE,
        FALSE, FALSE, TRUE, TRUE,
        '普通', '吸わない', 'なし',
        '1982-11-23', '2024-03-15', FALSE,
        '家庭', '家庭介護', '10:00-16:00', '応相談',
        '母', '090-3333-4444',
        'なし', 'できない', '犬猫NG。事前に必ず確認すること'
    ) RETURNING id INTO p3_id;

    -- (4) 喫煙者・ベビーシッター経験ありパターン
    INSERT INTO persons (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1,
        nearest_line, nearest_station,
        home_phone, fax_phone, mobile_phone,
        desired_job, desired_type, desired_types, introducer,
        qual_nursery, qual_cook, qual_care_worker, qual_care_helper,
        animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy,
        cooking, smoking, childcare_exp,
        birth_date, registered_date, line_works,
        work_location, work_duties, specific_days, work_available_hours, work_start_period,
        emergency_relation, emergency_phone,
        babysitter_exp, babysitter_avail
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM persons),
        'ヤマモト', 'ケンジ', '山本', '健二',
        '113-0021', '東京都文京区本駒込1-1-1',
        '南北線', '本駒込',
        '03-4444-4444', '03-4444-4445', '090-4444-4444',
        '家事代行,ベビーシッター', '家事代行', '家事代行,ベビーシッター', '自社サイト',
        TRUE, FALSE, FALSE, FALSE,
        TRUE, FALSE, FALSE, FALSE,
        '得意', '吸う', 'あり',
        '1988-02-14', '2024-05-20', TRUE,
        '家庭', '付添,家事', '{"火":"13:00-18:00","木":"13:00-18:00","土":"09:00-15:00"}', '13:00-18:00', '2024-06-01より',
        '妻', '090-4444-5555',
        'あり', 'できる'
    ) RETURNING id INTO p4_id;

    -- (5) 高齢・長期未稼働気味のパターン(境界値: 古い登録日)
    INSERT INTO persons (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1,
        home_phone,
        desired_job, desired_type, desired_types,
        qual_nursery, qual_cook, qual_care_worker, qual_care_helper,
        animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy,
        cooking, smoking,
        birth_date, registered_date, line_works,
        emergency_relation, emergency_phone,
        notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM persons),
        'イトウ', 'フミコ', '伊藤', '文子',
        '112-0002', '東京都文京区小石川1-1-1',
        '03-5555-5555',
        '家事代行', '家事代行', '家事代行',
        FALSE, TRUE, FALSE, FALSE,
        FALSE, FALSE, FALSE, FALSE,
        '得意', '吸わない',
        '1955-01-01', '2020-04-01', FALSE,
        '長女', '090-5555-6666',
        '高齢のため軽作業向け案件を希望'
    ) RETURNING id INTO p5_id;

    -- ========================================================
    -- 2. customers（求人者）
    -- ========================================================

    -- (1) 毎週パターン・ペット犬OK・面接2回実施済み
    INSERT INTO customers (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1, address2,
        nearest_line, nearest_station, access_time,
        home_phone, mobile_phone,
        staff_name, staff_phone, staff_notes,
        job_contents,
        freq_type, freq_weekly_days, freq_weekly_start, freq_weekly_end,
        family_adults, family_children,
        intro_route,
        pet_type,
        interview_none, interview_date1, interview_date2,
        registered_date, notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM customers),
        'タカハシ', 'ユウコ', '高橋', '優子',
        '106-0032', '東京都港区六本木1-1-1', 'パークタワー1001',
        '日比谷線', '六本木', '駅から徒歩5分',
        '03-6111-1111', '090-6111-1111',
        '高橋様ご本人', '090-6111-1111', '在宅勤務のため日中対応可',
        '家事,料理,洗濯',
        'weekly', '月,水,金', '09:00', '13:00',
        2, 1,
        'インターネット',
        'dog',
        FALSE, '2024-01-05 10:00:00', '2024-01-12 10:00:00',
        '2024-01-15', '大型犬あり、事前に伝えてある'
    ) RETURNING id INTO c1_id;

    -- (2) 臨時パターン・ペットなし・面接なし
    INSERT INTO customers (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1,
        home_phone, fax_phone,
        job_contents,
        freq_type, freq_temp_date,
        family_adults, family_children,
        introducer_name,
        pet_type,
        interview_none,
        registered_date
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM customers),
        'ワタナベ', 'ジロウ', '渡辺', '次郎',
        '150-0002', '東京都渋谷区渋谷2-2-2',
        '03-6222-2222', '03-6222-2223',
        '掃除',
        'temp', '2024-07-20',
        1, 0,
        '知人紹介',
        'none',
        TRUE,
        '2024-06-01'
    ) RETURNING id INTO c2_id;

    -- (3) 猫アレルギー配慮・タウンページ経由・子供多数
    INSERT INTO customers (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1, address2,
        nearest_line, nearest_station, access_time,
        home_phone, mobile_phone,
        staff_name, staff_phone,
        job_contents,
        freq_type, freq_weekly_days, freq_weekly_start, freq_weekly_end,
        family_adults, family_children,
        intro_route,
        pet_type,
        interview_none, interview_date1,
        registered_date, notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM customers),
        'ナカムラ', 'アキコ', '中村', '明子',
        '158-0094', '東京都世田谷区玉川2-2-2', 'グランメゾン玉川303',
        '田園都市線', '二子玉川', '徒歩10分・バス便あり',
        '03-6333-3333', '090-6333-3333',
        '中村様', '090-6333-3333',
        '家事,ベビーシッター,買い物',
        'weekly', '火,木', '10:00', '15:00',
        2, 3,
        'タウンページ',
        'cat',
        FALSE, '2024-02-10 14:00:00',
        '2024-02-15', '子供3人、末っ子は乳児'
    ) RETURNING id INTO c3_id;

    -- (4) その他ペット・その他紹介経路(自由記述あり)
    INSERT INTO customers (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1, address2,
        nearest_line, nearest_station,
        mobile_phone,
        job_contents,
        freq_type, freq_temp_date,
        family_adults, family_children,
        intro_route, intro_other_text,
        pet_type, pet_other_text,
        interview_none,
        registered_date
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM customers),
        'コバヤシ', 'タロウ', '小林', '太郎',
        '135-0091', '東京都江東区豊洲1-1-1', 'タワー豊洲2501',
        'ゆりかもめ', '豊洲',
        '080-6444-4444',
        'アイロン,その他',
        'temp', '2024-08-01',
        1, 0,
        'その他', '取引先からの紹介',
        'other', 'ウサギを2匹飼育',
        TRUE,
        '2024-07-01'
    ) RETURNING id INTO c4_id;

    -- (5) 家族構成0人パターン(境界値)・面接予定2回とも設定
    INSERT INTO customers (
        no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
        postal_code, address1,
        nearest_line, nearest_station,
        home_phone,
        job_contents,
        freq_type, freq_weekly_days, freq_weekly_start, freq_weekly_end,
        family_adults, family_children,
        intro_route,
        pet_type,
        interview_none, interview_date1, interview_date2,
        registered_date, notes
    ) VALUES (
        (SELECT COALESCE(MAX(no),0)+1 FROM customers),
        'カトウ', 'ミサキ', '加藤', '美咲',
        '104-0061', '東京都中央区銀座1-1-1',
        '銀座線', '銀座',
        '03-6555-5555',
        '家事',
        'weekly', '月,火,水,木,金', '08:00', '12:00',
        0, 0,
        'インターネット,タウンページ',
        'none',
        FALSE, '2024-03-01 09:00:00', '2024-03-08 09:00:00',
        '2024-03-10', '単身世帯、平日毎日希望'
    ) RETURNING id INTO c5_id;

    -- ========================================================
    -- 3. customer_requests（求人受付表 1-2-1）
    -- ========================================================
    INSERT INTO customer_requests (
        customer_id, postal_code, address, work_address,
        job_cooking, job_laundry, job_cleaning,
        freq_type, freq_weekly_days, freq_weekly_start, freq_weekly_end,
        family_adults, family_children,
        intro_internet,
        pet_none, pet_dog,
        remarks,
        interview_none, interview_date1, interview_date2,
        contact_history, candidate_person_id, printed
    ) VALUES (
        c1_id, '106-0032', '東京都港区六本木1-1-1', '同上',
        TRUE, TRUE, TRUE,
        'weekly', '月,水,金', '09:00', '13:00',
        2, 1,
        TRUE,
        FALSE, TRUE,
        '大型犬がいるため訪問時は事前連絡希望',
        FALSE, '2024-01-05 10:00:00', '2024-01-12 10:00:00',
        '[{"date":"2024-01-03","note":"電話にて希望条件をヒアリング"},{"date":"2024-01-05","note":"1回目面接実施"}]',
        p1_id, TRUE
    );

    INSERT INTO customer_requests (
        customer_id, postal_code, address, work_address,
        job_cleaning,
        freq_type, freq_temp_date,
        family_adults, family_children,
        introducer_name,
        pet_none,
        interview_none,
        printed
    ) VALUES (
        c2_id, '150-0002', '東京都渋谷区渋谷2-2-2', '同上',
        TRUE,
        'temp', '2024-07-20',
        1, 0,
        '知人紹介',
        TRUE,
        TRUE,
        FALSE
    );

    INSERT INTO customer_requests (
        customer_id, postal_code, address, work_address,
        job_laundry, job_ironing, job_other, job_other_text,
        freq_type, freq_temp_date,
        family_adults, family_children,
        intro_other, intro_other_text,
        pet_other, pet_other_text,
        remarks,
        interview_none,
        contact_history, printed
    ) VALUES (
        c4_id, '135-0091', '東京都江東区豊洲1-1-1', '勤務先は別住所を予定',
        TRUE, TRUE, TRUE, 'ペットの世話全般',
        'temp', '2024-08-01',
        1, 0,
        TRUE, '取引先からの紹介',
        TRUE, 'ウサギを2匹飼育',
        '在宅勤務の日のみ対応可',
        TRUE,
        '[{"date":"2024-07-25","note":"メールにて条件確認中"}]', FALSE
    );

    -- ========================================================
    -- 4. sales / sales_details ― 紹介手数料管理簿(1-3-1)、
    --    領収書関連画面 用
    -- ========================================================
    INSERT INTO sales (person_id, introduction_date, reception_fee, receipt_no, created_at)
    VALUES (p1_id, '2024-01-15', 710,
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales),
        '2024-01-15 10:00:00')
    RETURNING id INTO s1_id;

    INSERT INTO sales (person_id, introduction_date, reception_fee, receipt_no, created_at)
    VALUES (p2_id, '2024-06-01', 710,
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales),
        '2024-06-01 11:00:00')
    RETURNING id INTO s2_id;

    INSERT INTO sales (person_id, introduction_date, reception_fee, receipt_no, created_at)
    VALUES (p4_id, '2024-07-01', 710,
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales),
        '2024-07-01 09:30:00')
    RETURNING id INTO s3_id;

    -- (1) 日給複数枠あり・残業時給あり・臨時3ヶ月に手入力あり
    INSERT INTO sales_details (
        sales_id, customer_id,
        hourly_wage, hourly_wage_overtime, working_hours,
        monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date,
        reception_fee, customer_fee,
        daily_wages, introduction_date, receipt_no, remarks, issued_at,
        daily_wage_1month, temp_3month
    ) VALUES (
        s1_id, c1_id,
        1200, 1500, 160.5,
        192600, 31779, 3177, 1,
        '2024-01-20', '2024-02-19',
        710, 1000,
        '8000,8500,8000', '2024-01-15',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        '大型犬対応のため割増あり', '2024-01-20 09:00:00',
        0, 15000
    ) RETURNING id INTO sd1_id;

    -- (2) 臨時契約・日給枠なし・日雇1ヶ月に手入力あり
    INSERT INTO sales_details (
        sales_id, customer_id,
        hourly_wage, working_hours,
        monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date,
        reception_fee,
        introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s2_id, c2_id,
        1000, 40.0,
        40000, 6600, 660, 1,
        '2024-07-20', '2024-07-20',
        710,
        '2024-06-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        8000, 0
    ) RETURNING id INTO sd2_id;

    -- (3) 最小パターン: 手入力欄どちらも0(未入力)、備考なし
    INSERT INTO sales_details (
        sales_id, customer_id,
        hourly_wage, working_hours,
        monthly_total, commission, tax, detail_order,
        work_start_date, work_end_date,
        reception_fee, customer_fee,
        introduction_date, receipt_no,
        daily_wage_1month, temp_3month
    ) VALUES (
        s3_id, c4_id,
        900, 20.0,
        18000, 2970, 297, 1,
        '2024-08-01', '2024-08-01',
        710, 1000,
        '2024-07-01',
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM sales_details),
        0, 0
    ) RETURNING id INTO sd3_id;

    -- ========================================================
    -- 5. introductions（紹介状）― 紹介状一覧(1-4-2)、
    --    求職管理簿(1-1-4)、求人管理簿(1-2-2) 用
    -- ========================================================

    -- (1) 無期契約・採用決定・離職状況「問題なし」
    INSERT INTO introductions (
        ref_no, person_id, customer_id, intro_date, start_date, form_data, created_at,
        emp_status, hire_result, ledger_remarks, labor_contract,
        rishoku_status, henreikin
    ) VALUES (
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
        p1_id, c1_id, '2024-01-15', '2024-01-20',
        '{"empPeriod":"無期","trialPeriod":"無","workStyle":"通勤","workFreq":"毎週","overtime":"有","raise":"有","payMethod":"振込"}',
        '2024-01-15 10:00:00',
        '稼働中', '採用', '大型犬対応のため割増賃金で合意', '無期',
        '6カ月超', NULL
    );

    -- (2) 有期契約・臨時対応・返戻金ありパターン
    INSERT INTO introductions (
        ref_no, person_id, customer_id, intro_date, start_date, form_data, created_at,
        emp_status, hire_result, ledger_remarks, labor_contract,
        rishoku_status, henreikin
    ) VALUES (
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
        p2_id, c2_id, '2024-06-01', '2024-07-20',
        '{"empPeriod":"有期","trialPeriod":"無","workStyle":"通勤","workFreq":"隔週","overtime":"無","raise":"無","payMethod":"現金"}',
        '2024-06-01 11:00:00',
        '終了', '不採用', '契約期間満了につき終了', '有期',
        '6カ月以内', '3000'
    );

    -- (3) 求職者(伊藤)・求人者(中村)紹介検討中パターン: 開始日未定
    INSERT INTO introductions (
        ref_no, person_id, customer_id, intro_date, form_data, created_at,
        emp_status, ledger_remarks, labor_contract,
        rishoku_status
    ) VALUES (
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
        p5_id, c3_id, '2024-05-10',
        '{"empPeriod":"無期","trialPeriod":"有","workStyle":"通勤","workFreq":"毎週","overtime":"無","raise":"無","payMethod":"振込"}',
        '2024-05-10 15:00:00',
        '選考中', '本人希望により軽作業案件で調整中', '無期',
        '不明'
    );

    -- (4) 下書き状態: formDataあり・雇用結果類の入力はまだ無し(NULL多め)
    INSERT INTO introductions (
        ref_no, person_id, customer_id, intro_date, form_data, created_at
    ) VALUES (
        (SELECT LPAD((COALESCE(MAX(id),0)+1)::text, 4, '0') FROM introductions),
        p4_id, c4_id, '2024-07-01',
        '{"empPeriod":"有期","trialPeriod":"無","workStyle":"泊込み","workFreq":"毎週","overtime":"有","raise":"無","payMethod":"現金"}',
        '2024-07-01 09:30:00'
    );

    -- ========================================================
    -- 6. receipts_issued（領収書発行記録）
    -- ========================================================
    INSERT INTO receipts_issued (customer_id, sales_detail_id, receipt_type, amount, printed, printed_at, created_at)
    VALUES (c1_id, sd1_id, 'CUSTOMER', 1000, TRUE, '2024-01-20 09:10:00', '2024-01-20 09:00:00');

    INSERT INTO receipts_issued (customer_id, sales_detail_id, receipt_type, amount, printed, created_at)
    VALUES (NULL, sd2_id, 'JOBSEEKER', 6600, FALSE, '2024-07-20 10:00:00');

    INSERT INTO receipts_issued (customer_id, sales_detail_id, receipt_type, amount, printed, created_at)
    VALUES (c4_id, sd3_id, 'CUSTOMER', 1000, FALSE, '2024-08-01 09:00:00');

    -- ========================================================
    -- 7. register_records（レジ計算記録 1-8-1）
    -- ========================================================
    INSERT INTO register_records (person_id, work_month, salary, fee, memo, created_at)
    VALUES (p1_id, '2024-01', 193000, 31845, '1月分・繁忙期のため通常より多め', '2024-02-01 10:00:00');

    INSERT INTO register_records (person_id, work_month, salary, fee, memo, created_at)
    VALUES (p1_id, '2024-02', 180000, 29700, NULL, '2024-03-01 10:00:00');

    INSERT INTO register_records (person_id, work_month, salary, fee, memo, created_at)
    VALUES (p4_id, '2024-07', 18000, 2970, '臨時1回のみ', '2024-08-01 10:00:00');

    -- ========================================================
    -- 8. sancare_net_monthly（手数料収入決算表 1-3-3 関連）
    --    UNIQUE(year_month) のため既存があれば上書き(更新)する
    -- ========================================================
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2024-01', 50000)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2024-02', 0)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;
    INSERT INTO sancare_net_monthly (year_month, amount) VALUES ('2024-07', 12000)
        ON CONFLICT (year_month) DO UPDATE SET amount = EXCLUDED.amount;

    -- ========================================================
    -- 9. schedules（スケジュール）
    -- ========================================================
    INSERT INTO schedules (person_id, customer_id, day_of_week, time_slot, created_at)
    VALUES (p1_id, c1_id, '月', '09:00:00', '2024-01-15 10:00:00');

    INSERT INTO schedules (person_id, customer_id, day_of_week, time_slot, created_at)
    VALUES (p1_id, c1_id, '水', '09:00:00', '2024-01-15 10:00:00');

    INSERT INTO schedules (person_id, customer_id, day_of_week, time_slot, created_at)
    VALUES (p4_id, c4_id, '火', '13:00:00', '2024-07-01 09:30:00');

    RAISE NOTICE '投入完了: persons=%,%,%,%,% / customers=%,%,%,%,%',
        p1_id, p2_id, p3_id, p4_id, p5_id, c1_id, c2_id, c3_id, c4_id, c5_id;

END $$;

-- ============================================================
-- 以下は旧 test-data-seed-v3-report.sql の内容
-- ============================================================
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

-- ============================================================
-- 以下は旧 test-data-seed-v4-emp-period.sql の内容
-- ============================================================
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
