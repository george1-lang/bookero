# Bookero - BUILD.md (Claude Code build bible)

**Product:** Bookero - single-airline dynamic pricing, booking, revenue analytics  
**Topic:** #66 Dynamic Pricing Algorithm for Airlines  
**Spec:** `docs/superpowers/specs/2026-07-28-bookero-design.md`  
**Plan:** `docs/superpowers/plans/2026-07-28-bookero.md`  

Execute this document top-to-bottom. Check off work in the plan file. Commit **one file per conventional commit**.

---

## 0. Non-negotiable standards

1. Optimize like a senior engineer: clear modules, no dead code, indexed queries, no N+1, transactional booking, pagination on lists, cache airports/routes where safe.
2. Correctness first; measure algorithm latency; avoid clever micro-opts that obscure logic.
3. Sparse comments - invariants, formulas, trade-offs only.
4. Commit style: `feat|fix|docs|test|chore|refactor(scope): message` - **exactly one file staged per commit**.
5. TDD where the plan shows a failing test first; otherwise write tests in the same task before marking done.
6. No React Native, MongoDB, Supabase, live GDS, or real payments in MVP.

---

## 1. Target tree

```
bookero/
  CLAUDE.md
  BUILD.md
  docker-compose.yml
  .gitignore
  apps/web/                 # Next.js App Router
  services/api/             # Spring Boot 3.x + Java 21
  services/analytics/       # FastAPI + Pandas/NumPy/sklearn
  docs/
    01-problem-framing.md
    02-system-design.md
    05-evaluation.md
    06-technical-report.md
    user-testing.md
    algorithms/             # one md per algorithm key
  data/raw/
  data/processed/
  scripts/seed.sh
  scripts/demo-walkthrough.md
```

If the workspace root is already `mannerz`, create the above layout **in this root** (do not nest an extra `bookero/` folder unless renaming the repo).

---

## 2. Delivery Framework checklist (must all exist before “done”)

| Phase | Output |
|-------|--------|
| 1 Problem Framing | `docs/01-problem-framing.md` |
| 2 System Design | `docs/02-system-design.md` (Mermaid: architecture, use cases, ER, sequences) |
| 3 App Development | Working Next.js + Spring via Docker Compose |
| 4 Data Pipeline | Python ETL + ops analytics dashboard |
| 5 Evaluation | Lab metrics + `docs/05-evaluation.md` + `docs/user-testing.md` |
| 6 Documentation | `docs/06-technical-report.md` (research-paper structure) |

**Algorithmic Thinking (per algorithm key):** description, pseudocode, Mermaid flowchart, implementation, tests, performance numbers in `docs/algorithms/<key>.md` and Lab UI.

---

## 3. Docker Compose

Services: `postgres` (16), `api` (8080), `analytics` (8001), `web` (3000).

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: bookero
      POSTGRES_USER: bookero
      POSTGRES_PASSWORD: bookero
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bookero -d bookero"]
      interval: 5s
      timeout: 5s
      retries: 10

  api:
    build: ./services/api
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bookero
      SPRING_DATASOURCE_USERNAME: bookero
      SPRING_DATASOURCE_PASSWORD: bookero
      ANALYTICS_BASE_URL: http://analytics:8001
      JWT_SECRET: bookero-dev-secret-change-me-32chars
    depends_on:
      postgres:
        condition: service_healthy

  analytics:
    build: ./services/analytics
    ports: ["8001:8001"]
    environment:
      DATABASE_URL: postgresql://bookero:bookero@postgres:5432/bookero
    depends_on:
      postgres:
        condition: service_healthy

  web:
    build: ./apps/web
    ports: ["3000:3000"]
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080
      NEXT_PUBLIC_ANALYTICS_URL: http://localhost:8001
    depends_on: [api, analytics]

volumes:
  pgdata:
```

---

## 4. Database schema (Flyway or Liquibase - prefer Flyway)

Create `services/api/src/main/resources/db/migration/V1__init.sql`:

```sql
CREATE TABLE airport (
  code VARCHAR(8) PRIMARY KEY,
  name TEXT NOT NULL,
  city TEXT,
  country TEXT,
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION
);

CREATE TABLE route (
  id UUID PRIMARY KEY,
  origin_code VARCHAR(8) NOT NULL REFERENCES airport(code),
  dest_code VARCHAR(8) NOT NULL REFERENCES airport(code),
  distance_km INT,
  UNIQUE (origin_code, dest_code)
);

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role VARCHAR(16) NOT NULL CHECK (role IN ('TRAVELER', 'ANALYST'))
);

