-- ============================================================
-- test-data-seed.sql
-- 家政婦紹介事務所 人物管理システム テスト用データ投入スクリプト
--
-- 目的:
--   求職者情報(1-1-4)、紹介状一覧(1-4-2) をはじめ、各画面が
--   様々なパターン(値あり/なし、複数選択、境界値、頻度違い等)で
--   正しく表示・動作するかを確認するためのテストデータ。
--
-- 使い方:
--   psql -U postgres -h 127.0.0.1 -p 5432 -d kaseihu -f test-data-seed.sql
--   (pgAdminのクエリエディタに貼り付けて実行してもOK)
--
-- 注意:
--   - id は明示的に指定しているため、既存データがある場合は
--     IDが重複してエラーになる可能性があります。
--     クリーンな状態のDBで実行することを推奨します。
--   - 実行後、シーケンスを実データに追いつかせる処理を最後に入れています。
-- ============================================================

BEGIN;

-- ============================================================
-- 1. persons（求職者）― 1-1-4 求職者情報 / 求職管理簿 用
--    パターン: 資格の組み合わせ違い、ペット可否/アレルギー違い、
--              複数希望職種、登録日あり/なし、備考あり/なし
-- ============================================================
INSERT INTO persons (
    id, no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
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
) VALUES
-- (1) フルパターン: 資格全部あり、ペットOK、複数希望職種、備考あり
(1, 1, 'タナカ', 'ハナコ', '田中', '花子',
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
 'あり', 'できる', '経験豊富で評判が良い'),

-- (2) 最小パターン: 必須項目のみ、資格なし、備考なし、登録日NULL
(2, 2, 'サトウ', 'イチロウ', '佐藤', '一郎',
 '160-0022', '東京都新宿区新宿3-3-3', '', '',
 NULL, NULL,
 NULL, NULL, '080-2222-2222',
 '家事代行', '家事代行', '家事代行', NULL,
 FALSE, FALSE, FALSE, FALSE,
 FALSE, FALSE, FALSE, FALSE,
 NULL, NULL, NULL,
 '1990-08-01', NULL, FALSE,
 NULL, NULL, NULL, NULL, NULL,
 NULL, NULL,
 NULL, NULL, NULL),

-- (3) ペットアレルギーあり(NG)パターン
(3, 3, 'スズキ', 'マリ', '鈴木', '真理',
 '170-0013', '東京都豊島区東池袋2-2-2', 'メゾン池袋502', '',
 '東武東上線', '池袋',
 '03-3333-3333', NULL, '090-3333-3333',
 '介護', '介護', '介護', 'ハローワーク',
 FALSE, FALSE, TRUE, TRUE,
 FALSE, FALSE, TRUE, TRUE,
 '普通', '吸わない', 'なし',
 '1982-11-23', '2024-03-15', FALSE,
 '家庭', '家庭介護', NULL, '10:00-16:00', '応相談',
 '母', '090-3333-4444',
 'なし', 'できない', '犬猫NG。事前に必ず確認すること'),

-- (4) 喫煙者・ベビーシッター経験ありパターン
(4, 4, 'ヤマモト', 'ケンジ', '山本', '健二',
 '113-0021', '東京都文京区本駒込1-1-1', '', '',
 '南北線', '本駒込',
 '03-4444-4444', '03-4444-4445', '090-4444-4444',
 '家事代行,ベビーシッター', '家事代行', '家事代行,ベビーシッター', '自社サイト',
 TRUE, FALSE, FALSE, FALSE,
 TRUE, FALSE, FALSE, FALSE,
 '得意', '吸う', 'あり',
 '1988-02-14', '2024-05-20', TRUE,
 '家庭', '付添,家事', '{"火":"13:00-18:00","木":"13:00-18:00","土":"09:00-15:00"}', '13:00-18:00', '2024-06-01より',
 '妻', '090-4444-5555',
 'あり', 'できる', NULL),

