import pytest
from decimal import Decimal


def test_metrics_empty_database():
    from app.metrics import get_revenue_metrics
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
        CREATE TABLE IF NOT EXISTS booking (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            flight_id TEXT,
            fare_class_id TEXT,
            paid_price REAL,
            created_at TIMESTAMP
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
        CREATE TABLE IF NOT EXISTS fare_class (
            id TEXT PRIMARY KEY,
            flight_id TEXT,
            code TEXT,
            base_price REAL,
            current_price REAL,
            seats_allocated INTEGER
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

        conn.commit()

    session = Session(engine)
    metrics = get_revenue_metrics(session)

    assert metrics["totalRevenue"] == 0.0
    assert metrics["baselineRevenue"] == 0.0
    assert metrics["revenueDelta"] == 0.0
    assert metrics["loadFactor"] == 0.0
    assert metrics["avgFare"] == 0.0
    assert metrics["seatsSold"] == 0
    assert metrics["seatsTotal"] == 0
    assert metrics["bookingCount"] == 0
    assert isinstance(metrics["revenueByDay"], list)
    assert isinstance(metrics["byRoute"], list)

    session.close()


def test_metrics_structure():
    from app.metrics import get_revenue_metrics
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
        CREATE TABLE IF NOT EXISTS booking (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            flight_id TEXT,
            fare_class_id TEXT,
            paid_price REAL,
            created_at TIMESTAMP
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
        CREATE TABLE IF NOT EXISTS fare_class (
            id TEXT PRIMARY KEY,
            flight_id TEXT,
            code TEXT,
            base_price REAL,
            current_price REAL,
            seats_allocated INTEGER
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

        conn.commit()

    session = Session(engine)
    metrics = get_revenue_metrics(session)

    required_keys = [
        "totalRevenue",
        "baselineRevenue",
        "revenueDelta",
        "revenueDeltaPct",
        "loadFactor",
        "avgFare",
        "seatsSold",
        "seatsTotal",
        "bookingCount",
        "revenueByDay",
        "byRoute",
        "generatedAt",
    ]

    for key in required_keys:
        assert key in metrics, f"Missing key: {key}"

    session.close()
