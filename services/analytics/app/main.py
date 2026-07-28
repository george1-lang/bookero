import time
from typing import Optional

from fastapi import FastAPI, Depends, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from app.db import get_db, healthcheck
from app.etl import run_etl
from app.eda import get_eda_summary
from app.metrics import get_revenue_metrics
from app.demand import train_demand_model, forecast_demand

app = FastAPI(title="Bookero Analytics", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:3100"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    db_ok = healthcheck()
    return {
        "status": "ok",
        "database": "up" if db_ok else "down"
    }


@app.post("/etl/run")
async def etl_run(
    refresh: bool = Query(False),
    db: Session = Depends(get_db)
):
    start_time = time.time()
    airports_upserted, routes_upserted = await run_etl(db, refresh=refresh)
    duration_ms = int((time.time() - start_time) * 1000)

    return {
        "airportsUpserted": airports_upserted,
        "routesUpserted": routes_upserted,
        "durationMs": duration_ms,
    }


@app.get("/eda/summary")
async def eda_summary(db: Session = Depends(get_db)):
    summary = get_eda_summary(db)
    return summary


@app.post("/demand/train")
async def demand_train(db: Session = Depends(get_db)):
    result = train_demand_model(db)
    return result


@app.get("/demand/forecast")
async def demand_forecast(
    flight_id: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    result = forecast_demand(db, flight_id=flight_id)
    return result


@app.get("/metrics/revenue")
async def metrics_revenue(db: Session = Depends(get_db)):
    metrics = get_revenue_metrics(db)
    return metrics
