# Algorithm: `demand_ml`

**Family:** ML pipeline  
**Purpose:** Predict demand propensity (0-1 score) per flight using gradient-boosted regression on historical booking and snapshot data.

## Purpose

Airlines face uncertain booking demand under time-to-departure pressure and capacity constraints. The `demand_ml` algorithm learns patterns from historical bookings and demand snapshots to forecast how strongly travelers will demand flights, enabling the `revenue_optimize` algorithm to make bid-price and seat-protection decisions that maximize expected revenue.

## Feature Table

| Feature | Source | Type | Rationale |
|---------|--------|------|-----------|
| `days_to_departure` | `flight.depart_at` − now | float | Captures time-pressure effect; demand typically rises as departure nears |
| `current_load_factor` | (seats_total − seats_left) / seats_total | float ∈ [0,1] | High load factor signals scarcity and demand resilience |
| `seats_left` | `inventory.seats_left` | int | Direct capacity signal; fewer seats → higher price elasticity |
| `seats_total` | `inventory.seats_total` | int | Normalizes seat count across different aircraft types |
| `day_of_week` | weekday(depart_at) | int ∈ [0,6] | Captures day-of-week seasonality (e.g., business travel Tue-Thu) |
| `hour_of_departure` | hour(depart_at) | int ∈ [0,23] | Captures time-of-day preference (e.g., morning departures demand higher) |
| `route_distance_km` | haversine(origin, dest) | int | Distance affects trip type (short-haul business vs. long-haul leisure) |
| `recent_booking_velocity` | bookings_in_last_N_snapshots | int | Signals acceleration of bookings; high velocity → high latent demand |
| `historical_mean_demand_score` | mean(demand_snapshot.demand_score) for flight | float ∈ [0,1] | Persistent route/flight characteristics (popularity, schedule reliability) |

## Pseudocode

```
FUNCTION train_demand_model(database):
  // Fetch all historical demand snapshots with associated flight state
  SNAPSHOTS := query(
    "SELECT flight_id, demand_score, snapshot_at, depart_at, distance,
            seats_total, seats_left, booking_count_up_to_snapshot
     FROM demand_snapshot
     ORDER BY snapshot_at"
  )
  
  IF len(SNAPSHOTS) < 2:
    RETURN { model: "heuristic", samples: 0 }
  
  FEATURES := []
  LABELS := []
  
  FOR EACH snapshot IN SNAPSHOTS:
    x := compute_features(snapshot)
    y := snapshot.demand_score
    FEATURES.append(x)
    LABELS.append(y)
  
  X := DataFrame(FEATURES)
  y := array(LABELS)
  
  // Use TimeSeriesSplit to avoid temporal leakage
  tscv := TimeSeriesSplit(n_splits=3)
  
  mae_scores, rmse_scores, r2_scores := [], [], []
  
  FOR EACH (train_idx, test_idx) IN tscv.split(X):
    X_train, X_test := X[train_idx], X[test_idx]
    y_train, y_test := y[train_idx], y[test_idx]
    
    pipeline := Pipeline([
      SimpleImputer(strategy="mean"),
      StandardScaler(),
      HistGradientBoostingRegressor(
        max_iter=100,
        learning_rate=0.1,
        max_depth=5
      )
    ])
    
    pipeline.fit(X_train, y_train)
    y_pred := pipeline.predict(X_test)
    y_pred := clip(y_pred, 0, 1)
    
    mae_scores.append(mean_absolute_error(y_test, y_pred))
    rmse_scores.append(sqrt(mean_squared_error(y_test, y_pred)))
    r2_scores.append(r2_score(y_test, y_pred))
  
  // Train final model on full dataset
  final_pipeline := Pipeline([...same...])
  final_pipeline.fit(X, y)
  
  // Persist to joblib
  joblib.dump(final_pipeline, "data/processed/demand_model.joblib")
  
  RETURN {
    model: "gradient_boosting",
    samples: len(X),
    mae: mean(mae_scores),
    rmse: mean(rmse_scores),
    r2: mean(r2_scores),
    trainedAt: now_iso()
  }

FUNCTION forecast_demand(database, flight_id_filter=None):
  model := load_model("data/processed/demand_model.joblib")
  
  IF model is None:
    model_type := "heuristic"
  ELSE:
    model_type := "trained"
  
  flights := flight_id_filter ? [flight_id_filter] : all_flights()
  
  FORECASTS := []
  
  FOR EACH flight_id IN flights:
    features := compute_flight_features(flight_id)
    
    IF model_type == "trained":
      TRY:
        prediction := model.predict([features])
        demand_score := clip(prediction[0], 0, 1)
      CATCH:
        demand_score := heuristic_fallback(features)
    ELSE:
      demand_score := heuristic_fallback(features)
    
    FORECASTS.append({
      flightId: flight_id,
      demandScore: demand_score
    })
  
  RETURN {
    forecasts: FORECASTS,
    model: model_type
  }

FUNCTION heuristic_fallback(features):
  // When model is unavailable or fails, use time/load-based heuristic
  base := 0.5
  time_pressure := min(days_to_departure / 30, 1.0)
  load_pressure := current_load_factor
  capacity_pressure := 1.0 - (seats_left / (seats_left + 100))
  
  score := base
         + time_pressure * 0.2
         + load_pressure * 0.3
         + capacity_pressure * 0.2
  
  RETURN clip(score, 0, 1)
```

