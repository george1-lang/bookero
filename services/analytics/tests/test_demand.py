import pytest
import numpy as np


def test_demand_heuristic():
    from app.demand import _heuristic_demand_score

    score = _heuristic_demand_score(
        days_to_departure=10,
        current_load_factor=0.5,
        seats_left=50
    )

    assert 0.0 <= score <= 1.0
    assert isinstance(score, float)


def test_demand_forecast_empty_flights():
    from app.demand import forecast_demand
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    with engine.connect() as conn:
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS flight (
            id TEXT PRIMARY KEY,
            route_id TEXT,
            flight_no TEXT,
            depart_at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS route (
            id TEXT PRIMARY KEY,
            origin_code TEXT,
            dest_code TEXT,
            distance_km INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS inventory (
            flight_id TEXT PRIMARY KEY,
            seats_total INTEGER,
            seats_left INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS demand_snapshot (
            id TEXT PRIMARY KEY,
            flight_id TEXT,
            demand_score REAL,
            at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS booking (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            flight_id TEXT,
            fare_class_id TEXT,
            paid_price REAL,
            created_at TIMESTAMP
        )
        """))

        conn.commit()

    session = Session(engine)
    result = forecast_demand(session)

    assert "forecasts" in result
    assert "model" in result
    assert isinstance(result["forecasts"], list)
    assert result["model"] in ["trained", "heuristic"]

    session.close()


def test_demand_forecast_structure():
    from app.demand import forecast_demand
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    with engine.connect() as conn:
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS flight (
            id TEXT PRIMARY KEY,
            route_id TEXT,
            flight_no TEXT,
            depart_at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS route (
            id TEXT PRIMARY KEY,
            origin_code TEXT,
            dest_code TEXT,
            distance_km INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS inventory (
            flight_id TEXT PRIMARY KEY,
            seats_total INTEGER,
            seats_left INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS demand_snapshot (
            id TEXT PRIMARY KEY,
            flight_id TEXT,
            demand_score REAL,
            at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS booking (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            flight_id TEXT,
            fare_class_id TEXT,
            paid_price REAL,
            created_at TIMESTAMP
        )
        """))

        conn.commit()

    session = Session(engine)
    result = forecast_demand(session)

    assert "forecasts" in result
    for forecast in result["forecasts"]:
        assert "flightId" in forecast
        assert "demandScore" in forecast
        assert 0.0 <= forecast["demandScore"] <= 1.0

    session.close()


def test_demand_train_empty_snapshots():
    from app.demand import train_demand_model
    from sqlalchemy import create_engine, text
    from sqlalchemy.orm import Session
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    with engine.connect() as conn:
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS demand_snapshot (
            id TEXT PRIMARY KEY,
            flight_id TEXT,
            demand_score REAL,
            at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS flight (
            id TEXT PRIMARY KEY,
            route_id TEXT,
            flight_no TEXT,
            depart_at TIMESTAMP
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS route (
            id TEXT PRIMARY KEY,
            origin_code TEXT,
            dest_code TEXT,
            distance_km INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS inventory (
            flight_id TEXT PRIMARY KEY,
            seats_total INTEGER,
            seats_left INTEGER
        )
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS booking (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            flight_id TEXT,
            fare_class_id TEXT,
            paid_price REAL,
            created_at TIMESTAMP
        )
        """))

        conn.commit()

    session = Session(engine)
    result = train_demand_model(session)

    assert "model" in result
    assert result["model"] == "heuristic"
    assert result["samples"] == 0
    assert isinstance(result["features"], list)

    session.close()