-- (5) 高齢・長期未稼働気味のパターン(境界値: 古い登録日)
(5, 5, 'イトウ', 'フミコ', '伊藤', '文子',
 '112-0002', '東京都文京区小石川1-1-1', '', '',
 NULL, NULL,
 '03-5555-5555', NULL, NULL,
 '家事代行', '家事代行', '家事代行', NULL,
 FALSE, TRUE, FALSE, FALSE,
 FALSE, FALSE, FALSE, FALSE,
 '得意', '吸わない', NULL,
 '1955-01-01', '2020-04-01', FALSE,
 NULL, NULL, NULL, NULL, NULL,
 '長女', '090-5555-6666',
 NULL, NULL, '高齢のため軽作業向け案件を希望');

-- ============================================================
-- 2. customers（求人者）― 求人管理簿・紹介手数料管理簿 用
--    パターン: 頻度(臨時/毎週)違い、ペット種別違い、面接あり/なし、
--              家族構成違い、紹介経路違い
-- ============================================================
INSERT INTO customers (
    id, no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji,
    postal_code, address1, address2, address3,
    nearest_line, nearest_station, access_time,
    home_phone, fax_phone, mobile_phone,
    staff_name, staff_phone, staff_notes,
    job_contents,
    freq_type, freq_temp_date, freq_weekly_days, freq_weekly_start, freq_weekly_end,
    family_adults, family_children,
    introducer_name, intro_route, intro_other_text,
    pet_type, pet_other_text,
    interview_none, interview_date1, interview_date2,
    registered_date, notes
) VALUES
-- (1) 毎週パターン・ペット犬OK・面接2回実施済み
(1, 1, 'タカハシ', 'ユウコ', '高橋', '優子',
 '106-0032', '東京都港区六本木1-1-1', 'パークタワー1001', '',
 '日比谷線', '六本木', '駅から徒歩5分',
 '03-6111-1111', NULL, '090-6111-1111',
 '高橋様ご本人', '090-6111-1111', '在宅勤務のため日中対応可',
 '家事,料理,洗濯',
 'weekly', NULL, '月,水,金', '09:00', '13:00',
 2, 1,
 NULL, 'インターネット', NULL,
 'dog', NULL,
 FALSE, '2024-01-05 10:00:00', '2024-01-12 10:00:00',
 '2024-01-15', '大型犬あり、事前に伝えてある'),

-- (2) 臨時パターン・ペットなし・面接なし
(2, 2, 'ワタナベ', 'ジロウ', '渡辺', '次郎',
 '150-0002', '東京都渋谷区渋谷2-2-2', '', '',
 NULL, NULL, NULL,
 '03-6222-2222', '03-6222-2223', NULL,
 NULL, NULL, NULL,
 '掃除',
 'temp', '2024-07-20', NULL, NULL, NULL,
 1, 0,
 '知人紹介', NULL, NULL,
 'none', NULL,
 TRUE, NULL, NULL,
 '2024-06-01', NULL),

-- (3) 猫アレルギー配慮・タウンページ経由・子供多数
(3, 3, 'ナカムラ', 'アキコ', '中村', '明子',
 '158-0094', '東京都世田谷区玉川2-2-2', 'グランメゾン玉川303', '',
 '田園都市線', '二子玉川', '徒歩10分・バス便あり',
 '03-6333-3333', NULL, '090-6333-3333',
 '中村様', '090-6333-3333', NULL,
 '家事,ベビーシッター,買い物',
 'weekly', NULL, '火,木', '10:00', '15:00',
 2, 3,
 NULL, 'タウンページ', NULL,
 'cat', NULL,
 FALSE, '2024-02-10 14:00:00', NULL,
 '2024-02-15', '子供3人、末っ子は乳児'),