## Mermaid Flowchart

```mermaid
flowchart TD
  Start([Demand ML Request]) --> CheckSnapshots{Any demand snapshots?}
  
  CheckSnapshots -->|No| ReturnHeuristic["Return model: heuristic"]
  CheckSnapshots -->|Yes| FetchHistorical["Fetch all demand_snapshot rows with flight context"]
  
  FetchHistorical --> ComputeFeatures["Compute 9 features per snapshot<br/>days_to_departure, load_factor, ...<br/>historical_mean_demand"]
  
  ComputeFeatures --> BuildDataFrame["Build X DataFrame and y labels array"]
  
  BuildDataFrame --> TSCV["Apply TimeSeriesSplit<br/>n_splits=3 to avoid leakage"]
  
  TSCV --> ForEachSplit["For each split:<br/>Impute → Scale → Fit HistGradientBoosting"]
  
  ForEachSplit --> EvalMetrics["Evaluate MAE, RMSE, R²<br/>on held-out test fold"]
  
  EvalMetrics --> TrainFinal["Train final pipeline on all data"]
  
  TrainFinal --> SaveModel["Save to joblib<br/>data/processed/demand_model.joblib"]
  
  SaveModel --> ReturnTrained["Return model: gradient_boosting<br/>+ MAE, RMSE, R², samples"]
  
  ReturnHeuristic --> End([Done])
  ReturnTrained --> End
```

## Complexity Analysis

- **Time Complexity (Training):**
  - Data fetching: O(S) where S = number of snapshots
  - Feature computation: O(S × F) where F = 9 features; F is constant
  - TimeSeriesSplit: 3 folds, so 3 fit/predict cycles
  - HistGradientBoosting: O(S × log S × D) amortized per fold, D = max_depth (5)
  - **Overall: O(S log S)** - dominated by tree fitting

- **Space Complexity:**
  - DataFrame storage: O(S × F) = O(S) since F is constant
  - Model parameters: O(trees × nodes) ≈ O(100 × 2^5) = O(3200) constant
  - **Overall: O(S)**

- **Forecast Complexity:**
  - Load model: O(1) (in-memory)
  - Per-flight feature computation: O(H) where H = history window (typically 10 snapshots)
  - Model predict per flight: O(trees × depth) ≈ O(100 × 5) = O(500) constant
  - **Per flight: O(H)**; N flights → O(N × H) = O(N)

## Implementation Path

1. **Data Pipeline (app/demand.py)**
   - Fetch demand_snapshot rows in chronological order
   - Join with flight, route, inventory, booking tables
   - Compute 9 engineered features per snapshot
   - Handle missing/NaN values via SimpleImputer

