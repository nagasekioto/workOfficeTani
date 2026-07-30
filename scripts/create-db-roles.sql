-- ============================================================
--  対策1: 最小権限の原則（DBロール分離）
--
--  【なぜ必要か】
--  アプリがPostgreSQLのスーパーユーザー postgres で接続していると、
--  SQLインジェクションが1つ通る、あるいはアプリが乗っ取られた瞬間に
--  以下がすべて可能になる。権限の壁がゼロの状態。
--
--    - DROP DATABASE kaseihu        … 業務データの全消去
--    - 他のデータベースの閲覧
--    - COPY ... FROM PROGRAM 'cmd'  … 任意のコマンド実行（DB経由でPCを乗っ取れる）
--
--  最後の COPY ... FROM PROGRAM はスーパーユーザーだけが使える機能で、
--  これを封じるだけでも被害の上限が大きく下がる。
--
--  【ロール構成】
--  | ロール          | 権限                        | 誰が使うか |
--  |----------------|----------------------------|-----------|
--  | kaseihu_owner  | テーブルの所有者。DDLのみ      | DatabaseMigrationRunner（起動時のみ） |
--  | kaseihu_app    | SELECT/INSERT/UPDATE/DELETE | アプリの通常動作 |
--  | kaseihu_ro     | SELECT のみ                 | バックアップ（pg_dump）・調査 |
--
--  postgres は緊急用に温存する（docs/EMERGENCY_ACCESS.md 参照）。
--
--  引き継ぎ資料の当初案は4分割（admin を分ける）だったが、3つに減らした。
--  アプリは領収書削除・売上明細削除など各画面で正常に DELETE するため、
--  DELETE だけを別ロールに分けると業務が動かなくなる。
--  致命的な被害はスーパーユーザー権限にあるので、そこを外すことを優先する。
--
--  【実行方法】
--  パスワードはこのファイルに書かず、実行時に渡す。
--
--    psql -U postgres -h localhost -d kaseihu ^
--         -v owner_pw="'（所有者のパスワード）'" ^
--         -v app_pw="'（アプリのパスワード）'" ^
--         -v ro_pw="'（参照用のパスワード）'" ^
--         -f scripts\create-db-roles.sql
--
--  【元に戻す】
--  scripts\rollback-db-roles.sql を実行する。
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- ------------------------------------------------------------
-- 1. ロールを作る
--    いずれも SUPERUSER / CREATEDB / CREATEROLE を付けない。
--    付けると分離した意味が無くなる。
-- ------------------------------------------------------------

CREATE ROLE kaseihu_owner LOGIN PASSWORD :owner_pw
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

CREATE ROLE kaseihu_app LOGIN PASSWORD :app_pw
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

CREATE ROLE kaseihu_ro LOGIN PASSWORD :ro_pw
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

-- ------------------------------------------------------------
-- 2. データベースへの接続を許可する
-- ------------------------------------------------------------

GRANT CONNECT ON DATABASE kaseihu TO kaseihu_owner, kaseihu_app, kaseihu_ro;

-- ------------------------------------------------------------
-- 3. 既存テーブル・シーケンスの所有者を kaseihu_owner に移す
--
--    所有者でないと ALTER TABLE ができない。
--    DatabaseMigrationRunner が起動のたびに ALTER TABLE するため、
--    所有者を用意しないとアプリが起動しなくなる。
--
--    テーブル名を列挙せず catalog から回すのは、
--    テーブルが増えてもこのスクリプトを直さなくて済むようにするため。
-- ------------------------------------------------------------

DO $$
DECLARE
    obj record;
BEGIN
    FOR obj IN
        SELECT tablename AS name FROM pg_tables WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO kaseihu_owner', obj.name);
    END LOOP;

    FOR obj IN
        SELECT sequencename AS name FROM pg_sequences WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO kaseihu_owner', obj.name);
    END LOOP;

    FOR obj IN
        SELECT table_name AS name FROM information_schema.views WHERE table_schema = 'public'
    LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO kaseihu_owner', obj.name);
    END LOOP;
END
$$;

-- スキーマ自体も所有者に移す。
-- CREATE TABLE（新しいテーブルの追加）ができるようにするため。
ALTER SCHEMA public OWNER TO kaseihu_owner;

-- ------------------------------------------------------------
-- 4. 権限を与える
-- ------------------------------------------------------------

-- スキーマを見る権限（これが無いとテーブル名すら解決できない）
GRANT USAGE ON SCHEMA public TO kaseihu_app, kaseihu_ro;

-- アプリ: 業務データの読み書き。DDLは与えない
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kaseihu_app;
-- BIGSERIAL の採番に必要
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO kaseihu_app;

-- 参照用: 閲覧と pg_dump のみ
GRANT SELECT ON ALL TABLES IN SCHEMA public TO kaseihu_ro;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO kaseihu_ro;

-- ------------------------------------------------------------
-- 5. 【最重要】今後追加されるテーブルにも自動で権限が付くようにする
--
--    ここを忘れると、次にマイグレーションで新しいテーブルが増えたとき、
--    kaseihu_owner が作ったそのテーブルに kaseihu_app の権限が無く、
--    **その画面だけが実行時に権限エラーで落ちる**。
--    しかもテストでは（postgres で動かしていると）再現しないため気付きにくい。
--
--    「FOR ROLE kaseihu_owner」を付けるのが要点。
--    これが無いと「このスクリプトを実行した本人(postgres)が作ったテーブル」
--    にしか適用されず、実際に作るのは kaseihu_owner なので効かない。
-- ------------------------------------------------------------

ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO kaseihu_app;
ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO kaseihu_app;

ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    GRANT SELECT ON TABLES TO kaseihu_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO kaseihu_ro;

-- ------------------------------------------------------------
-- 6. 余計な権限を取り上げる
--    PostgreSQL 15 以降、public スキーマの CREATE は既定で PUBLIC に
--    付かなくなったが、古いバージョンから移行してきた場合に残ることがある。
--    明示的に外しておく。
-- ------------------------------------------------------------

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

COMMIT;

-- ------------------------------------------------------------
-- 7. 結果の確認
-- ------------------------------------------------------------

\echo ''
\echo '===== 作成したロール（rolsuper が f であること）====='
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin
FROM pg_roles WHERE rolname LIKE 'kaseihu%' ORDER BY rolname;

\echo ''
\echo '===== テーブル所有者（kaseihu_owner になっていること）====='
SELECT tableowner, COUNT(*) AS tables
FROM pg_tables WHERE schemaname = 'public' GROUP BY tableowner;

\echo ''
\echo '===== 今後追加されるテーブルへの既定権限（空でないこと）====='
SELECT pg_get_userbyid(defaclrole) AS for_role, defaclobjtype, defaclacl
FROM pg_default_acl;