-- (4) その他ペット・その他紹介経路(自由記述あり)
(4, 4, 'コバヤシ', 'タロウ', '小林', '太郎',
 '135-0091', '東京都江東区豊洲1-1-1', 'タワー豊洲2501', '',
 'ゆりかもめ', '豊洲', NULL,
 NULL, NULL, '080-6444-4444',
 NULL, NULL, NULL,
 'アイロン,その他',
 'temp', '2024-08-01', NULL, NULL, NULL,
 1, 0,
 NULL, 'その他', '取引先からの紹介',
 'other', 'ウサギを2匹飼育',
 TRUE, NULL, NULL,
 '2024-07-01', NULL),

-- (5) 家族構成0人パターン(境界値)・面接予定2回とも設定
(5, 5, 'カトウ', 'ミサキ', '加藤', '美咲',
 '104-0061', '東京都中央区銀座1-1-1', '', '',
 '銀座線', '銀座', NULL,
 '03-6555-5555', NULL, NULL,
 NULL, NULL, NULL,
 '家事',
 'weekly', NULL, '月,火,水,木,金', '08:00', '12:00',
 0, 0,
 NULL, 'インターネット,タウンページ', NULL,
 'none', NULL,
 FALSE, '2024-03-01 09:00:00', '2024-03-08 09:00:00',
 '2024-03-10', '単身世帯、平日毎日希望');

-- ============================================================
-- 3. customer_requests（求人受付表 1-2-1）
--    パターン: 仕事内容の組み合わせ違い、面接予定あり/なし
-- ============================================================
INSERT INTO customer_requests (
    id, customer_id, postal_code, address, work_address,
    job_cooking, job_laundry, job_cleaning, job_ironing, job_babysitting, job_nursing, job_other, job_other_text,
    freq_type, freq_temp_date, freq_weekly_days, freq_weekly_start, freq_weekly_end,
    family_adults, family_children,
    introducer_name, intro_internet, intro_townpage, intro_other, intro_other_text,
    pet_none, pet_dog, pet_cat, pet_other, pet_other_text,
    remarks,
    interview_none, interview_date1, interview_date2,
    contact_history, candidate_person_id, printed
) VALUES
(1, 1, '106-0032', '東京都港区六本木1-1-1', '同上',
 TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, NULL,
 'weekly', NULL, '月,水,金', '09:00', '13:00',
 2, 1,
 NULL, TRUE, FALSE, FALSE, NULL,
 FALSE, TRUE, FALSE, FALSE, NULL,
 '大型犬がいるため訪問時は事前連絡希望',
 FALSE, '2024-01-05 10:00:00', '2024-01-12 10:00:00',
 '[{"date":"2024-01-03","note":"電話にて希望条件をヒアリング"},{"date":"2024-01-05","note":"1回目面接実施"}]',
 1, TRUE),

(2, 2, '150-0002', '東京都渋谷区渋谷2-2-2', '同上',
 FALSE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, NULL,
 'temp', '2024-07-20', NULL, NULL, NULL,
 1, 0,
 '知人紹介', FALSE, FALSE, FALSE, NULL,
 TRUE, FALSE, FALSE, FALSE, NULL,
 NULL,
 TRUE, NULL, NULL,
 NULL, NULL, FALSE),

(3, 4, '135-0091', '東京都江東区豊洲1-1-1', '勤務先は別住所を予定',
 FALSE, TRUE, FALSE, TRUE, FALSE, FALSE, TRUE, 'ペットの世話全般',
 'temp', '2024-08-01', NULL, NULL, NULL,
 1, 0,
 NULL, FALSE, FALSE, TRUE, '取引先からの紹介',
 FALSE, FALSE, FALSE, TRUE, 'ウサギを2匹飼育',
 '在宅勤務の日のみ対応可',
 TRUE, NULL, NULL,
 '[{"date":"2024-07-25","note":"メールにて条件確認中"}]',
 NULL, FALSE);

