-- ============================================================
-- drop-unused-schedules-table.sql
--
-- 【背景】
-- schedules テーブル（曜日・時間帯の予定を保持する古いテーブル）は、
-- 現在の 1-1-2 スケジュール画面（ScheduleController / person-schedule.html）
-- からも、その他のどの画面・処理からも一切参照されていないことを確認済み。
-- 現在の1-1-2画面は introductions テーブルのJSONデータから動的に空き状況を
-- 計算する方式に置き換わっており、このテーブルは開発初期の名残と考えられる。
--
-- このテーブルに古いデータが残っていると、求人者・求職者の「完全に削除」機能が
-- 外部キー制約違反で失敗する原因になっていた（例: customer_id=13 のケース）。
--
-- 【実行方法】既存のデータベースに対して1回だけ実行してください。
--   & "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d kaseihu -f "C:\workOfficeTani\src\main\resources\drop-unused-schedules-table.sql"
--
-- 新規に環境構築する場合は、schema-bootstrap.sql が既にこのテーブルを
-- 作成しないよう修正済みのため、このファイルの実行は不要（対象は既存DBのみ）。
-- ============================================================

DROP TABLE IF EXISTS public.schedules CASCADE;
DROP SEQUENCE IF EXISTS public.schedules_id_seq;
