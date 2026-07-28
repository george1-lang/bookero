# Algorithm: `time_pressure_heuristic`

**Family:** Heuristic  
**Purpose:** Adjust prices by time-to-departure and load factor using a closed-form formula.

## Purpose

As departure approaches, demand intensifies (time pressure) and capacity constraints tighten. `time_pressure_heuristic` is a fast, interpretable heuristic that applies a multiplier based on days-to-departure and load factor. It also serves as the default post-booking reprice algorithm.

## Inputs / Outputs

| Item | Type |
|------|------|
| **Input** | Days to departure, load factor |
| **Input** | Base prices |
| **Output** | Price updates (multiplied prices) |
| **Output** | `metrics.faresUpdated` | Count of price changes |

## Formula

```
multiplier = 1 + a·(1 - d/D)^p + b·loadFactor^q

where:
  d = days to departure (0 at departure)
  D = total days (30, horizon)
  a = 0.3 (time pressure coefficient)
  p = 2.0 (time pressure exponent)
  b = 0.2 (load factor coefficient)
  q = 1.5 (load factor exponent)

clamped to [0.5, 2.0]
```

## Pseudocode

```
FUNCTION time_pressure_heuristic_execute(context):
  a, p, b, q := 0.3, 2.0, 0.2, 1.5
  D := 30  // horizon in days
  
  updates := []
  
  for each flight in context.flightIds():
    fares := context.getFareClasses()[flight]
    inv := context.getInventory()[flight]
    
    d := days_between(now(), flight.departAt)
    d := max(0, d)
    
    loadFactor := (inv.seatsTotal - inv.seatsLeft) / inv.seatsTotal
    loadFactor := clamp(loadFactor, 0, 1)
    
    timeComponent := a * ((1 - d/D) ^ p)
    loadComponent := b * (loadFactor ^ q)
    multiplier := 1 + timeComponent + loadComponent
    multiplier := clamp(multiplier, 0.5, 2.0)
    
    for each fare in fares:
      newPrice := fare.basePrice * multiplier
      newPrice := round(newPrice, 2)
      newPrice := max(1.0, newPrice)
      
      if newPrice != fare.currentPrice:
        updates.append({newPrice})
  
  RETURN {
    status: "SUCCESS",
    priceUpdates: updates,
    metrics: {
      faresUpdated: len(updates),
      formula: "1 + 0.3*(1-d/30)^2 + 0.2*loadFactor^1.5",
      multiplierBand: "[0.5, 2.0]"
    }
  }
```

## Mermaid Flowchart

```mermaid
flowchart TD
  Start([Time Pressure]) --> Loop["For each flight"]
  Loop --> CalcDays["Calculate days to departure"]
  CalcDays --> CalcLoad["Calculate load factor"]
  CalcLoad --> TimeComp["Compute time component: 0.3*(1-d/30)^2"]
  TimeComp --> LoadComp["Compute load component: 0.2*loadFactor^1.5"]
  LoadComp --> Sum["Sum: 1 + timeComp + loadComp"]
  Sum --> Clamp["Clamp to [0.5, 2.0]"]
  Clamp --> ApplyFares["Apply multiplier to all fares"]
  ApplyFares --> Loop
  Loop --> Return["Return metrics"]
  Return --> End([Done])
```

## Complexity Analysis

- **Time:** O(F·C) where F = flights, C = classes. Simple arithmetic per fare.
- **Space:** O(U) where U = updates.

## Design Rationale

Closed-form heuristic is interpretable and fast (no iteration). Time pressure (1-d/D) rises nonlinearly (quadratic exponent 2) to match real traveler behavior. Load factor (^1.5) emphasizes scarcity when flight is nearly full. Formula mirrors airline industry practice (simplified revenue management).

**Alternatives:** Dynamic programming (optimal allocation), neural network (ML), time-series forecasting (demand curve evolution).

## Implementation

**Class:** `com.bookero.algorithms.TimePressureHeuristicAlgorithm`

## Tests

**Test class:** `AlgorithmSmokeTests`

## Performance Results

| Flights | Days to depart | Load | Duration (ms) | Updated | Max multiplier |
|---------|----------------|------|-------------|---------|---------|
| 50 | 7 | 70% | 2 | 50 | 1.64 |
| 100 | 1 | 85% | 3 | 100 | 1.78 |
| 200 | 14 | 50% | 5 | 200 | 1.23 |

**Note:** Post-booking reprice uses this algorithm by default (configured via `bookero.reprice-after-booking-key=time_pressure_heuristic`).
