import pickle
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, Dict, Any

import numpy as np
import pandas as pd
from sqlalchemy import text
from sqlalchemy.orm import Session
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.model_selection import TimeSeriesSplit
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
import joblib

from app.config import settings


MODEL_PATH = settings.data_processed_dir / "demand_model.joblib"


def _heuristic_demand_score(
    days_to_departure: float,
    current_load_factor: float,
    seats_left: int,
) -> float:
    base = 0.5
    time_pressure = min(days_to_departure / 30, 1.0)
    load_pressure = current_load_factor
    capacity_pressure = 1.0 - (seats_left / max(seats_left + 100, 1))

    score = base + (time_pressure * 0.2) + (load_pressure * 0.3) + (capacity_pressure * 0.2)
    return np.clip(score, 0.0, 1.0)


def get_flight_features(
    db: Session,
    flight_id: str,
) -> Optional[Dict[str, Any]]:
    flight_row = db.execute(
        text("""
        SELECT
          f.id, f.depart_at, r.distance_km,
          i.seats_total, i.seats_left,
          COUNT(b.id) as bookings_count
        FROM flight f
        LEFT JOIN route r ON f.route_id = r.id
        LEFT JOIN inventory i ON f.id = i.flight_id
        LEFT JOIN booking b ON f.id = b.flight_id
        WHERE f.id = :fid
        GROUP BY f.id, f.depart_at, r.distance_km, i.seats_total, i.seats_left
        """),
        {"fid": flight_id}
    ).fetchone()

    if not flight_row:
        return None

    fid, depart_at, distance_km, seats_total, seats_left, bookings_count = flight_row

    seats_total = seats_total or 150
    seats_left = seats_left or seats_total
    bookings_count = bookings_count or 0

    current_load_factor = (seats_total - seats_left) / seats_total if seats_total > 0 else 0.0

    now = datetime.now(timezone.utc).replace(tzinfo=None) if datetime.now(timezone.utc).tzinfo else datetime.utcnow()
    depart_dt = depart_at.replace(tzinfo=None) if depart_at.tzinfo else depart_at

    days_to_departure = max((depart_dt - now).total_seconds() / 86400, 0)
    day_of_week = depart_dt.weekday()
    hour_of_departure = depart_dt.hour

    distance_km = distance_km or 1000

    recent_booking_velocity = bookings_count

    demand_snapshots = db.execute(
        text("""
        SELECT demand_score FROM demand_snapshot
        WHERE flight_id = :fid
        ORDER BY at DESC
        LIMIT 10
        """),
        {"fid": flight_id}
    ).fetchall()

    historical_mean_demand = (
        float(np.mean([row[0] for row in demand_snapshots]))
        if demand_snapshots
        else 0.5
    )

    return {
        "flight_id": flight_id,
        "days_to_departure": days_to_departure,
        "current_load_factor": current_load_factor,
        "seats_left": seats_left,
        "seats_total": seats_total,
        "day_of_week": day_of_week,
        "hour_of_departure": hour_of_departure,
        "route_distance_km": distance_km,
        "recent_booking_velocity": recent_booking_velocity,
        "historical_mean_demand_score": historical_mean_demand,
    }


def build_training_dataset(db: Session) -> Optional[tuple]:
    demand_snapshots = db.execute(
        text("""
        SELECT
          ds.flight_id,
          ds.demand_score,
          ds.at,
          f.depart_at,
          r.distance_km,
          i.seats_total,
          i.seats_left,
          COUNT(b.id) as bookings_by_snapshot
        FROM demand_snapshot ds
        JOIN flight f ON ds.flight_id = f.id
        JOIN route r ON f.route_id = r.id
        LEFT JOIN inventory i ON f.id = i.flight_id
        LEFT JOIN booking b ON f.id = b.flight_id AND b.created_at < ds.at
        GROUP BY ds.id, ds.flight_id, ds.demand_score, ds.at, f.depart_at,
                 r.distance_km, i.seats_total, i.seats_left
        ORDER BY ds.at
        """)
    ).fetchall()

    if not demand_snapshots:
        return None

    features_list = []
    labels = []

    for row in demand_snapshots:
        flight_id, demand_score, snapshot_at, depart_at, distance_km, seats_total, seats_left, bookings_by_snapshot = row

        seats_total = seats_total or 150
        seats_left = seats_left or seats_total
        bookings_by_snapshot = bookings_by_snapshot or 0

        current_load_factor = (seats_total - seats_left) / seats_total if seats_total > 0 else 0.0

        now = snapshot_at.replace(tzinfo=None) if snapshot_at.tzinfo else snapshot_at
        depart_dt = depart_at.replace(tzinfo=None) if depart_at.tzinfo else depart_at

        days_to_departure = max((depart_dt - now).total_seconds() / 86400, 0)
        day_of_week = depart_dt.weekday()
        hour_of_departure = depart_dt.hour
        distance_km = distance_km or 1000

        recent_velocity = bookings_by_snapshot

        historical_snapshots = db.execute(
            text("""
            SELECT demand_score FROM demand_snapshot
            WHERE flight_id = :fid AND at < :snap_at
            ORDER BY at DESC
            LIMIT 10
            """),
            {"fid": flight_id, "snap_at": snapshot_at}
        ).fetchall()

        historical_mean = (
            float(np.mean([row[0] for row in historical_snapshots]))
            if historical_snapshots
            else 0.5
        )

        features_list.append({
            "days_to_departure": days_to_departure,
            "current_load_factor": current_load_factor,
            "seats_left": seats_left,
            "seats_total": seats_total,
            "day_of_week": day_of_week,
            "hour_of_departure": hour_of_departure,
            "route_distance_km": distance_km,
            "recent_booking_velocity": recent_velocity,
            "historical_mean_demand_score": historical_mean,
        })
        labels.append(demand_score)

    if len(features_list) < 2:
        return None

    df = pd.DataFrame(features_list)
    y = np.array(labels)

    return df, y


