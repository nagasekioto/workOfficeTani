# CLAUDE.md - 家政婦紹介事務所 人物管理システム

## プロジェクト概要

- **システム名**: 家政婦紹介事務所 人物管理システム
- **技術スタック**: Java 17 / Spring Boot 3.5 / Spring Data JDBC / Thymeleaf / PostgreSQL / iTextPDF
- **ビルドツール**: Maven
- **DB**: kaseihu (PostgreSQL, localhost:5432)
- **認証**: セッションベース（パスワード: 7136）

## 用語定義

| 用語 | 意味 |
|------|------|
| 求職者 | 家政婦（家事・介護をする人） |
| 求人者 | 家政婦を雇う人（雇用主） |
| 私（運用者） | 仲介業者（このシステムの運営者） |

## 画面構成

```
/login              ログイン
/menu               初期画面

  /person-menu               1-1 求職者管理
    /person/register           1-1-1 名簿入力
    /person/schedule           1-1-2 スケジュール
    /person/sales               1-1-3 売上入力
    /person/shokuji-ledger      1-1-4 管理簿入力
    /person/working-ledger      1-1-5 稼働管理簿
    /person/list                 1-1-6 求職者情報（在職中のみ）
    /person/membership           1-1-7 会費
    /person/retired-list          1-1-8 退職者リスト（1-1-6で「退職」した求職者。復職・完全削除が可能）

  /customer-menu               1-2 求人者管理
    /customer/register           1-2-1 求人者新規登録
    /customer/report             1-2-2 管理簿入力
    /customer/list                 1-2-3 求人者情報（取引中のみ）
    /customer/retired-list          1-2-4 元求人先（1-2-3で「終了」した求人者。取引再開・完全削除が可能）

  /fee-menu                   1-3 手数料管理
    /report-menu                 1-3-1 紹介手数料管理簿
    /fee-reception-ledger         1-3-2 求職受付手数料管理簿
    /fee-settlement                1-3-3 手数料収入決算表（会社決算表・労働局用決算表・事業報告書月別表）

  /introduction-menu          1-4 紹介状
    /introduction                 1-4-1 紹介状作成
    /introduction/list             1-4-2 紹介状一覧

  /receipt-menu                1-5 領収書作成
    /receipt-menu/customer-receipt   1-5-1 求人者宛領収書
    /receipt-menu/jobseeker-receipt   1-5-2 求職受付手数料領収書
    /receipt-menu/issued-list          1-5-3 発行済み領収書一覧
    /receipt-menu/delete                1-5-4 領収書削除

  /register-menu               1-6 レジ
    /register/calc                 1-6-1 振込金入力
    /register/list                  1-6-2 レジ一覧（稼働台帳との自動照合）
    /register/fee-ledger             1-6-3 手数料管理簿

  /other-menu                  1-7 その他
    /system-manual                 1-7-1 システム説明書
    /system-qa-help                  1-7-2 Q&A表
    /system-qa                        1-7-3 システム診断（データ整合性チェック）
    /data-flow-guide                    1-7-4 データフロー・計算式ガイド
```

金額の流れ・各画面の計算式の詳細は `/data-flow-guide`（1-7-4）を参照。
各画面の使い方は `/system-manual`（1-7-1）を参照。

## Development Rules

### Mandatory Validation
すべての実装・修正・リファクタリング完了後に必ず以下を実行すること。

```bash
# Java構文チェック（波括弧バランス・クラス宣言）
find src -name "*.java" | xargs -I{} python3 -c "..."

# HTMLテンプレートチェック（タグバランス）
find src/main/resources/templates -name "*.html" | xargs -I{} python3 -c "..."

# Mavenビルド（ネットワーク接続時）
./mvnw compile
./mvnw test
```

※このプロジェクトはJava/MavenプロジェクトのためnpmコマンドではなくMavenを使用する。
エラーや警告が発生した場合は、その原因を調査し修正してから再度実行すること。
すべて成功するまで作業完了として報告してはならない。

### Existing Code Verification
新規実装だけでなく、過去に生成・修正したコードについても影響範囲を確認すること。
変更によって既存機能が壊れていないか検証し、必要に応じてテストを追加・修正すること。

### Testing Policy
以下を遵守すること。

* 新規機能には対応するテストを作成する
* 既存テストが失敗した場合は原因を調査する
* テストを無効化して完了扱いにしてはならない
* テストコードではなく本体コードの修正を優先して検討する
* 一時的な回避策ではなく根本原因を修正する

### Completion Criteria
作業完了の条件は以下をすべて満たすこと。

* 実装完了
* lint成功
* test成功
* 既存機能への影響確認完了
* 追加したコードに対するテスト作成完了

完了報告時には実行したコマンドと結果を簡潔に記載すること。

## DB テーブル構造（参考）

正確な全カラム定義は `src/main/resources/schema-all.sql`（統合スキーマ、初回セットアップ・DB再構築時はこれを実行）と
`DatabaseMigrationRunner.java`（アプリ起動時に自動適用される追加分。IF NOT EXISTSのため実害はないが
schema-all.sqlにも同じ内容をドキュメントとして統合済み）を参照。以下は主要カラムの要約。

