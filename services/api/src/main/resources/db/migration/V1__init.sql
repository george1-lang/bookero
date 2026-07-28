CREATE TABLE airport (
  code VARCHAR(8) PRIMARY KEY,
  name TEXT NOT NULL,
  city TEXT,
  country TEXT,
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION
);

CREATE TABLE route (
  id UUID PRIMARY KEY,
  origin_code VARCHAR(8) NOT NULL REFERENCES airport(code),
  dest_code VARCHAR(8) NOT NULL REFERENCES airport(code),
  distance_km INT,
  UNIQUE (origin_code, dest_code)
);
CREATE INDEX idx_route_origin ON route (origin_code);
CREATE INDEX idx_route_dest ON route (dest_code);

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role VARCHAR(16) NOT NULL CHECK (role IN ('TRAVELER', 'ANALYST'))
);

CREATE TABLE flight (
  id UUID PRIMARY KEY,
  route_id UUID NOT NULL REFERENCES route(id),
  flight_no VARCHAR(16) NOT NULL,
  depart_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_flight_depart ON flight (depart_at);
CREATE INDEX idx_flight_route ON flight (route_id);

CREATE TABLE fare_class (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id) ON DELETE CASCADE,
  code VARCHAR(8) NOT NULL,
  base_price NUMERIC(12,2) NOT NULL,
  current_price NUMERIC(12,2) NOT NULL,
  seats_allocated INT NOT NULL,
  UNIQUE (flight_id, code)
);
CREATE INDEX idx_fare_class_flight ON fare_class (flight_id);

CREATE TABLE inventory (
  flight_id UUID PRIMARY KEY REFERENCES flight(id) ON DELETE CASCADE,
  seats_total INT NOT NULL,
  seats_left INT NOT NULL CHECK (seats_left >= 0)
);

CREATE TABLE booking (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  flight_id UUID NOT NULL REFERENCES flight(id),
  fare_class_id UUID NOT NULL REFERENCES fare_class(id),
  paid_price NUMERIC(12,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_booking_user ON booking (user_id);
CREATE INDEX idx_booking_flight ON booking (flight_id);
CREATE INDEX idx_booking_created ON booking (created_at);

CREATE TABLE algorithm_run (
  id UUID PRIMARY KEY,
  algorithm_key VARCHAR(64) NOT NULL,
  params JSONB,
  status VARCHAR(16) NOT NULL,
  duration_ms BIGINT,
  revenue_delta NUMERIC(14,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_algorithm_run_key ON algorithm_run (algorithm_key, created_at DESC);

CREATE TABLE price_history (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id),
  algorithm_run_id UUID REFERENCES algorithm_run(id),
  fare_class_code VARCHAR(8) NOT NULL,
  price NUMERIC(12,2) NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_price_history_flight ON price_history (flight_id, at DESC);

CREATE TABLE demand_snapshot (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id),
  demand_score DOUBLE PRECISION NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_demand_flight ON demand_snapshot (flight_id, at DESC);
