--


--
--


--
--


--
--

ALTER TABLE ONLY public.maintenance
    ADD CONSTRAINT maintenance_pkey PRIMARY KEY (maintenance_id);


--
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (payment_id);


--
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_ride_id_key UNIQUE (ride_id);


--
--

ALTER TABLE ONLY public.rides
    ADD CONSTRAINT rides_pkey PRIMARY KEY (ride_id);


--
--

ALTER TABLE ONLY public.scooters
    ADD CONSTRAINT scooters_pkey PRIMARY KEY (scooter_id);


--
--

ALTER TABLE ONLY public.scooters
    ADD CONSTRAINT scooters_qr_code_key UNIQUE (qr_code);


--
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
--

ALTER TABLE ONLY public.maintenance
    ADD CONSTRAINT maintenance_scooter_id_fkey FOREIGN KEY (scooter_id) REFERENCES public.scooters(scooter_id);


--
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_ride_id_fkey FOREIGN KEY (ride_id) REFERENCES public.rides(ride_id);


--
--

ALTER TABLE ONLY public.rides
    ADD CONSTRAINT rides_scooter_id_fkey FOREIGN KEY (scooter_id) REFERENCES public.scooters(scooter_id);


--
--

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