-- ============================================================
-- 4. sales / sales_details ― 紹介手数料管理簿(1-3-1)、
--    領収書関連画面 用
--    パターン: 日給複数枠、残業時給あり、日雇1ヶ月/臨時3ヶ月の
--              手入力値あり/なし(0)、備考あり/なし
-- ============================================================
INSERT INTO sales (id, person_id, introduction_date, reception_fee, receipt_no, created_at) VALUES
(1, 1, '2024-01-15', 710, '0001', '2024-01-15 10:00:00'),
(2, 2, '2024-06-01', 710, '0002', '2024-06-01 11:00:00'),
(3, 4, '2024-07-01', 710, '0003', '2024-07-01 09:30:00');

INSERT INTO sales_details (
    id, sales_id, customer_id,
    hourly_wage, hourly_wage_overtime, working_hours,
    monthly_total, commission, tax, detail_order,
    work_start_date, work_end_date,
    reception_fee, customer_fee,
    daily_wages, introduction_date, receipt_no, remarks, issued_at,
    daily_wage_1month, temp_3month
) VALUES
-- (1) 日給複数枠あり・残業時給あり・臨時3ヶ月に手入力あり
(1, 1, 1,
 1200, 1500, 160.5,
 192600, 31779, 3177, 1,
 '2024-01-20', '2024-02-19',
 710, 1000,
 '8000,8500,8000', '2024-01-15', '0001', '大型犬対応のため割増あり', '2024-01-20 09:00:00',
 0, 15000),

-- (2) 臨時契約・日給枠なし・日雇1ヶ月に手入力あり
(2, 2, 2,
 1000, NULL, 40.0,
 40000, 6600, 660, 1,
 '2024-07-20', '2024-07-20',
 710, NULL,
 NULL, '2024-06-01', '0002', NULL, NULL,
 8000, 0),

-- (3) 最小パターン: 手入力欄どちらも0(未入力)、備考なし
(3, 3, 4,
 900, NULL, 20.0,
 18000, 2970, 297, 1,
 '2024-08-01', '2024-08-01',
 710, 1000,
 NULL, '2024-07-01', '0003', NULL, NULL,
 0, 0);

-- ============================================================
-- 5. introductions（紹介状）― 紹介状一覧(1-4-2)、
--    求職管理簿(1-1-4)、求人管理簿(1-2-2) 用
--    パターン: 有期/無期、採否結果違い、離職状況違い、返戻金あり/なし、
--              formData未入力(下書き)パターンも含む
-- ============================================================
INSERT INTO introductions (
    id, ref_no, person_id, customer_id, intro_date, start_date, form_data, created_at,
    emp_status, hire_result, ledger_remarks, labor_contract,
    rishoku_status, henreikin
) VALUES
-- (1) 無期契約・採用決定・離職状況「問題なし」
(1, '0001', 1, 1, '2024-01-15', '2024-01-20',
 '{"empPeriod":"無期","trialPeriod":"無","workStyle":"通勤","workFreq":"毎週","overtime":"有","raise":"有","payMethod":"振込"}',
 '2024-01-15 10:00:00',
 '稼働中', '採用', '大型犬対応のため割増賃金で合意', '無期',
 '6カ月超', NULL),

-- (2) 有期契約・臨時対応・返戻金ありパターン
(2, '0002', 2, 2, '2024-06-01', '2024-07-20',
 '{"empPeriod":"有期","trialPeriod":"無","workStyle":"通勤","workFreq":"隔週","overtime":"無","raise":"無","payMethod":"現金"}',
 '2024-06-01 11:00:00',
 '終了', '不採用', '契約期間満了につき終了', '有期',
 '6カ月以内', '3000'),

-- (3) 求職者3(伊藤)・求人者3(中村)紹介検討中パターン: 開始日未定
(3, '0003', 5, 3, '2024-05-10', NULL,
 '{"empPeriod":"無期","trialPeriod":"有","workStyle":"通勤","workFreq":"毎週","overtime":"無","raise":"無","payMethod":"振込"}',
 '2024-05-10 15:00:00',
 '選考中', NULL, '本人希望により軽作業案件で調整中', '無期',
 '不明', NULL),

