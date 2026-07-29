# Bookero

Dynamic pricing and revenue management for a single airline. Simulate demand against a
real route network, reprice the fare ladder with ten explicit algorithms, let travellers
book at the live price, and measure whether any of it actually earned more money than
static pricing.

Built for the Computational Systems and Problem Solving capstone, topic 66,
"Dynamic Pricing Algorithm for Airlines".

---

## The headline result

Six pricing strategies, each measured against a static-pricing control on identical
seeded demand. The only difference between arms is the pricing decision.

| Strategy | Family | Revenue vs control (low load) | Revenue vs control (high load) |
|---|---|---:|---:|
| `baseline` | Control | control | control |
| `revenue_optimize` | Optimisation | **+19.30%** | -0.46% |
| `demand_ml` | Machine learning | +9.37% | +5.88% |
| `dp_seat_protect` | Dynamic programming | +6.72% | **+6.98%** |
| `greedy_protection` | Greedy | 0.00% | -0.50% |
| `time_pressure_heuristic` | Heuristic | -21.69% | -26.25% |

The heuristic that raises fares as departure approaches, which sounds entirely
reasonable, is the worst thing you can do: it marks up without any demand signal and
loses more than a fifth of revenue in both regimes. Full analysis in
[`docs/05-evaluation.md`](docs/05-evaluation.md).

---

## Running it

### Docker Compose

```bash
docker compose up --build
API=http://localhost:8080 ANALYTICS=http://localhost:8001 ./scripts/seed.sh
```

Web on <http://localhost:3000>, API on 8080, analytics on 8001.

### Natively, without Docker

```bash
source scripts/env.sh
./scripts/stack.sh up      # postgres 5433, analytics 8001, api 8090, web 3100
./scripts/seed.sh
```

### Sign in

| Role | Email | Password |
|---|---|---|
| Revenue analyst | `analyst@bookero.local` | `password` |
| Traveller | `traveler@bookero.local` | `password` |

These are demo credentials and are deliberately public.
[`docs/deploy-stretch.md`](docs/deploy-stretch.md) section 5 lists what must change
before deploying anywhere real.

---

## Architecture

| Service | Stack | Responsibility |
|---|---|---|
| `services/api` | Spring Boot 4.1, Java 21, Hibernate 7, Flyway | Domain, bookings, simulation, all ten algorithms |
| `services/analytics` | FastAPI, Python 3.13, pandas 3, scikit-learn 1.9 | OpenFlights ETL, demand model, revenue metrics |
| `apps/web` | Next.js 16, React 19, CSS Modules | Traveller booking and the analyst ops console |
| `postgres` | PostgreSQL | System of record |

The Algorithm Lab and the live reprice endpoint execute the **same** code path
(`AlgorithmRunService`), so a measurement taken in the Lab describes what production
actually does.

---

## The algorithms

| Key | Family | What it computes |
|---|---|---|
| `baseline` | Control | Restores every fare to its published base price |
| `route_graph` | Graph | Weighted adjacency over 3,257 airports and 37,042 routes; degree, components |
| `shortest_path` | Shortest path | Dijkstra with a binary heap from the carrier hub |
| `flight_search` | Search | Best-first itinerary expansion under hop and connection-time constraints |
| `slot_schedule` | Scheduling | Stand assignment by earliest-finishing-time interval scheduling |
| `greedy_protection` | Greedy | Withdraws discount buckets in fare order as the cabin fills |
| `dp_seat_protect` | Dynamic programming | Seat allocation recurrence over (seats, class), derives booking limits and a bid price |
| `revenue_optimize` | Optimisation | Golden-section search for the revenue-maximising fare multiplier |
| `time_pressure_heuristic` | Heuristic | Closed-form markup from days to departure and load factor |
| `demand_ml` | Machine learning | Applies the gradient-boosting demand forecast to fares |

Each has a documentation pack in [`docs/algorithms/`](docs/algorithms/) with purpose,
pseudocode, a flowchart, complexity, design rationale, tests and measured performance.

---

## Tests

```bash
cd services/api && mvn -B test              # 39 passing
services/analytics/.venv/Scripts/python.exe -m pytest services/analytics/tests -q   # 12 passing
cd e2e && npx playwright test               # 46 passing (38 API contract, 8 browser)
```

`BookingConcurrencyIT` fires eight concurrent bookings at a single remaining seat and
asserts exactly one succeeds, seven receive HTTP 409, and the cabin rests at zero seats
rather than going negative.

---

## Documentation

| Phase | Document |
|---|---|
| 1. Problem framing | [`docs/01-problem-framing.md`](docs/01-problem-framing.md) |
| 2. System design | [`docs/02-system-design.md`](docs/02-system-design.md) |
| 4. Algorithm packs | [`docs/algorithms/`](docs/algorithms/) |
| 5. Evaluation | [`docs/05-evaluation.md`](docs/05-evaluation.md), [`docs/user-testing.md`](docs/user-testing.md) |
| 6. Technical report | [`docs/06-technical-report.md`](docs/06-technical-report.md) |
| Demo script | [`scripts/demo-walkthrough.md`](scripts/demo-walkthrough.md) |
| Deployment | [`docs/deploy-stretch.md`](docs/deploy-stretch.md) |

[`deliverables/`](deliverables/) holds the report as a `.docx` with every diagram
embedded, the Mermaid and PlantUML sources, rendered PNGs and UI screenshots. See
[`deliverables/README.md`](deliverables/README.md) for how to regenerate any of it.

---

## Reproducing the numbers

```bash
./scripts/stack.sh up
node scripts/benchmark.mjs                        # per-algorithm latency
node scripts/experiment.mjs                       # revenue A/B, low starting load
WAVE_ONE=7 WAVE_TWO=9 node scripts/experiment.mjs # revenue A/B, high starting load
```

Results land in `data/processed/` and are the source of every figure quoted above.
