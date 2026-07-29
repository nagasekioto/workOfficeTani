# SQLインジェクション監査報告書

実施日: 2026-07-29
対象: 家政婦紹介事務所 人物管理システム（コミット `e349c58` 時点）
技術構成: Java 17 / Spring Boot 3.5 / **Spring Data JDBC**（JPAではない） / PostgreSQL / Thymeleaf

---

## 1. 結論

**SQLインジェクションの危険箇所は0件でした。**

SQLを発行しているすべての箇所（75件）を機械的に列挙し、1件ずつ確認した結果、
外部入力をSQL文字列に直接連結している箇所は**1件も存在しませんでした**。
すべてプレースホルダ（`:paramName` または `?`）を経由してバインドされています。

事前情報にあった「`@Query` は `:paramName` 形式のプレースホルダを使用済み」は**正しい**ことを確認しました。
加えて、`@Query` 以外の経路（`jdbcTemplate` 直接呼び出し・`Statement` 直接使用）についても
同様に安全であることを確認しています。

| 分類 | 件数 | 危険 | 要検討 | 安全 |
|------|-----:|-----:|-------:|-----:|
| `@Query` アノテーション | 17 | 0 | 1 | 16 |
| `jdbcTemplate` 呼び出し | 16 | 0 | 0 | 16 |
| `Statement.execute()`（DDL） | 42 | 0 | 0 | 42 |
| 派生クエリの `Sort` / `Pageable` | 0 | — | — | — |
| 動的な ORDER BY / テーブル名組立 | 0 | — | — | — |
| **合計** | **75** | **0** | **1** | **74** |

SQL以外の注入リスクも併せて調査し、**1件を修正しました**（→ 第3章）。

---

## 2. 調査方法

「たぶん全部見た」を避けるため、以下の複数パターンで機械的に全件抽出してから、1件ずつ目視確認しました。

| # | 検索対象 | 目的 |
|---|---------|------|
| 1 | `@Query` | Spring Data JDBC の明示クエリ |
| 2 | `jdbcTemplate` / `JdbcTemplate` / `NamedParameterJdbcTemplate` / `JdbcClient` の全メソッド | `query` `queryForObject` `queryForList` `update` `execute` `batchUpdate` |
| 3 | `Statement` / `createStatement` / `PreparedStatement` | JDBC直接使用 |
| 4 | `"SELECT` `"INSERT` `"UPDATE` `"DELETE` `"WITH` | SQL文字列リテラルの全出現 |
| 5 | `+ 変数 + "` の連結パターン | SQL文への変数埋め込み |
| 6 | `String.format` / `StringBuilder` | 動的SQL組立 |
| 7 | `Sort` / `Pageable` | 動的ソート項目の外部入力 |
| 8 | `th:utext` / `[(${...})]` | Thymeleafのエスケープなし出力（XSS） |
| 9 | `setHeader` / `Content-Disposition` | HTTPヘッダーインジェクション |

対象ファイル: Java 40ファイル、Thymeleafテンプレート 46ファイル（全件）。

---

## 3. 修正した箇所

### 3-1. Content-Disposition ヘッダーへの未検証値の埋め込み

**該当**: `src/main/java/jp/co/housekeeping/person_management/controller/RegisterController.java`
**画面**: 1-6-3 手数料管理簿（`/register/fee-ledger/pdf`）

**修正前**:

```java
public void feeLedgerPdf(@RequestParam String month, ...) {
    if (!checkAuth(session)) { response.sendError(401); return; }
    List<RegisterRecord> raw = registerRecordRepository.findByWorkMonth(month);
    ...
    response.setHeader("Content-Disposition", "inline; filename=fee-ledger-" + month + ".pdf");
```

**問題点**:
`month` を一切検証せずHTTPレスポンスヘッダーに文字列連結していました。
SQLインジェクションではありません（`findByWorkMonth` はプレースホルダ経由のため安全）が、
CRLF（改行コード）を含む値を渡された場合のヘッダーインジェクションの典型パターンです。

同じメソッド内の `buildPersonToCustomerListMap` では `YearMonth.parse` で検証しているのに、
ヘッダー出力だけ検証を通っていないという**不整合**があり、他のPDF出力メソッド
（`ReportMenuController` の `pdf` / `receptionPdf` / `settlementPdf`）が
すべて `YearMonth.parse` を通しているのと比べても、この1箇所だけが例外でした。