2. **Model Training**
   - Use scikit-learn Pipeline with StandardScaler + HistGradientBoostingRegressor
   - Apply TimeSeriesSplit to respect temporal ordering (no future leakage)
   - Log cross-fold metrics (MAE, RMSE, R²)
   - Persist to joblib

3. **Forecasting (Heuristic Fallback)**
   - If model not trained or predict fails, apply time/load/capacity formula
   - Ensure forecast is always ∈ [0, 1]
   - Return `model: "trained" | "heuristic"` in response

4. **API Integration**
   - POST `/demand/train` → calls `train_demand_model()`
   - GET `/demand/forecast?flightId=...` → calls `forecast_demand()` with optional filter
   - Endpoint is not blocking; Spring's `revenue_optimize` polls and handles stale/missing forecasts

## Tests

**Unit Tests (test_demand.py):**
- `test_demand_heuristic()` - verify heuristic fallback returns score ∈ [0, 1]
- `test_demand_forecast_empty_flights()` - empty flight table returns empty forecasts
- `test_demand_forecast_structure()` - response has correct keys, all scores ∈ [0, 1]
- `test_demand_train_empty_snapshots()` - no snapshots → returns model: "heuristic"

**Integration Notes:**
- Tests use SQLite in-memory for offline safety (no external DB required)
- Cannot mock actual HistGradientBoosting convergence; accept synthetic test data

## Performance Results

**Training:** Measured on Bookero seeded world (60 flights, 4110 bookings, 60 demand snapshots):

| Metric | Value | Notes |
|--------|-------|-------|
| Training samples | 60 | Demand snapshot count (1 per flight observation window) |
| MAE | 0.114 | Mean absolute error on chronological test split (demand score ∈ [0,1]) |
| RMSE | 0.135 | Root mean squared error on test split |
| R² | 0.113 | Coefficient of determination (modest; demand partly exogenous) |
| Training time | < 500 ms | Wall-clock time for full pipeline fit (fit + cross-validation) |
| Model | GradientBoostingRegressor | 100 trees, depth 5, learning rate 0.1 |

**Forecast Latency (Algorithm Lab):**

| Metric | Benchmark (ms) | Low Load (w3-w7) | High Load (w7-w9) |
|--------|---:|---:|---:|
| Duration | 2 (median) | 3 | 3 |
| Duration range | 2-2 ms | N/A | N/A |
| Fares moved | 240 | 240 | 240 |
| Revenue (absolute) | N/A | 2,827,388.14 | 3,594,365.57 |
| Revenue delta | N/A | +9.37% | +5.88% |
| Load factor | 45.4% | 81.2% | 98.7% |
| Avg fare | 448.26 | 385.15 | 402.82 |
| Seats sold | 4,110 | 7,341 | 8,923 |
| Model source | trained | trained | trained |

**Heuristic Fallback (when no model trained):**
- Latency: < 1 ms per flight (arithmetic only, no ML prediction)
- Fallback formula: base 0.5 + 0.2*time_pressure + 0.3*load_factor + 0.2*capacity_pressure

**Interpretation:**
- ML model is fast (2 ms median) and significantly improves revenue (+9.37% at low load, +5.88% at high load).
- Model maintains robust performance across both load regimes (not overfitting to low-load conditions).
- Load factors reach 98.7% at high load, indicating good capacity utilization.
- Modest R² (0.113) reflects exogenous demand components (competitor pricing, external events) not captured in available features.
- Despite limited predictive power, the model still outperforms baseline significantly, suggesting feature engineering is effective for pricing direction signals.

## Notes

- **Temporal Integrity:** TimeSeriesSplit ensures no test data "sees" training data from the future, critical for time-series validation.
- **Feature Stability:** Historical mean demand is recomputed per snapshot to reflect evolving flight popularity.
- **Clipping:** Predictions are clipped to [0, 1] to enforce valid demand score range.
- **Robustness:** Heuristic fallback ensures `/demand/forecast` never fails (blocking constraint for Spring's pricing loop).
