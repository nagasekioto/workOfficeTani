-- receipt_no を一旦クリアして「未発行」状態に戻す
-- ※ 実際に領収書PDFを発行済みのレコードは手動で receipt_no を再設定してください
UPDATE sales_details SET receipt_no = NULL;
