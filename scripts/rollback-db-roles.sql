-- ============================================================
--  対策1（DBロール分離）を元に戻す
--
--  create-db-roles.sql で行った変更を取り消し、
--  すべてのテーブルを postgres 所有に戻してロールを削除する。
--
--  【使うのはどんな時か】
--  - ロール分離が原因でアプリが起動しなくなった
--  - 権限エラーで一部の画面が動かない
--
--  【実行方法】
--    psql -U postgres -h localhost -d kaseihu -f scripts\rollback-db-roles.sql
--
--  【実行後にやること】
--  環境変数を元に戻してからアプリを起動し直す。
--    - DB_USER を postgres に（または未設定に）
--    - DB_PASSWORD を postgres のパスワードに
--    - DB_MIGRATION_USER / DB_MIGRATION_PASSWORD を削除
--
--  ⚠️ このスクリプトは業務データを一切消しません。
--     所有者と権限だけを戻します。
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- ------------------------------------------------------------
-- 1. 所有権を postgres に戻す
--    ロールを削除する前に必ず行う。
--    所有物が残っているロールは DROP ROLE できないため。
-- ------------------------------------------------------------

DO $$
DECLARE
    obj record;
BEGIN
    FOR obj IN
        SELECT tablename AS name FROM pg_tables
        WHERE schemaname = 'public' AND tableowner = 'kaseihu_owner'
    LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO postgres', obj.name);
    END LOOP;

    FOR obj IN
        SELECT sequencename AS name FROM pg_sequences
        WHERE schemaname = 'public' AND sequenceowner = 'kaseihu_owner'
    LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO postgres', obj.name);
    END LOOP;

    FOR obj IN
        SELECT table_name AS name FROM information_schema.views
        WHERE table_schema = 'public'
    LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO postgres', obj.name);
    END LOOP;
END
$$;

ALTER SCHEMA public OWNER TO postgres;

-- ------------------------------------------------------------
-- 2. 既定権限の設定を取り消す
--    これを残したままロールを削除すると、
--    pg_default_acl に消えない参照が残って DROP ROLE が失敗する。
-- ------------------------------------------------------------

ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLES FROM kaseihu_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    REVOKE USAGE, SELECT ON SEQUENCES FROM kaseihu_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    REVOKE SELECT ON TABLES FROM kaseihu_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    REVOKE SELECT ON SEQUENCES FROM kaseihu_ro;

-- ------------------------------------------------------------
-- 3. 付与した権限を取り上げる
-- ------------------------------------------------------------

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM kaseihu_app, kaseihu_ro;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM kaseihu_app, kaseihu_ro;
REVOKE ALL ON SCHEMA public FROM kaseihu_app, kaseihu_ro;
REVOKE ALL ON DATABASE kaseihu FROM kaseihu_owner, kaseihu_app, kaseihu_ro;

COMMIT;

-- ------------------------------------------------------------
-- 4. ロールを削除する
--    トランザクションの外で実行する。
--    残っている所有物があると失敗するため、上の手順を必ず先に済ませること。
-- ------------------------------------------------------------

DROP ROLE IF EXISTS kaseihu_app;
DROP ROLE IF EXISTS kaseihu_ro;
DROP ROLE IF EXISTS kaseihu_owner;

-- ------------------------------------------------------------
-- 5. 結果の確認
-- ------------------------------------------------------------

\echo ''
\echo '===== 残っている kaseihu ロール（0件であること）====='
SELECT rolname FROM pg_roles WHERE rolname LIKE 'kaseihu%';

\echo ''
\echo '===== テーブル所有者（postgres に戻っていること）====='
SELECT tableowner, COUNT(*) AS tables
FROM pg_tables WHERE schemaname = 'public' GROUP BY tableowner;
