-- schema-update-11.sql
-- 領収書番号(receipt_no)専用の採番カウンターテーブル
--
-- これまでは「sales_details.receipt_no のMAXを数えて+1する」方式で、
-- 求人者宛領収書(1-5-1)・求職受付手数料領収書(1-5-2)・紹介状経由の
-- 領収書(/jobseeker-receipt/pdf-intro) の3箇所が同じカウンターを
-- 参照していた。しかし紹介状経由のものだけ結果を
-- introductions.ledger_remarks に保存していたため、そのMAX計算から
-- 見えず、番号が重複する不具合があった。
--
-- 本テーブルを唯一の採番元とし、UPDATE ... RETURNING で
-- アトミックに次番号を払い出すことで、重複と競合の両方を解消する。

CREATE TABLE IF NOT EXISTS receipt_no_counter (
    id      SMALLINT PRIMARY KEY DEFAULT 1,
    next_no INTEGER NOT NULL,
    CONSTRAINT receipt_no_counter_single_row CHECK (id = 1)
);

-- 初期値は「既存データ(sales_details.receipt_no と
-- introductions.ledger_remarks の "RCPT:" 部分)のうち最大の番号 + 1」
-- とする。既に行が存在する場合は何もしない。
INSERT INTO receipt_no_counter (id, next_no)
SELECT 1, GREATEST(
    COALESCE((SELECT MAX(CAST(receipt_no AS INTEGER)) FROM sales_details WHERE receipt_no ~ '^[0-9]+$'), 0),
    COALESCE((SELECT MAX(CAST(SUBSTRING(ledger_remarks FROM 6) AS INTEGER)) FROM introductions WHERE ledger_remarks ~ '^RCPT:[0-9]+$'), 0)
) + 1
ON CONFLICT (id) DO NOTHING;