CREATE TABLE flight (
  id UUID PRIMARY KEY,
  route_id UUID NOT NULL REFERENCES route(id),
  flight_no VARCHAR(16) NOT NULL,
  depart_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_flight_depart ON flight (depart_at);
CREATE INDEX idx_flight_route ON flight (route_id);

CREATE TABLE fare_class (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id) ON DELETE CASCADE,
  code VARCHAR(8) NOT NULL,
  base_price NUMERIC(12,2) NOT NULL,
  current_price NUMERIC(12,2) NOT NULL,
  seats_allocated INT NOT NULL,
  UNIQUE (flight_id, code)
);

CREATE TABLE inventory (
  flight_id UUID PRIMARY KEY REFERENCES flight(id) ON DELETE CASCADE,
  seats_total INT NOT NULL,
  seats_left INT NOT NULL CHECK (seats_left >= 0)
);

CREATE TABLE booking (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  flight_id UUID NOT NULL REFERENCES flight(id),
  fare_class_id UUID NOT NULL REFERENCES fare_class(id),
  paid_price NUMERIC(12,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_booking_user ON booking (user_id);
CREATE INDEX idx_booking_flight ON booking (flight_id);

CREATE TABLE algorithm_run (
  id UUID PRIMARY KEY,
  algorithm_key VARCHAR(64) NOT NULL,
  params JSONB,
  status VARCHAR(16) NOT NULL,
  duration_ms BIGINT,
  revenue_delta NUMERIC(14,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE price_history (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id),
  algorithm_run_id UUID REFERENCES algorithm_run(id),
  fare_class_code VARCHAR(8) NOT NULL,
  price NUMERIC(12,2) NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_price_history_flight ON price_history (flight_id, at DESC);

CREATE TABLE demand_snapshot (
  id UUID PRIMARY KEY,
  flight_id UUID NOT NULL REFERENCES flight(id),
  demand_score DOUBLE PRECISION NOT NULL,
  at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_demand_flight ON demand_snapshot (flight_id, at DESC);
```

Seed users (password `password` bcrypt) in `V2__seed_users.sql`:  
- `analyst@bookero.local` / `ANALYST`  
- `traveler@bookero.local` / `TRAVELER`

---

## 5. Spring API (`services/api`)

**Stack:** Java 21, Spring Boot 3.3+, Spring Web, Data JPA, Security, Validation, Flyway, PostgreSQL driver.

### Packages

```
com.bookero
  BookeroApplication.java
  auth/
  airport/
  route/
  flight/
  inventory/
  booking/
  simulation/
  pricing/
  algorithms/
    Algorithm.java              # interface
    AlgorithmRegistry.java
    AlgorithmRunService.java
    RouteGraphAlgorithm.java
    ShortestPathAlgorithm.java
    FlightSearchAlgorithm.java
    SlotScheduleAlgorithm.java
    GreedyProtectionAlgorithm.java
    DpSeatProtectAlgorithm.java
    RevenueOptimizeAlgorithm.java
    TimePressureHeuristicAlgorithm.java
    BaselineAlgorithm.java
  common/                       # ApiException, ProblemDetail handler
```

### Algorithm interface

```java
public interface Algorithm {
  String key();
  String displayName();
  AlgorithmResult execute(AlgorithmContext ctx);
}
```

`AlgorithmContext`: flight ids (optional = all open), demand map, inventory, fare classes.  
`AlgorithmResult`: durationMs, revenueDelta, per-flight price updates, status.

**Rule:** `POST /api/pricing/reprice` and `POST /api/algorithms/{key}/run` both call `AlgorithmRegistry.get(key).execute(...)`.

### HTTP API

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/login` | public `{email,password}` → `{token, role}` |
| GET | `/api/flights/search?origin&dest&date` | authenticated |
| GET | `/api/flights/{id}` | authenticated |
| POST | `/api/bookings` | TRAVELER `{flightId, fareClassId}` |
| GET | `/api/bookings/me` | TRAVELER |
| POST | `/api/simulate` | ANALYST `{intensity?: number}` |
| POST | `/api/pricing/reprice` | ANALYST `{algorithmKey, flightIds?}` |
| GET | `/api/algorithms` | ANALYST |
| POST | `/api/algorithms/{key}/run` | ANALYST |
| GET | `/api/algorithms/runs` | ANALYST |
| GET | `/api/ops/inventory` | ANALYST |

### Booking transaction

```text
BEGIN
  SELECT inventory FOR UPDATE
  IF seats_left < 1 THEN fail 409
  seats_left -= 1
  INSERT booking
COMMIT
```

Optionally trigger light reprice after book (time_pressure_heuristic) - feature flag default on for demo.

---

## 6. Analytics (`services/analytics`)

**Stack:** Python 3.12, FastAPI, uvicorn, psycopg/sqlalchemy, pandas, numpy, scikit-learn. PyTorch optional for a small MLP demand model; sklearn `GradientBoostingRegressor` is acceptable MVP.

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | liveness |
| POST | `/etl/run` | download/parse OpenFlights → upsert airports/routes (limit to usable subset: e.g. top airports + ACC hub focus) |
| GET | `/eda/summary` | row counts, demand distribution, revenue totals |
| POST | `/demand/train` | fit model on synthetic + snapshot features |
| GET | `/demand/forecast` | `{flightId, demandScore}[]` |
| GET | `/metrics/revenue` | totalRevenue, baselineRevenue, loadFactor, avgFare, byDay |

### ETL notes

- Source: [OpenFlights](https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat) and `routes.dat`.
- Store raw under `data/raw/`.
- Do not load the entire world into demo flights - ETL full reference tables, then a **seed job** (API or script) creates ~20-40 Bookero flights on a hub network (include ACC if present).

---

## 7. Web (`apps/web`)

**Stack:** Next.js 15 App Router, TypeScript, fetch to Spring/Analytics. Distinctive but restrained UI (not generic purple SaaS). CSS modules or Tailwind OK.

### Routes

| Path | Role | Purpose |
|------|------|---------|
| `/login` | public | login |
| `/` | traveler | search |
| `/flights/[id]` | traveler | fares + book |
| `/bookings` | traveler | history |
| `/ops` | analyst | inventory + simulate + reprice |
| `/ops/dashboard` | analyst | revenue charts from analytics |
| `/ops/lab` | analyst | Algorithm Lab |
| `/ops/lab/[key]` | analyst | run + link to docs |

Algorithm Lab must show: key, description, last duration_ms, revenue_delta vs baseline, run button, link to `/docs` content or embedded markdown from `docs/algorithms/<key>.md` (can be statically imported or fetched as public files copied into `apps/web/public/docs`).

---

## 8. Algorithm keys (implement all)

| Key | Family | Location |
|-----|--------|----------|
| `baseline` | control | Spring |
| `route_graph` | Graph | Spring - build adjacency; Lab shows node/edge counts |
| `shortest_path` | Shortest path | Spring - Dijkstra origin→dest |
| `flight_search` | Search | Spring - constrained itinerary expansion used by search API |
| `slot_schedule` | Scheduling | Spring - assign seeded departures to abstract gates/slots |
| `greedy_protection` | Greedy | Spring - protect Y/J as load rises |
| `dp_seat_protect` | DP | Spring - EMSR-style or multi-class DP allocation |
| `revenue_optimize` | Optimization | Spring - uses analytics demand forecast |
| `time_pressure_heuristic` | Heuristic | Spring - days-to-departure × load factor |
| `demand_ml` | ML pipeline | Python train + Spring consume forecasts |

Each gets `docs/algorithms/<key>.md` with: Purpose, Pseudocode, Mermaid flowchart, Complexity, Implementation path, Tests, Performance results table (fill after runs).

---

## 9. Implementation order

1. Git init (if needed), `.gitignore`, `CLAUDE.md`, this `BUILD.md`  
2. `docs/01-problem-framing.md`  
3. `docs/02-system-design.md` (copy/adapt Mermaid from design spec)  
4. `docker-compose.yml` + Postgres up  
5. Spring skeleton + Flyway V1/V2  
6. Auth login JWT  
7. Entities/repos for domain  
8. Analytics ETL + health  
9. Seed flights script/endpoint  
10. Flight search + inventory  
11. Booking with oversell protection + tests  
12. Demand simulator  
13. Baseline + each algorithm (doc → test → impl → Lab wire → commit per file)  
14. Demand ML + revenue_optimize integration  
15. Next.js pages for traveler + ops + lab + dashboard  
16. `scripts/demo-walkthrough.md`  
17. `docs/05-evaluation.md`, `docs/user-testing.md`  
18. `docs/06-technical-report.md`  
19. Full Compose smoke of demo script  
20. Stretch: cloud notes in `docs/deploy-stretch.md`

---

## 10. Definition of done

- [ ] `docker compose up --build` serves web on :3000, api :8080, analytics :8001  
- [ ] Analyst can simulate, reprice, view Lab metrics  
- [ ] Traveler can search, see dynamic price, book without oversell  
- [ ] All algorithm keys documented (Mermaid + pseudo) + tested + runnable  
- [ ] Phases 1-6 docs present and consistent with running system  
- [ ] Commit history is conventional and one-file-per-commit  

---

## 11. Eval demo (5-7 min)

Follow `scripts/demo-walkthrough.md`: login analyst → ETL/seed status → simulate → Lab compare baseline vs dp vs greedy → login traveler → search → book → dashboard refresh.
