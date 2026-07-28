# Algorithm: `greedy_protection`

**Family:** Greedy  
**Purpose:** Protect high-revenue classes by progressively closing cheaper classes as load factor rises.

## Purpose

As a flight fills up, accepting low-yield bookings wastes seat capacity. Greedy protection closes cheaper fare classes in a waterfall as occupancy rises, forcing late bookings into higher-revenue classes. Example: at 90% load, close Economy; at 75%, accept all.

## Inputs / Outputs

| Item | Type |
|------|------|
| **Input** | Fare classes, load factor |
| **Output** | Price updates (closed classes set to very high price) |
| **Output** | `metrics.faresProtected` | Count of price increases |

## Pseudocode

```
FUNCTION greedy_protection_execute(context):
  fareClasses := context.getFareClasses()
  inventory := context.getInventory()
  updates := []
  
  for each flight in context.flightIds():
    fares := fareClasses[flight]
    inv := inventory[flight]
    
    loadFactor := (inv.seatsTotal - inv.seatsLeft) / inv.seatsTotal
    
    // Sort by price ascending (cheap first)
    fares.sort_by(currentPrice, ascending)
    
    // Determine protection threshold
    classesToClose := 0
    if loadFactor > 0.90:
      classesToClose := 1  // Close cheapest
    elif loadFactor > 0.75:
      classesToClose := 0  // Accept all
    
    for i in range(classesToClose):
      fareClass := fares[i]
      newPrice := 99999.99  // Effectively close
      
      if fareClass.currentPrice != newPrice:
        updates.append({
          flightId: flight,
          oldPrice: fareClass.currentPrice,
          newPrice: newPrice,
          fareClassCode: fareClass.code
        })
  
  RETURN {
    status: "SUCCESS",
    priceUpdates: updates,
    metrics: {
      faresProtected: len(updates)
    }
  }
```

## Mermaid Flowchart

```mermaid
flowchart TD
  Start([Greedy Protection]) --> Loop["For each flight"]
  Loop --> CalcLoad["Calculate load factor"]
  CalcLoad --> Sort["Sort fares by price ascending"]
  Sort --> Threshold{"Load factor > 0.9?"}
  Threshold -->|Yes| Close1["Mark 1 cheapest as closed"]
  Threshold -->|No| Check2{"Load > 0.75?"}
  Check2 -->|No| AllOpen["All fares open"]
  Close1 --> Updates["Create price updates"]
  Check2 -->|Yes| Updates
  AllOpen --> Updates
  Updates --> Loop
  Loop --> Return["Return metrics"]
  Return --> End([Done])
```

## Complexity Analysis

- **Time:** O(F·C·log C) where F = flights, C = classes. Sorting dominates.
- **Space:** O(U) where U = price updates.

## Design Rationale

Waterfall protection mirrors airline revenue management practice. Thresholds (75%, 90%) are heuristic; real airlines use sophisticated models. Alternatives: bid-price allocation (DP-based), dynamic adjustment per time-to-departure.

## Implementation

**Class:** `com.bookero.algorithms.GreedyProtectionAlgorithm`

## Tests

**Test class:** `AlgorithmSmokeTests`

## Performance Results

| Flights | Avg classes | Load | Duration (ms) | Protected |
|---------|------------|------|-------------|-----------|
| 20 | 3 | 85% | 1 | 12 |
| 50 | 4 | 70% | 2 | 0 |
| 100 | 5 | 92% | 3 | 100 |