> 現行のSpring Boot組込Tomcatは `setHeader` に制御文字が含まれると例外で弾くため、
> 現時点で実際に攻撃が成立する可能性は高くありません。
> しかし「サーブレットコンテナの実装に守られているだけ」という状態であり、
> 設定変更やバージョン変更で防御が崩れます。アプリ側で担保すべきと判断しました。

**修正後**:

```java
// monthはヘッダー出力に使うため、ヘッダーインジェクション対策として形式検証を行う
java.time.YearMonth ym;
try {
    ym = java.time.YearMonth.parse(month);
} catch (java.time.format.DateTimeParseException e) {
    response.sendError(400);
    return;
}
String normalizedMonth = ym.toString();
```

以降の `findByWorkMonth` / `buildPersonToCustomerListMap` / `createFeeLedgerPdf` /
ヘッダー出力は、すべて検証済みの `normalizedMonth` を使用します。

**副作用の確認**:

| 観点 | 結果 |
|------|------|
| 正常系の動作変更 | **なし**。画面は `<input type="month">` の値（`yyyy-MM` 形式）を渡すため、`normalizedMonth` は `month` と常に同一値 |
| 画面からの到達経路 | `register-fee-ledger.html:46` の PDF出力リンクのみ。`selectedMonth != null` のときだけ表示され、必ず妥当な値を渡す |
| 異常系の動作変更 | 不正な形式の場合、従来の「空のPDFを200で返す」から「400を返す」に変更。画面から到達する経路はない |
| 既存テストへの影響 | なし（30件すべて成功） |

**追加したテスト**: `src/test/java/.../controller/RegisterControllerFeeLedgerPdfTest.java`

1. 未認証でのアクセス → 401
2. `month=2026-07`（正常値）→ 200、`Content-Disposition` に `fee-ledger-2026-07.pdf` が含まれる
3. `month=abc`（不正な形式）→ 400
4. `month` にCRLFを含む値 → 400、かつ `X-Injected` ヘッダーが存在しない

---

## 4. 要検討として記録した箇所（今回は修正せず）

### 4-1. `RegisterRecordRepository.findByYear` の LIKE ワイルドカード

**該当**: `src/main/java/jp/co/housekeeping/person_management/repository/RegisterRecordRepository.java:18-19`

```java
@Query("SELECT * FROM register_records WHERE work_month LIKE :yearPrefix ORDER BY work_month, created_at")
List<RegisterRecord> findByYear(@Param("yearPrefix") String yearPrefix);
```

**SQLインジェクションは不可能です**（`:yearPrefix` はプレースホルダ経由）。

ただし `LIKE` 句であるにもかかわらず、ワイルドカード文字（`%` `_`）をエスケープする仕組みがありません。
現時点では**このメソッドはコードベース全体のどこからも呼び出されていません**（宣言のみの未使用コード）
ため、実害はありません。

**将来この関数を使う場合の注意**（このために記録しています）:
「年で絞り込む」画面などを作り、入力値をそのまま `yearPrefix` に渡す実装にすると、
入力欄に `%` を1文字入れるだけで**全年度のレジ記録が表示されます**。
情報の見えすぎ（意図しない範囲のデータ露出）が起きます。

**対処方針（使用時に実施）**:
- 呼び出し側で `\` `%` `_` をエスケープし、`LIKE ... ESCAPE '\'` を明示する、または
- `work_month LIKE :yearPrefix` を `SUBSTRING(work_month, 1, 4) = :year` の完全一致に変更する

今回は未使用のため、変更による予期せぬ影響を避けて**現状維持**としました。

### 4-2. `DatabaseMigrationRunner` の `Statement` 直接使用

**該当**: `src/main/java/jp/co/housekeeping/person_management/DatabaseMigrationRunner.java`

42件の `stmt.execute(...)` はすべて固定文字列のDDL（`ALTER TABLE ... IF NOT EXISTS` / `CREATE TABLE ... IF NOT EXISTS`）で、
外部入力は一切関与しません。**現状は安全**です。

ただし `Statement`（`PreparedStatement` ではない）を使う設計自体が、
将来誰かが同じ書き方で外部入力混じりのSQLを書いてしまう入口になり得ます。
第6章のガードレールで対応します。

---

## 5. 安全と確認した箇所の一覧

### 5-1. `@Query`（17件・全件プレースホルダ）

