# CLAUDE.md - 家政婦紹介事務所 人物管理システム

## プロジェクト概要

- **システム名**: 家政婦紹介事務所 人物管理システム
- **技術スタック**: Java 17 / Spring Boot 3.5 / Spring Data JDBC / Thymeleaf / PostgreSQL / iTextPDF
- **ビルドツール**: Maven
- **DB**: person_management (PostgreSQL, localhost:5432)
- **認証**: セッションベース（パスワード: 7136）

## 用語定義

| 用語 | 意味 |
|------|------|
| 求職者 | 家政婦（家事・介護をする人） |
| 求人者 | 家政婦を雇う人（雇用主） |
| 私（運用者） | 仲介業者（このシステムの運営者） |

## 画面構成

```
/login            ログイン
/menu             初期画面
  /person-menu    1-1 求職者管理
    /person/register       1-1-1 名簿入力
    /person/schedule       1-1-2 スケジュール
    /person/sales          1-1-3 売上入力
    /person/report         1-1-4 管理簿入力（準備中）
    /person/working-ledger 1-1-5 稼働管理簿
  /customer-menu  1-2 求人者管理
    /customer/register     1-2-1 名簿入力
    /customer/report       1-2-2 管理簿入力（準備中）
  /receipt        領収書出力
```

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

### persons（求職者）
- id, no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji
- postal_code, address1, address2, address3, nearest_line, nearest_station
- home_phone, fax_phone, mobile_phone
- desired_job, desired_type, introducer
- qual_nursery, qual_cook, qual_care_worker, qual_care_helper
- animal_dog_ok, animal_cat_ok, animal_dog_allergy, animal_cat_allergy
- cooking, smoking, childcare_exp
- birth_date, registered_date
- line_works（追加）

### customers（求人者）
- id, no, last_name_kana, first_name_kana, last_name_kanji, first_name_kanji
- postal_code, address1, address2, address3, nearest_line, nearest_station
- home_phone, fax_phone, mobile_phone, birth_date, registered_date, notes

### sales（売上ヘッダー）
- id, person_id, introduction_date, reception_fee, receipt_no, created_at

### sales_details（売上明細 最大5件）
- id, sales_id, customer_id, hourly_wage, working_hours, monthly_total, commission, tax, detail_order
- work_start_date, work_end_date（就労月日）
- daily_wage_count（日給枠数）
- reception_fee（受付料 710円固定）
- customer_fee（求人受付手数料 1000円固定）
- hourly_wage_overtime（時給残業）
- daily_wages（日給JSON: 最大5枠）
- remarks（備考）

### working_ledger（稼働管理簿）
- id, receipt_no（採番用）