外部キー制約は設定していない（削除してもDB上はエラーにならず、関連レコードは孤児として残る。
求職者・求人者は削除ではなく「退職/取引終了」フラグ方式にしているのはそのため。詳細は1-7-4参照）。

### persons（求職者）
- 基本情報: id, no, 氏名（漢字/カナ）, 住所, 最寄り駅, 電話番号各種
- 希望条件: desired_job, desired_type（旧）, desired_types（新・複数選択）, work_location, work_duties,
  specific_days, work_available_hours, work_start_period
- 資格・可否: qual_nursery, qual_cook, qual_care_worker, qual_care_helper, animal_dog_ok/cat_ok/dog_allergy/cat_allergy,
  cooking, smoking, childcare_exp
- 緊急連絡先: emergency_relation, emergency_phone, babysitter_exp, babysitter_avail
- 会費(1-1-7): membership_fee（'有'/'無'）, membership_fee_amount（1550 or 350）
- その他: birth_date, registered_date, line_works, dispatch_customer_id, notes
- **retired_at**: nullなら在職中。値が入っていれば退職者リスト(1-1-8)へ移動（削除ではなく論理フラグ）

### customers（求人者）
- 基本情報: id, no, 氏名（漢字/カナ）, 住所, 最寄り駅, access_time, 電話番号各種
- 担当者: staff_name, staff_phone, staff_notes
- 依頼内容: job_contents, freq_type, freq_temp_date, freq_weekly_days/start/end
- 家族構成: family_adults, family_children
- 紹介経路: introducer_name, intro_route, intro_other_text
- ペット: pet_type, pet_other_text
- 面談: interview_none, interview_date1, interview_date2
- その他: registered_date, notes, access_info
- **retired_at**: nullなら取引中。値が入っていれば元求人先(1-2-4)へ移動（削除ではなく論理フラグ）

### sales / sales_details（売上入力 1-1-3。1明細=1回の紹介・就労を表す）
sales: id, person_id, introduction_date, reception_fee, receipt_no, created_at（レガシーなヘッダー的存在。
実質的な計算・表示は sales_details 側で完結している）

sales_details（1求職者につき最大5件）:
- 基本: id, sales_id, customer_id, detail_order
- 賃金: hourly_wage, hourly_wage_overtime, working_hours, daily_wages（日給。カンマ区切り最大5件）
- **monthlyTotal（賃金総額）** = 時給×勤務時間 + 日給合計。決算表(1-3-1/1-3-3)の紹介手数料計算(×15%)の元になる値。
- **commission/tax** = monthlyTotal×16.5%（賃金総額×16.5%）/ その内訳。稼働管理簿(1-1-5)・レジ一覧(1-6-2)で使用。
- **daily_wage_rate（日給掛け率%、デフォルト16.5）/ salesAmount（売上）** = 時給×勤務時間 + 日給合計×daily_wage_rate
  + チェック済み手数料。1-1-3画面専用の値でmonthlyTotalとは独立（詳細は1-7-4参照）
- 就労期間: introduction_date, work_start_date, work_end_date
- 手数料チェック: reception_fee（求職受付手数料 710円固定 or null）, customer_fee（求人受付手数料 1000円固定 or null）
- 領収書: receipt_no, issued_at（発行時に採番・記録される。採番自体は receipt_no_counter テーブルで一元管理）
- daily_wage_1month, temp_3month（紹介手数料管理簿1-3-1の届出手数料自動転記用）
- remarks（備考。稼働管理簿1-1-5で編集可能）

### introductions（紹介状 1-4）
id, ref_no（採番）, person_id, customer_id, intro_date, start_date, form_data（JSON。賃金形態・交通費等の自由項目）,
emp_period（無期/有期/臨時/日雇い。紹介手数料管理簿1-3-1の届出手数料自動判定に使用）,
emp_status, hire_result, ledger_remarks（求職受付手数料領収書の"RCPT:番号"格納にも流用）, labor_contract,
rishoku_status, henreikin, created_at

### register_records（振込金入力 1-6-1）
id, person_id, work_month, salary, fee（salary×16.5%）, membership_fee, transferred（振込済みフラグ）, memo, created_at

### receipt_no_counter（領収書番号の採番カウンター）
id（固定値1の1行のみ）, next_no。求人者宛領収書(1-5-1)・求職受付手数料領収書(1-5-2)・紹介状経由の領収書、
3種類すべてがこの1テーブルから `UPDATE ... RETURNING` でアトミックに採番する（同時実行・番号重複対策）。

### sancare_net_monthly（手数料収入決算表1-3-3のサンケアネット月別入力）
id, year_month（UNIQUE）, amount

### membership_confirmations（会費1-1-7の月別支払い確認チェック）
id, person_id, work_month, confirmed（UNIQUE(person_id, work_month)）

### customer_requests（求人受付表 1-2-1）/ customer_ledgers（求人管理簿 1-2-2）/ receipts_issued（領収書発行記録）
いずれも詳細は schema-all.sql 参照。
