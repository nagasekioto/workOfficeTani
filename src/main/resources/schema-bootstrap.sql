-- ============================================================
-- schema-bootstrap.sql
-- 家政婦紹介事務所 人物管理システム 初期スキーマ（ゼロからの構築用）
--
-- 【重要】このファイルは、これまでリポジトリに存在していなかった
-- 「土台となるテーブル」(persons, customers, sales, sales_details,
-- schedules 等) を作成するためのものです。
-- schema-all.sql は既存テーブルへの追加・変更（ALTER/CREATE IF NOT EXISTS）
-- のみを前提としており、この bootstrap ファイルなしでは
-- 新しいパソコンでゼロからデータベースを作ることができませんでした。
--
-- 実行順序（新しいパソコンでの初回セットアップ時）:
--   1. schema-bootstrap.sql   ← このファイル（土台のテーブルを作成）
--   2. schema-all.sql         ← 追加カラム・追加テーブルを反映
--
-- 本番データベースの実際のスキーマ構造（pg_dump --schema-only）を元に
-- 作成しているため、IF NOT EXISTS 等は付けていません
-- （空のデータベースに対して1回だけ実行する想定）。
-- ============================================================

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- ─── persons（求職者）───────────────────────────────────
CREATE TABLE IF NOT EXISTS public.persons (
    id integer NOT NULL,
    no integer,
    last_name_kana character varying(50),
    first_name_kana character varying(50),
    last_name_kanji character varying(50),
    first_name_kanji character varying(50),
    postal_code character varying(8),
    address1 text,
    address2 text,
    address3 text,
    nearest_line character varying(100),
    nearest_station character varying(100),
    home_phone character varying(20),
    fax_phone character varying(20),
    mobile_phone character varying(20),
    desired_job character varying(20),
    desired_type character varying(20),
    introducer text,
    qual_nursery boolean DEFAULT false,
    qual_cook boolean DEFAULT false,
    qual_care_worker boolean DEFAULT false,
    qual_care_helper boolean DEFAULT false,
    animal_dog_ok boolean DEFAULT false,
    animal_cat_ok boolean DEFAULT false,
    animal_dog_allergy boolean DEFAULT false,
    animal_cat_allergy boolean DEFAULT false,
    cooking character varying(20),
    smoking character varying(20),
    childcare_exp character varying(20),
    birth_date date,
    registered_date date,
    line_works boolean DEFAULT false,
    dispatch_customer_id bigint,
    work_location text,
    work_duties text,
    desired_types text,
    specific_days text,
    work_available_hours text,
    work_start_period text,
    emergency_relation text,
    emergency_phone text,
    babysitter_exp text,
    babysitter_avail text,
    notes text,
    membership_fee text,
    membership_fee_amount integer,
    retired_at date
);

CREATE SEQUENCE IF NOT EXISTS public.persons_id_seq
    AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.persons_id_seq OWNED BY public.persons.id;
ALTER TABLE ONLY public.persons ALTER COLUMN id SET DEFAULT nextval('public.persons_id_seq'::regclass);

ALTER TABLE ONLY public.persons
    DROP CONSTRAINT IF EXISTS persons_pkey,
    ADD CONSTRAINT persons_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.persons
    DROP CONSTRAINT IF EXISTS persons_no_key,
    ADD CONSTRAINT persons_no_key UNIQUE (no);

-- ─── customers（求人者）─────────────────────────────────
CREATE TABLE IF NOT EXISTS public.customers (
    id integer NOT NULL,
    no integer,
    last_name_kana character varying(50),
    first_name_kana character varying(50),
    last_name_kanji character varying(50),
    first_name_kanji character varying(50),
    postal_code character varying(8),
    address1 text,
    address2 text,
    address3 text,
    nearest_line character varying(100),
    nearest_station character varying(100),
    home_phone character varying(20),
    fax_phone character varying(20),
    mobile_phone character varying(20),
    birth_date date,
    registered_date date,
    notes text,
    access_time text,
    staff_name text,
    staff_notes text,
    job_contents text,
    freq_type text,
    freq_temp_date text,
    freq_weekly_days text,
    freq_weekly_start text,
    freq_weekly_end text,
    family_adults integer,
    family_children integer,
    introducer_name text,
    intro_route text,
    intro_other_text text,
    pet_type text,
    pet_other_text text,
    interview_none boolean,
    interview_date1 text,
    interview_date2 text,
    staff_phone text,
    retired_at date
);

CREATE SEQUENCE IF NOT EXISTS public.customers_id_seq
    AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;
ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);