| ファイル:行 | SQL要約 |
|---|---|
| SalesRepository.java:15 | `SELECT * FROM sales WHERE person_id = :personId ORDER BY id` |
| SalesDetailRepository.java:16 | `SELECT * FROM sales_details WHERE sales_id = :salesId ORDER BY COALESCE(detail_order,0), id` |
| SalesDetailRepository.java:19 | `SELECT * FROM sales_details WHERE introduction_date >= :startDate AND <= :endDate` |
| SalesDetailRepository.java:22 | `SELECT * FROM sales_details WHERE work_start_date >= :startDate AND <= :endDate` |
| RegisterRecordRepository.java:15 | `SELECT * FROM register_records WHERE work_month = :workMonth` |
| RegisterRecordRepository.java:18 | `SELECT * FROM register_records WHERE work_month LIKE :yearPrefix`（→ 4-1） |
| RegisterRecordRepository.java:21 | `SELECT * FROM register_records WHERE person_id = :personId` |
| IntroductionRepository.java:12 | `SELECT COALESCE(MAX(CAST(ref_no AS INTEGER)),0) FROM introductions WHERE ref_no ~ '^[0-9]+$'`（正規表現はリテラル固定） |
| IntroductionRepository.java:15 | `SELECT * FROM introductions ORDER BY created_at DESC`（パラメータなし） |
| IntroductionRepository.java:18 | `SELECT * FROM introductions WHERE person_id = :personId` |
| IntroductionRepository.java:21 | `SELECT * FROM introductions WHERE customer_id = :customerId` |
| ReceiptsIssuedRepository.java:16 | `SELECT * FROM receipts_issued WHERE sales_detail_id = :detailId LIMIT 1` |
| ReceiptsIssuedRepository.java:19 | `SELECT * FROM receipts_issued WHERE TO_CHAR(created_at,'YYYY-MM') = :month` |
| ReceiptsIssuedRepository.java:22 | `SELECT * FROM receipts_issued WHERE person_id = :personId` |
| ReceiptsIssuedRepository.java:25 | `SELECT * FROM receipts_issued WHERE customer_id = :customerId` |
| CustomerRequestRepository.java:15 | `SELECT * FROM customer_requests WHERE customer_id = :customerId` |
| CustomerRequestRepository.java:18 | `SELECT * FROM customer_requests ORDER BY created_at DESC`（パラメータなし） |

`CustomerRepository` / `PersonRepository` は `@Query` を持たず、標準CRUDのみ。

### 5-2. `jdbcTemplate`（16件・全件 `?` プレースホルダ）

| ファイル:行 | メソッド | SQL要約 |
|---|---|---|
| PermanentDeleteController.java:116 | update | `DELETE FROM membership_confirmations WHERE person_id = ?` |
| PermanentDeleteController.java:120 | update | `UPDATE customer_requests SET candidate_person_id = NULL WHERE candidate_person_id = ?` |
| PermanentDeleteController.java:152 | update | `DELETE FROM customer_ledgers WHERE customer_id = ?` |
| PermanentDeleteController.java:155 | update | `UPDATE persons SET dispatch_customer_id = NULL WHERE dispatch_customer_id = ?` |
| PersonController.java:371 | query | `SELECT person_id, confirmed FROM membership_confirmations WHERE work_month = ?` |
| PersonController.java:406 | update | `INSERT INTO membership_confirmations ... ON CONFLICT ... DO UPDATE` |
| ReceiptMenuController.java:1083 | queryForObject | `UPDATE receipt_no_counter SET next_no = next_no+1 WHERE id=1 RETURNING next_no-1`（固定id） |
| ReportMenuController.java:199 | update | `INSERT INTO sancare_net_monthly ... ON CONFLICT ... DO UPDATE` |
| ReportMenuController.java:280 | update | `UPDATE sales_details SET daily_wage_1month=?, temp_3month=? WHERE id=?` |
| ReportMenuController.java:346 | queryForObject | `SELECT last_name_kanji \|\| ' ' \|\| first_name_kanji FROM persons WHERE id = ?` |
| ReportMenuController.java:489 | queryForObject | `SELECT COUNT(*) FROM sales_details WHERE ... EXTRACT(YEAR...)=? AND EXTRACT(MONTH...)=?` |
| ReportMenuController.java:500 | queryForObject | `SELECT COALESCE(SUM(temp_3month),0) FROM sales_details WHERE ...` |
| ReportMenuController.java:511 | queryForObject | `SELECT COALESCE(SUM(daily_wage_1month),0) FROM sales_details WHERE ...` |
| ReportMenuController.java:523 | queryForObject | `SELECT COUNT(*) FROM sales_details WHERE reception_fee=710 AND ...` |
| ReportMenuController.java:535 | queryForList | `SELECT emp_period FROM introductions WHERE person_id=? AND customer_id=? LIMIT 1` |
| ReportMenuController.java:614 | queryForObject | `SELECT amount FROM sancare_net_monthly WHERE year_month = ?` |

