-- SCOOTER RENTAL SYSTEM - COMPLETE POSTGRESQL SCHEMA
-- Run ONCE on a NEW, EMPTY database named scooter_rental.
-- Do not run on a database already containing these tables.
-- Schema only: no test accounts, passwords, or ride data are inserted.
-- This includes the five original tables and two backend support tables.

BEGIN;
SET LOCAL search_path TO public;

CREATE TABLE public.maintenance (
    maintenance_id integer NOT NULL,
    scooter_id integer NOT NULL,
    issue_description text NOT NULL,
    reported_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    resolved_date timestamp without time zone,
    status character varying(20) DEFAULT 'pending'::character varying,
    CONSTRAINT maintenance_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'in_progress'::character varying, 'resolved'::character varying])::text[])))
);

CREATE SEQUENCE public.maintenance_maintenance_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.maintenance_maintenance_id_seq OWNED BY public.maintenance.maintenance_id;

CREATE TABLE public.payments (
    payment_id integer NOT NULL,
    ride_id integer NOT NULL,
    amount numeric(10,2) NOT NULL,
    payment_method character varying(30),
    payment_status character varying(20) DEFAULT 'pending'::character varying,
    payment_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_amount_check CHECK ((amount >= (0)::numeric)),
    CONSTRAINT payments_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['pending'::character varying, 'completed'::character varying, 'failed'::character varying, 'refunded'::character varying])::text[])))
);

CREATE SEQUENCE public.payments_payment_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.payments_payment_id_seq OWNED BY public.payments.payment_id;

CREATE TABLE public.rides (
    ride_id integer NOT NULL,
    user_id integer NOT NULL,
    scooter_id integer NOT NULL,
    start_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    end_time timestamp without time zone,
    distance_km numeric(10,2) DEFAULT 0,
    total_cost numeric(10,2) DEFAULT 0,
    status character varying(20) DEFAULT 'active'::character varying,
    CONSTRAINT rides_status_check CHECK (((status)::text = ANY ((ARRAY['active'::character varying, 'completed'::character varying, 'cancelled'::character varying])::text[])))
);

CREATE SEQUENCE public.rides_ride_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.rides_ride_id_seq OWNED BY public.rides.ride_id;

CREATE TABLE public.scooters (
    scooter_id integer NOT NULL,
    qr_code character varying(100) NOT NULL,
    battery_level integer NOT NULL,
    latitude double precision,
    longitude double precision,
    status character varying(20) DEFAULT 'available'::character varying,
    CONSTRAINT scooters_battery_level_check CHECK (((battery_level >= 0) AND (battery_level <= 100))),
    CONSTRAINT scooters_status_check CHECK (((status)::text = ANY ((ARRAY['available'::character varying, 'in_use'::character varying, 'maintenance'::character varying])::text[])))
);

CREATE SEQUENCE public.scooters_scooter_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.scooters_scooter_id_seq OWNED BY public.scooters.scooter_id;

CREATE TABLE public.users (
    user_id integer NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(150) NOT NULL,
    password_hash text NOT NULL,
    role character varying(20) DEFAULT 'user'::character varying
);

CREATE SEQUENCE public.users_user_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;

ALTER TABLE ONLY public.maintenance ALTER COLUMN maintenance_id SET DEFAULT nextval('public.maintenance_maintenance_id_seq'::regclass);

ALTER TABLE ONLY public.payments ALTER COLUMN payment_id SET DEFAULT nextval('public.payments_payment_id_seq'::regclass);

ALTER TABLE ONLY public.rides ALTER COLUMN ride_id SET DEFAULT nextval('public.rides_ride_id_seq'::regclass);

ALTER TABLE ONLY public.scooters ALTER COLUMN scooter_id SET DEFAULT nextval('public.scooters_scooter_id_seq'::regclass);

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);

ALTER TABLE ONLY public.maintenance
    ADD CONSTRAINT maintenance_pkey PRIMARY KEY (maintenance_id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_ride_id_key UNIQUE (ride_id);

ALTER TABLE ONLY public.rides
    ADD CONSTRAINT rides_pkey PRIMARY KEY (ride_id);

ALTER TABLE ONLY public.scooters
    ADD CONSTRAINT scooters_pkey PRIMARY KEY (scooter_id);

ALTER TABLE ONLY public.scooters
    ADD CONSTRAINT scooters_qr_code_key UNIQUE (qr_code);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY public.maintenance
    ADD CONSTRAINT maintenance_scooter_id_fkey FOREIGN KEY (scooter_id) REFERENCES public.scooters(scooter_id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_ride_id_fkey FOREIGN KEY (ride_id) REFERENCES public.rides(ride_id);

ALTER TABLE ONLY public.rides
    ADD CONSTRAINT rides_scooter_id_fkey FOREIGN KEY (scooter_id) REFERENCES public.scooters(scooter_id);

ALTER TABLE ONLY public.rides
    ADD CONSTRAINT rides_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);

-- BACKEND SUPPORT TABLES AND INDEXES
CREATE TABLE IF NOT EXISTS auth_tokens (
  token_hash CHAR(64) PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS auth_tokens_user_id_idx ON auth_tokens(user_id);
CREATE TABLE IF NOT EXISTS scooter_telemetry (
  telemetry_id BIGSERIAL PRIMARY KEY,
  scooter_id INTEGER NOT NULL REFERENCES scooters(scooter_id),
  battery_level INTEGER NOT NULL CHECK (battery_level BETWEEN 0 AND 100),
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  speed_kmh DOUBLE PRECISION,
  device_timestamp BIGINT,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS scooter_telemetry_scooter_time_idx ON scooter_telemetry(scooter_id,received_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS one_active_ride_per_scooter ON rides(scooter_id) WHERE status='active';
CREATE UNIQUE INDEX IF NOT EXISTS one_active_ride_per_user ON rides(user_id) WHERE status='active';

COMMIT;
