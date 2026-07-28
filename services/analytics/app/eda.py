import pandas as pd
from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Dict, Any


def get_eda_summary(db: Session) -> Dict[str, Any]:
    table_counts = {}
    for table in ["airport", "route", "flight", "fare_class", "inventory", "booking", "demand_snapshot"]:
        count = db.execute(text(f"SELECT COUNT(*) FROM {table}")).scalar() or 0
        table_counts[table] = count

    demand_scores = []
    rows = db.execute(
        text("SELECT demand_score FROM demand_snapshot ORDER BY demand_score")
    ).fetchall()

    if rows:
        demand_scores = [float(row[0]) for row in rows]

    demand_distribution = {}
    if demand_scores:
        df = pd.Series(demand_scores)
        demand_distribution = {
            "count": len(df),
            "mean": float(df.mean()),
            "std": float(df.std()),
            "min": float(df.min()),
            "p25": float(df.quantile(0.25)),
            "p50": float(df.quantile(0.50)),
            "p75": float(df.quantile(0.75)),
            "max": float(df.max()),
        }
    else:
        demand_distribution = {
            "count": 0,
            "mean": None,
            "std": None,
            "min": None,
            "p25": None,
            "p50": None,
            "p75": None,
            "max": None,
        }

    seats_data = db.execute(
        text("""
        SELECT SUM(seats_total), SUM(seats_left)
        FROM inventory
        """)
    ).fetchone()

    seats_total = seats_data[0] or 0
    seats_left = seats_data[1] or 0
    load_factor_avg = (
        ((seats_total - seats_left) / seats_total)
        if seats_total > 0
        else None
    )

    total_revenue = db.execute(
        text("SELECT COALESCE(SUM(paid_price), 0) FROM booking")
    ).scalar() or 0.0

    top_routes = db.execute(
        text("""
        SELECT
          CONCAT(r.origin_code, '-', r.dest_code) as route,
          COUNT(b.id) as bookings
        FROM booking b
        JOIN flight f ON b.flight_id = f.id
        JOIN route r ON f.route_id = r.id
        GROUP BY r.id, r.origin_code, r.dest_code
        ORDER BY bookings DESC
        LIMIT 10
        """)
    ).fetchall()

    top_routes_list = [
        {"route": row[0], "bookings": row[1]}
        for row in top_routes
    ]

    return {
        "tableCounts": table_counts,
        "demandDistribution": demand_distribution,
        "loadFactorAvg": load_factor_avg,
        "totalRevenue": float(total_revenue),
        "topRoutesByBookings": top_routes_list,
    }