def train_demand_model(db: Session) -> Dict[str, Any]:
    dataset = build_training_dataset(db)

    if dataset is None:
        return {
            "model": "heuristic",
            "samples": 0,
            "features": [],
            "mae": None,
            "rmse": None,
            "r2": None,
            "trainedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }

    X, y = dataset

    feature_columns = [
        "days_to_departure",
        "current_load_factor",
        "seats_left",
        "seats_total",
        "day_of_week",
        "hour_of_departure",
        "route_distance_km",
        "recent_booking_velocity",
        "historical_mean_demand_score",
    ]

    tscv = TimeSeriesSplit(n_splits=3)

    mae_scores = []
    rmse_scores = []
    r2_scores = []

    for train_idx, test_idx in tscv.split(X):
        X_train, X_test = X.iloc[train_idx], X.iloc[test_idx]
        y_train, y_test = y[train_idx], y[test_idx]

        pipeline = Pipeline([
            ("imputer", SimpleImputer(strategy="mean")),
            ("scaler", StandardScaler()),
            ("model", HistGradientBoostingRegressor(
                max_iter=100,
                learning_rate=0.1,
                max_depth=5,
                random_state=42,
            ))
        ])

        pipeline.fit(X_train, y_train)
        y_pred = pipeline.predict(X_test)
        y_pred = np.clip(y_pred, 0.0, 1.0)

        mae_scores.append(mean_absolute_error(y_test, y_pred))
        rmse_scores.append(np.sqrt(mean_squared_error(y_test, y_pred)))
        r2_scores.append(r2_score(y_test, y_pred))

    final_pipeline = Pipeline([
        ("imputer", SimpleImputer(strategy="mean")),
        ("scaler", StandardScaler()),
        ("model", HistGradientBoostingRegressor(
            max_iter=100,
            learning_rate=0.1,
            max_depth=5,
            random_state=42,
        ))
    ])
    final_pipeline.fit(X, y)

    joblib.dump(final_pipeline, str(MODEL_PATH))

    return {
        "model": "gradient_boosting",
        "samples": len(X),
        "features": feature_columns,
        "mae": float(np.mean(mae_scores)),
        "rmse": float(np.mean(rmse_scores)),
        "r2": float(np.mean(r2_scores)),
        "trainedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }


def load_model() -> Optional[Any]:
    if MODEL_PATH.exists():
        return joblib.load(str(MODEL_PATH))
    return None


def forecast_demand(db: Session, flight_id: Optional[str] = None) -> Dict[str, Any]:
    model = load_model()

    flights_to_forecast = []

    if flight_id:
        flight_row = db.execute(
            text("SELECT id FROM flight WHERE id = :fid"),
            {"fid": flight_id}
        ).fetchone()
        if flight_row:
            flights_to_forecast.append(flight_row[0])
    else:
        flight_rows = db.execute(
            text("SELECT id FROM flight ORDER BY depart_at")
        ).fetchall()
        flights_to_forecast = [row[0] for row in flight_rows]

    forecasts = []

    for fid in flights_to_forecast:
        features_dict = get_flight_features(db, fid)

        if features_dict is None:
            continue

        if model is not None:
            X_dict = {
                "days_to_departure": features_dict["days_to_departure"],
                "current_load_factor": features_dict["current_load_factor"],
                "seats_left": features_dict["seats_left"],
                "seats_total": features_dict["seats_total"],
                "day_of_week": features_dict["day_of_week"],
                "hour_of_departure": features_dict["hour_of_departure"],
                "route_distance_km": features_dict["route_distance_km"],
                "recent_booking_velocity": features_dict["recent_booking_velocity"],
                "historical_mean_demand_score": features_dict["historical_mean_demand_score"],
            }

            X_df = pd.DataFrame([X_dict])

            try:
                pred = model.predict(X_df)[0]
                demand_score = float(np.clip(pred, 0.0, 1.0))
                model_used = "trained"
            except Exception:
                demand_score = _heuristic_demand_score(
                    features_dict["days_to_departure"],
                    features_dict["current_load_factor"],
                    features_dict["seats_left"],
                )
                model_used = "heuristic"
        else:
            demand_score = _heuristic_demand_score(
                features_dict["days_to_departure"],
                features_dict["current_load_factor"],
                features_dict["seats_left"],
            )
            model_used = "heuristic"

        forecasts.append({
            "flightId": fid,
            "demandScore": demand_score,
        })

    model_type = "trained" if model is not None else "heuristic"

    return {
        "forecasts": forecasts,
        "model": model_type,
    }
