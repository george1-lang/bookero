# Phase 1: Problem Framing — Bookero Dynamic Pricing System

**Course:** Computational Systems & Problem Solving (Topic #66: Dynamic Pricing Algorithm for Airlines)  
**Date:** 2026-07-28  
**Product:** Bookero — single-airline revenue-management system for dynamic pricing, booking, and revenue analytics

---

## 1. The Airline Revenue-Management Problem

Airlines operate under structural constraints that make pricing exceptionally difficult:

- **Fixed perishable capacity:** A 180-seat aircraft cannot be recalled or reproduced after departure. Each empty seat represents irretrievable lost revenue (spoilage).
- **Uncertain and time-varying demand:** Demand varies by season, day-of-week, time-to-departure (late bookings spike), and competitor actions. Airlines cannot observe true demand in real time, only realized bookings.
- **Time-to-departure pressure:** As departure nears, the decision horizon shrinks. Markdowns to fill remaining seats conflict with protecting premium seats for late high-value bookings.
- **Willingness-to-pay segmentation:** Customers have heterogeneous valuations. Leisure travelers book early at lower prices; business travelers pay premium fares at the last minute. An airline must design fare classes and pricing to extract value from each segment.
- **Inventory tradeoffs:** Selling a cheap Y (Economy) seat to a customer today prevents selling that seat as an expensive J (First) ticket to a business traveler tomorrow. This tension between **dilution** (cannibalizing high-value demand with cheap fares) and **spoilage** (leaving seats empty) is central to revenue management.

### Why Static Pricing Is Inadequate: A Worked Example

Suppose an airline offers a single fare of \$300 for all bookings on a flight with 100 seats and 30 days until departure.

**Scenario A (Static pricing):**
- Days 1–15: Demand is low; only 40 customers buy at \$300. Seats remain unsold.
- Days 16–30: Demand spikes. 50 customers are willing to pay \$500, but the airline has only 60 seats left. The airline either:
  - Accepts \$300 from all remaining demand and leaves 10 seats empty → **spoilage loss of \$3,000**.
  - Turns away the 50 late customers → **dilution loss of \$10,000**.
  
  **Total revenue: \$12,000 (40 × \$300 + 60 × \$300).**

**Scenario B (Dynamic pricing):**
- Days 1–15: Low demand; offer \$250 to attract leisure. Sell 50 seats.
- Days 16–30: High demand; raise fare to \$450 for remaining 50 seats. Sell all 50 at premium.
  
  **Total revenue: \$22,500 (50 × \$250 + 50 × \$450).**

The difference of \$10,500 is pure margin—captured only by reprice decisions informed by demand forecasts and inventory levels. This is Bookero's value proposition.

---

## 2. Bookero Goals and Non-Goals

### Goals

1. **Implement a coherent revenue-management system** that maps demand forecasts and inventory state into dynamic fare classes.
2. **Demonstrate algorithmic families** (search, optimization, DP, greedy, heuristics, ML pipelines) applied to a real airline domain.
3. **Provide an Algorithm Lab** for side-by-side comparison: baseline (static) vs. optimized algorithms, with recorded metrics (latency, revenue lift, load factor).
4. **Enable role-based usage:** Analysts simulate demand and trigger reprices; travelers search and book at live dynamic prices.
5. **Produce evaluable evidence** of computational thinking: clear problem decomposition, pattern recognition from bookings/demand, abstraction via formal models, and algorithmic design backed by pseudocode and tests.

### Non-Goals

- **Multi-airline OTA:** Bookero models a single carrier. Inter-airline pricing and codeshare logic are out of scope.
- **Live GDS integration:** No connection to real-time IATA message formats, live competitor feeds, or live payment processing.
- **Production HA deployment:** No load balancing, multi-region failover, or compliance auditing. Docker Compose on a single machine suffices for evaluation.
- **Mobile or native apps:** Web-only (Next.js). React Native is out of scope.
- **Real payment processing:** PCI scope is avoided; simulation uses synthetic bookings.

---

## 3. Stakeholders and Actors

| Stakeholder | Needs | Bookero role |
|-------------|-------|-------------|
| **Traveler** | Find and book flights at transparent dynamic fares | Search, view prices, book; role `TRAVELER` |
| **Revenue Analyst** | Simulate demand scenarios, trigger reprice, compare algorithm performance | Simulator control, algorithm lab, ops dashboard; role `ANALYST` |
| **System (Simulator)** | Inject synthetic demand, decay/reprice trigger logic | Autonomous demand model, optional light-reprice after bookings |

---

## 4. Computational Thinking Pillars and Bookero Artefacts

| Pillar | Definition | Bookero Expression | Module/File |
|--------|-----------|-------------------|------------|
| **Decomposition** | Break complex problem into independent subproblems | Separate concerns: routing (graph), inventory (transactional), pricing (optimization), analytics (forecasting), web (UI layers) | `services/api/route`, `services/api/inventory`, `services/api/pricing`, `services/analytics/demand`, `apps/web/app/ops` |
| **Pattern Recognition** | Identify repeating structures; learn from data | Booking history → demand distribution; time-series patterns in booking curves; fare-class elasticity learned via ML | `services/analytics/eda.py`, `services/analytics/demand.py`, demand_snapshot table |
| **Abstraction** | Hide detail behind clear interfaces; use models | Fare-class ladder (Y < B < M < J); bid-price model for seat allocation; booking class nesting; `Algorithm` interface | `services/api/flight/FareClassEntity`, `services/api/algorithms/Algorithm.java`, ER diagram (§6) |
| **Algorithmic Thinking** | Design step-by-step procedures; analyze complexity; test rigorously | Algorithm pack (9 keys) with pseudocode, flowcharts, complexity analysis, unit tests, performance metrics | `services/api/algorithms/**Algorithm.java`, `docs/algorithms/<key>.md`, `services/analytics/app/demand.py` |

---

## 5. Learning Emphasis Mapping

The course emphasizes nine algorithmic families. Below, each is mapped to a Bookero algorithm key that instantiates and tests it:

| Learning Emphasis | Bookero Algorithm Key | Purpose in domain |
|-------------------|----------------------|------------------|
| **Search algorithms** | `flight_search` | Constrained itinerary search under time/cost limits; BFS-style expansion of flight legs |
| **Optimization algorithms** | `revenue_optimize` | Maximize expected revenue under capacity constraint; integrates demand forecast and seat allocation |
| **Shortest path algorithms** | `shortest_path` | Dijkstra origin → destination on airport/route graph; enables connection-based search |
| **Scheduling algorithms** | `slot_schedule` | Assign seeded departures to abstract slot times; heuristic gate/turnaround optimization |
| **Greedy algorithms** | `greedy_protection` | Incrementally close lower fare classes as load factor rises; greedy criterion is immediate protection value |
| **Dynamic programming** | `dp_seat_protect` | EMSR-style multi-class seat allocation; DP recurrence allocates seats given demand and fare differences |
| **Graph algorithms** | `route_graph` | Build weighted airport/route graph; analyze connectivity, transitivity for search space pruning |
| **Heuristic algorithms** | `time_pressure_heuristic` | Adjust prices by days-to-departure × load factor; captures domain intuition without formal optimization |
| **Machine learning pipelines** | `demand_ml` | Fit demand model on booking/time snapshots; forecast demand by flight; feed forecast into `revenue_optimize` |

Each algorithm ships with: pseudocode (docs), Mermaid flowchart (docs), unit test (src/test), implementation (src/main), and performance metrics (Algorithm Lab, `docs/05-evaluation.md`). Lab and live reprice share the same implementation (enforced via `Algorithm` interface + `AlgorithmRegistry`).

---

## 6. Success Criteria and Measurement

| Criterion | Metric | How Measured |
|-----------|--------|------------|
| **Algorithm latency** | Reprice completes in < 500ms | API endpoint `POST /api/pricing/reprice` wall-clock time; recorded in `algorithm_run.duration_ms` |
| **Revenue uplift** | Each algorithm ≥ baseline or explains tradeoff | `algorithm_run.revenue_delta` vs. baseline run on same flights; dashboard by algorithm key |
| **Load factor** | Target 85%+ for all flights | (bookings at departure time) / seats_total; aggregated in `/metrics/revenue` dashboard |
| **Average fare** | Increases with reprice (not stationary) | Mean `booking.paid_price` grouped by flight and algorithm; tracked in `/metrics/revenue` |
| **Forecast accuracy** | Demand model MAE/RMSE < 20% of mean booking rate | After training on 10+ days of synthetic demand, validate model on held-out flights; reported in analytics `/eda/summary` and `docs/05-evaluation.md` |
| **Booking transaction safety** | Zero oversell in concurrent load | Concurrency test: 10 threads attempt to book 1 remaining seat; exactly 1 succeeds; others receive HTTP 409; see `services/api/booking/BookingServiceTest.java` |
| **Full-loop demo** | Complete pricing cycle in < 5 minutes | Analyst simulates → reprice → traveler searches → books → dashboard updates; scripted in `scripts/demo-walkthrough.md` |

---

## 7. Assumptions and Constraints

### Assumptions

1. **Demand is exogenous and Markovian:** Booking rate depends on current inventory and time-to-departure, not price history. (Elasticity is ignored in v1; stretch goal.)
2. **Single-leg flights only:** No multi-leg connections in the initial dynamic-pricing algorithm. Routing algorithms (graph, shortest path) are demonstrated on the reference network but routing is separate from pricing in the MVP.
3. **Capacity is fixed:** No overbooking, no no-shows modeled; inventory decrements only on booking.
4. **Demand snapshot is clean:** Simulator produces realistic booking patterns without data quality issues.
5. **Analytics down is tolerable:** If Python analytics is unavailable, the API falls back to baseline pricing and the dashboard shows stale data; bookings still proceed.

### Constraints

1. **Single-airline scope:** Revenue-management rules (bid pricing, nesting, class hierarchy) are airline-specific and not parameterized for multi-airline competition.
2. **No real-time external feeds:** ETL imports static OpenFlights reference data at startup; no live competitor pricing or market data.
3. **Docker Compose default:** Native development is supported via `scripts/env.sh` (documented) but evaluation on native stack is not a requirement.
4. **No multi-tenant isolation:** All users share one airline's inventory; no per-customer pricing.
5. **PostgreSQL 16 as sole database:** No MongoDB, Supabase, or data warehouse; all state in PostgreSQL.

---

## 8. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| **Oversell race condition** | Medium | High (evaluation blocker) | Pessimistic lock (SELECT … FOR UPDATE) on inventory; transactional test (Task 12) before Phase 3 |
| **Algorithm latency exceeds 500ms** | Medium | Medium (demo impact) | Profile under seed load early; avoid nested loops over fare classes; cache demand forecast |
| **Demand model overfits to synthetic data** | Medium | Low (alternative heuristic used) | Train on mixed synthetic + small real dataset if available; otherwise fallback to `time_pressure_heuristic` |
| **Analytics service crashes during demo** | Low | Medium (dashboard down) | Bookings/pricing unaffected (fallback to baseline); pre-demo health check in walkthrough script |
| **Mermaid diagram render fails on evaluation device** | Low | Low (context remains in prose) | Validate all diagrams syntactically before commit; test in markdown viewer used for grading |
| **Git repo bloated by seed data** | Low | Low (local issue) | Seed data in .gitignore; generated on first run via ETL + SeedService |

---

## 9. Definition of Done: Phase 1

- [x] Airline revenue-management problem articulated with worked numerical example.
- [x] Bookero goals and explicit non-goals documented.
- [x] Stakeholders identified (traveler, analyst, system).
- [x] Computational Thinking pillars mapped to Bookero modules (decomposition, pattern recognition, abstraction, algorithmic thinking).
- [x] Learning emphasis items (search, optimization, shortest path, scheduling, greedy, DP, graph, heuristic, ML) mapped to algorithm keys with domain justification.
- [x] Success criteria and measurement method for each stated for reproducibility.
- [x] Assumptions and constraints enumerated (single airline, no elasticity, Markovian demand, etc.).
- [x] Risks and mitigations table documents known threats and responses.
- [x] This document is internally consistent with `BUILD.md` and design spec `2026-07-28-bookero-design.md`.

---

## References

- **Course Topic:** #66 Dynamic Pricing Algorithm for Airlines
- **Design Spec:** `docs/superpowers/specs/2026-07-28-bookero-design.md`
- **Build Bible:** `BUILD.md` (§2 delivery framework; §8 algorithm keys)
- **Learning Emphasis:** `ATTENTION ‼️.txt` (search, optimization, shortest path, scheduling, greedy, DP, graph, heuristic, ML)