-- (4) 下書き状態: formDataあり・雇用結果類の入力はまだ無し(NULL多め)
(4, '0004', 4, 4, '2024-07-01', NULL,
 '{"empPeriod":"有期","trialPeriod":"無","workStyle":"泊込み","workFreq":"毎週","overtime":"有","raise":"無","payMethod":"現金"}',
 '2024-07-01 09:30:00',
 NULL, NULL, NULL, NULL,
 NULL, NULL);

-- ============================================================
-- 6. receipts_issued（領収書発行記録）― 1-7-1(求人者向け)/
--    1-7-2(求職者向け) 用
--    パターン: 求人者向け/求職者向け、印刷済み/未印刷
-- ============================================================
INSERT INTO receipts_issued (id, customer_id, sales_detail_id, receipt_type, amount, printed, printed_at, created_at) VALUES
(1, 1, 1, 'CUSTOMER', 1000, TRUE, '2024-01-20 09:10:00', '2024-01-20 09:00:00'),
(2, NULL, 2, 'JOBSEEKER', 6600, FALSE, NULL, '2024-07-20 10:00:00'),
(3, 4, 3, 'CUSTOMER', 1000, FALSE, NULL, '2024-08-01 09:00:00');

-- ============================================================
-- 7. register_records（レジ計算記録 1-8-1）
--    パターン: 複数月分、メモあり/なし
-- ============================================================
INSERT INTO register_records (id, person_id, work_month, salary, fee, memo, created_at) VALUES
(1, 1, '2024-01', 193000, 31845, '1月分・繁忙期のため通常より多め', '2024-02-01 10:00:00'),
(2, 1, '2024-02', 180000, 29700, NULL, '2024-03-01 10:00:00'),
(3, 4, '2024-07', 18000, 2970, '臨時1回のみ', '2024-08-01 10:00:00');

-- ============================================================
-- 8. sancare_net_monthly（手数料収入決算表 1-3-3 関連）
-- ============================================================
INSERT INTO sancare_net_monthly (id, year_month, amount) VALUES
(1, '2024-01', 50000),
(2, '2024-02', 0),
(3, '2024-07', 12000);

-- ============================================================
-- 9. schedules（スケジュール）
-- ============================================================
INSERT INTO schedules (id, person_id, customer_id, day_of_week, time_slot, created_at) VALUES
(1, 1, 1, '月', '09:00:00', '2024-01-15 10:00:00'),
(2, 1, 1, '水', '09:00:00', '2024-01-15 10:00:00'),
(3, 4, 4, '火', '13:00:00', '2024-07-01 09:30:00');

COMMIT;

-- ============================================================
-- シーケンスを、明示的に投入したIDに追いつかせる
-- (以後 INSERT で id を省略しても採番が重複しないようにする)
-- ============================================================
SELECT setval(pg_get_serial_sequence('persons', 'id'), (SELECT MAX(id) FROM persons));
SELECT setval(pg_get_serial_sequence('customers', 'id'), (SELECT MAX(id) FROM customers));
SELECT setval(pg_get_serial_sequence('customer_requests', 'id'), (SELECT MAX(id) FROM customer_requests));
SELECT setval(pg_get_serial_sequence('sales', 'id'), (SELECT MAX(id) FROM sales));
SELECT setval(pg_get_serial_sequence('sales_details', 'id'), (SELECT MAX(id) FROM sales_details));
SELECT setval(pg_get_serial_sequence('introductions', 'id'), (SELECT MAX(id) FROM introductions));
SELECT setval(pg_get_serial_sequence('receipts_issued', 'id'), (SELECT MAX(id) FROM receipts_issued));
SELECT setval(pg_get_serial_sequence('register_records', 'id'), (SELECT MAX(id) FROM register_records));
SELECT setval(pg_get_serial_sequence('sancare_net_monthly', 'id'), (SELECT MAX(id) FROM sancare_net_monthly));
SELECT setval(pg_get_serial_sequence('schedules', 'id'), (SELECT MAX(id) FROM schedules));