ALTER TABLE ONLY public.customers
    DROP CONSTRAINT IF EXISTS customers_pkey,
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.customers
    DROP CONSTRAINT IF EXISTS customers_no_key,
    ADD CONSTRAINT customers_no_key UNIQUE (no);

-- ─── sales（売上）────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.sales (
    id integer NOT NULL,
    person_id integer,
    introduction_date date,
    reception_fee integer,
    receipt_no character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE IF NOT EXISTS public.sales_id_seq
    AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.sales_id_seq OWNED BY public.sales.id;
ALTER TABLE ONLY public.sales ALTER COLUMN id SET DEFAULT nextval('public.sales_id_seq'::regclass);

ALTER TABLE ONLY public.sales
    DROP CONSTRAINT IF EXISTS sales_pkey,
    ADD CONSTRAINT sales_pkey PRIMARY KEY (id);

-- ─── sales_details（売上明細）────────────────────────────
CREATE TABLE IF NOT EXISTS public.sales_details (
    id integer NOT NULL,
    sales_id integer,
    customer_id integer,
    hourly_wage integer,
    working_hours numeric(5,1),
    monthly_total integer,
    commission integer,
    tax integer,
    detail_order integer,
    work_start_date date,
    work_end_date date,
    reception_fee integer DEFAULT 710,
    customer_fee integer,
    hourly_wage_overtime integer,
    daily_wages text,
    remarks text,
    introduction_date date,
    receipt_no character varying(10),
    issued_at timestamp without time zone,
    daily_wage_1month integer DEFAULT 0,
    temp_3month integer DEFAULT 0,
    daily_wage_rate numeric(5,2) DEFAULT 16.5,
    sales_amount integer DEFAULT 0
);

CREATE SEQUENCE IF NOT EXISTS public.sales_details_id_seq
    AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.sales_details_id_seq OWNED BY public.sales_details.id;
ALTER TABLE ONLY public.sales_details ALTER COLUMN id SET DEFAULT nextval('public.sales_details_id_seq'::regclass);

ALTER TABLE ONLY public.sales_details
    DROP CONSTRAINT IF EXISTS sales_details_pkey,
    ADD CONSTRAINT sales_details_pkey PRIMARY KEY (id);

-- ─── schedules（スケジュール）────────────────────────────
CREATE TABLE IF NOT EXISTS public.schedules (
    id integer NOT NULL,
    person_id integer,
    customer_id integer,
    day_of_week character varying(10),
    time_slot time without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE IF NOT EXISTS public.schedules_id_seq
    AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.schedules_id_seq OWNED BY public.schedules.id;
ALTER TABLE ONLY public.schedules ALTER COLUMN id SET DEFAULT nextval('public.schedules_id_seq'::regclass);

ALTER TABLE ONLY public.schedules
    DROP CONSTRAINT IF EXISTS schedules_pkey,
    ADD CONSTRAINT schedules_pkey PRIMARY KEY (id);

-- ─── 外部キー制約（すべての土台テーブルが揃った後で追加）──────
ALTER TABLE ONLY public.sales
    DROP CONSTRAINT IF EXISTS sales_person_id_fkey,
    ADD CONSTRAINT sales_person_id_fkey FOREIGN KEY (person_id) REFERENCES public.persons(id);

ALTER TABLE ONLY public.sales_details
    DROP CONSTRAINT IF EXISTS sales_details_sales_id_fkey,
    ADD CONSTRAINT sales_details_sales_id_fkey FOREIGN KEY (sales_id) REFERENCES public.sales(id);

ALTER TABLE ONLY public.sales_details
    DROP CONSTRAINT IF EXISTS sales_details_customer_id_fkey,
    ADD CONSTRAINT sales_details_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id);

ALTER TABLE ONLY public.schedules
    DROP CONSTRAINT IF EXISTS schedules_person_id_fkey,
    ADD CONSTRAINT schedules_person_id_fkey FOREIGN KEY (person_id) REFERENCES public.persons(id);

ALTER TABLE ONLY public.schedules
    DROP CONSTRAINT IF EXISTS schedules_customer_id_fkey,
    ADD CONSTRAINT schedules_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id);

-- ============================================================
-- 以降、schema-all.sql / DatabaseMigrationRunner.java が
-- customer_requests, customer_ledgers, introductions, receipts_issued,
-- register_records, membership_confirmations, sancare_net_monthly,
-- working_ledger, receipt_sequence, receipt_no_counter
-- などの残りのテーブルを CREATE TABLE IF NOT EXISTS で作成します。
-- このファイルの後に schema-all.sql を実行してください。
-- ============================================================
