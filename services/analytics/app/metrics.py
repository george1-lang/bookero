from datetime import datetime, timezone
from decimal import Decimal
from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional


def get_revenue_metrics(db: Session) -> dict:
    total_revenue_row = db.execute(
        text("SELECT COALESCE(SUM(paid_price), 0) FROM booking")
    ).scalar()
    total_revenue = float(total_revenue_row) if total_revenue_row else 0.0

    baseline_revenue_row = db.execute(
        text("""
        SELECT COALESCE(SUM(fc.base_price), 0)
        FROM booking b
        JOIN fare_class fc ON b.fare_class_id = fc.id
        """)
    ).scalar()
    baseline_revenue = float(baseline_revenue_row) if baseline_revenue_row else 0.0

    revenue_delta = total_revenue - baseline_revenue
    revenue_delta_pct = (
        (revenue_delta / baseline_revenue * 100) if baseline_revenue > 0 else 0.0
    )

    seat_data = db.execute(
        text("""
        SELECT
          SUM(i.seats_total) as seats_total,
          SUM(i.seats_left) as seats_left
        FROM inventory i
        """)
    ).fetchone()

    seats_total = seat_data[0] or 0
    seats_left = seat_data[1] or 0
    seats_sold = seats_total - seats_left

    load_factor = (
        (seats_sold / seats_total) if seats_total > 0 else 0.0
    )

    avg_fare = (
        (total_revenue / seats_sold) if seats_sold > 0 else 0.0
    )

    booking_count = db.execute(
        text("SELECT COUNT(*) FROM booking")
    ).scalar() or 0

    revenue_by_day = db.execute(
        text("""
        SELECT
          DATE(b.created_at) as date,
          SUM(b.paid_price) as revenue,
          COUNT(*) as bookings
        FROM booking b
        GROUP BY DATE(b.created_at)
        ORDER BY date
        """)
    ).fetchall()

    revenue_by_day_list = [
        {
            "date": str(row[0]) if row[0] else None,
            "revenue": float(row[1]) if row[1] else 0.0,
            "bookings": row[2] or 0,
        }
        for row in revenue_by_day
    ]

    by_route = db.execute(
        text("""
        SELECT
          CONCAT(r.origin_code, '-', r.dest_code) as route,
          COUNT(b.id) as bookings,
          SUM(b.paid_price) as revenue
        FROM booking b
        JOIN flight f ON b.flight_id = f.id
        JOIN route r ON f.route_id = r.id
        GROUP BY r.id, r.origin_code, r.dest_code
        ORDER BY revenue DESC
        """)
    ).fetchall()

    by_route_list = [
        {
            "route": row[0],
            "bookings": row[1] or 0,
            "revenue": float(row[2]) if row[2] else 0.0,
        }
        for row in by_route
    ]

    return {
        "totalRevenue": total_revenue,
        "baselineRevenue": baseline_revenue,
        "revenueDelta": revenue_delta,
        "revenueDeltaPct": revenue_delta_pct,
        "loadFactor": load_factor,
        "avgFare": avg_fare,
        "seatsSold": seats_sold,
        "seatsTotal": seats_total,
        "bookingCount": booking_count,
        "revenueByDay": revenue_by_day_list,
        "byRoute": by_route_list,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
