-- Run AFTER importing the user's original scooter_rental PostgreSQL dump.
-- Keeps existing tables intact; only adds backend support tables and indexes.
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