`EXTRACT(YEAR/MONTH FROM ...)` の比較値は `ym.getYear()` / `ym.getMonthValue()`（`int` 型）で、
SQL構文自体は完全なリテラルです。

### 5-3. `Statement.execute()`（42件）

`DatabaseMigrationRunner.java` のDDLのみ。全件が固定文字列。→ 4-2 参照。

### 5-4. 動的ソート・動的テーブル名

- `Sort` / `Pageable` を受け取るメソッド: **0件**
- 外部入力によるカラム名・テーブル名・ORDER BY の組立: **0件**

`CustomerRepository` / `PersonRepository` は `CrudRepository` のみ実装（`PagingAndSortingRepository` 不使用）。

---

## 6. SQL以外の注入リスク（併せて調査）

### 6-1. XSS（クロスサイトスクリプティング）

| 調査対象 | 結果 |
|---------|------|
| `th:utext`（エスケープなし出力） | **0件**。全46テンプレートで不使用 |
| `[(${...})]`（エスケープなしインライン） | **0件** |
| `th:onclick` への値埋め込み | 3箇所（`receipt-customer-list.html:65`、`receipt-jobseeker-list.html:72,83`）。いずれもDBの数値IDのみで、氏名等の自由入力は埋め込んでいない → 安全 |
| `<script>` 内のインライン化 | `customer-request-form.html:267` の1箇所。Thymeleaf 3は `<script>` 内を自動でJavaScriptインライン化しJSONエスケープするため安全 |

**Thymeleafのデフォルトのエスケープに依存しています。**
今後 `th:utext` を使う必要が出た場合は、必ずレビューを通してください。

### 6-2. ヘッダーインジェクション

`Content-Disposition` への値埋め込み13箇所を全件確認。
1件（`RegisterController.java:419`）を修正済み（→ 3-1）。残り12件は以下の理由で安全です。

- `Long` 型で受けている（Springがパース失敗時に400を返す）: `SalesController.java:272`
- `YearMonth.parse` 済み、または固定文字列: `ReportMenuController.java:100,138,228` ほか
- `URLEncoder.encode` 適用済み: `IntroductionController.java:343`、`ReceiptMenuController.java:535`
- boolean の比較結果のみ埋め込み: `CustomerController.java:482`、`PersonController.java:569`

### 6-3. 未調査（今回のスコープ外・別途推奨）

- **ZIP一括出力のエントリ名生成**（`IntroductionController.exportPdfZip`、`ReceiptMenuController.issuedListExportPdf`）
  氏名など自由入力をZIPエントリ名に使う場合、パストラバーサル（`../`）の観点で別途監査が必要です。
  今回はHTTPヘッダー部のみ確認しました。

---

## 7. 再発防止のガードレール

コードレビュー時のチェック項目として運用してください。

- ☐ SQL文字列に `+` で変数を連結していないか（`?` または `:name` を使うこと）
- ☐ `ORDER BY` / テーブル名 / カラム名を外部入力から組み立てていないか
  （これらはプレースホルダにできないため、**許可リストによる照合**が必須）
- ☐ `LIKE` を使う場合、`%` `_` `\` をエスケープしているか
- ☐ 新しい `Statement` の直接使用を追加していないか（`PreparedStatement` か `jdbcTemplate` を使うこと）
- ☐ `@RequestParam` の値を、検証せずにHTTPヘッダー・ファイル名・PDF出力に埋め込んでいないか
- ☐ `th:utext` を新たに使っていないか

---

## 8. 「唯一の命綱」チェック（対策3分）

対策3は認証情報を扱わないため該当項目は少ないですが、以下は該当します。

| 守るもの | 命綱 | ❌ 危険な置き方 | ✅ 正しい置き方 |
|---------|------|----------------|----------------|
| 「安全であること」の根拠 | この監査報告書 | リポジトリ内だけ（リポジトリごと改ざんされれば根拠も書き換えられる） | 監査実施日のコミットハッシュ（`e349c58`）を記録し、報告書は別媒体にも保管 |
| 再発防止 | 第7章のチェック項目 | 報告書の中にだけ書いて誰も見ない | プルリクエストのテンプレート等、レビュー時に必ず目に入る場所へ転記 |

---

## 改訂履歴

| 日付 | 内容 |
|------|------|
| 2026-07-29 | 初版作成。コミット `e349c58` 時点の全SQL発行箇所75件を監査 |
