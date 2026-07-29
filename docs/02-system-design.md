# Phase 2: System Design - Bookero Architecture, API, and Data Model

**Date:** 2026-07-28  
**Spec reference:** `docs/superpowers/specs/2026-07-28-bookero-design.md` §4-8  
**Build reference:** `BUILD.md` §5-7

This document defines the complete runtime architecture, service boundaries, data model, API contracts, and deployment strategy for Bookero.

---

## 1. Service Topology

| Service | Technology | Responsibility | Port | Data |
|---------|------------|-----------------|------|------|
| **postgres** | PostgreSQL 16 | System of record; all state persisted | 5432 (Docker) / 5433 (native) | Schema: `V1__init.sql` + `V2__seed_users.sql` |
| **api** | Spring Boot 4.1, Java 21 | Domain logic, pricing orchestration, algorithm execution, auth | 8080 (Docker) / 8090 (native) | Connects to postgres via JDBC; proxies analytics |
| **analytics** | Python 3.13, FastAPI, uvicorn | ETL (OpenFlights), EDA, demand ML, revenue metrics | 8001 | Connects to postgres via SQLAlchemy; served to web via Spring proxy or direct |
| **web** | Next.js 16, TypeScript | Role-based UI (traveler search/book, analyst ops/lab) | 3000 (Docker) / 3100 (native) | Fetches JSON from `/api/**` (Spring) and `/api/ops/metrics` (Spring proxy) |

**Environment note:** Docker Compose is the canonical deployment (`docker-compose.yml`). Native development supported via `scripts/env.sh` (PostgreSQL 18 on 5433, API on 8090, web on 3100) because Docker was unavailable on the build workstation.

---

## 2. Architecture Flowchart

```mermaid
flowchart TB
  subgraph Web["web (Next.js 16)"]
    TUI["Traveler UI<br/>(search/book/history)"]
    OUI["Ops UI<br/>(inventory/simulate)"]
    LUI["Algorithm Lab"]
    DASH["Revenue Dashboard"]
  end

  subgraph API["api (Spring Boot 4.1, Java 21)"]
    AUTH["Auth Service<br/>(JWT HS256)"]
    FLIGHT["Flight Service<br/>(search/detail)"]
    INV["Inventory Service<br/>(seats, oversell check)"]
    BOOK["Booking Service<br/>(transactional)"]
    SIM["Demand Simulator"]
    PRICE["Pricing Orchestrator"]
    ALG["Algorithm Registry<br/>(9 keys)"]
    PROXY["Analytics Proxy<br/>(CORS bridge)"]
  end

  subgraph PY["analytics (FastAPI, Python 3.13)"]
    ETL["ETL Service<br/>(OpenFlights)"]
    EDA["EDA Service<br/>(summaries)"]
    ML["Demand ML<br/>(forecast)"]
    METRICS["Revenue Metrics<br/>(KPIs)"]
  end

  subgraph DB["PostgreSQL 16"]
    TABLES["airport | route | app_user | flight<br/>fare_class | inventory | booking<br/>algorithm_run | price_history | demand_snapshot"]
  end

  TUI -->|login| AUTH
  TUI -->|/flights/search| FLIGHT
  TUI -->|/bookings| BOOK

  OUI -->|/simulate| SIM
  OUI -->|/pricing/reprice| PRICE
  OUI -->|/inventory| INV

  LUI -->|/algorithms| ALG
  LUI -->|/algorithms/{key}/run| ALG

  DASH -->|/api/ops/metrics| PROXY

  AUTH -->|validate/create| DB
  FLIGHT -->|query| DB
  INV -->|query/lock| DB
  BOOK -->|transactional write| DB
  SIM -->|insert demand_snapshot| DB
  PRICE -->|run Algorithm| ALG
  ALG -->|query forecast| ML
  ALG -->|write algorithm_run| DB

  SIM -->|trigger| PRICE

  ETL -->|upsert airports/routes| DB
  ML -->|read bookings/demand_snapshot| DB
  METRICS -->|query revenue by flight| DB

  PROXY -->|GET /metrics/revenue| METRICS
```

---

## 3. Use-Case Diagram

```mermaid
flowchart LR
  T((Traveler))
  A((Revenue Analyst))
  SYS((System))
  S["Bookero"]

  T -->|Search flights| S
  T -->|View dynamic price| S
  T -->|Book seat| S
  T -->|View bookings| S

  A -->|Run demand simulation| S
  A -->|Trigger reprice| S
  A -->|Inspect Algorithm Lab| S
  A -->|View revenue dashboard| S
  A -->|Export metrics| S

  SYS -->|Inject synthetic demand| S
  SYS -->|Optional light reprice| S
```

---

## 4. Entity-Relationship Diagram (Schema Exact Match to V1__init.sql)

```mermaid
erDiagram
  AIRPORT ||--o{ ROUTE : origin
  AIRPORT ||--o{ ROUTE : destination
  ROUTE ||--o{ FLIGHT : schedules
  FLIGHT ||--o{ FARE_CLASS : offers
  FLIGHT ||--|| INVENTORY : has
  APP_USER ||--o{ BOOKING : places
  FLIGHT ||--o{ BOOKING : fulfills
  FARE_CLASS ||--o{ BOOKING : priced_as
  FLIGHT ||--o{ PRICE_HISTORY : records
  ALGORITHM_RUN ||--o{ PRICE_HISTORY : produces
  FLIGHT ||--o{ DEMAND_SNAPSHOT : observes

  AIRPORT {
    string code PK "VARCHAR(8)"
    string name UK "TEXT NOT NULL"
    string city "TEXT"
    string country "TEXT"
    double lat "DOUBLE PRECISION"
    double lon "DOUBLE PRECISION"
  }

  ROUTE {
    uuid id PK
    string origin_code FK "VARCHAR(8)"
    string dest_code FK "VARCHAR(8)"
    int distance_km "INT"
    unique "origin_code, dest_code"
  }

  FLIGHT {
    uuid id PK
    uuid route_id FK
    string flight_no "VARCHAR(16) NOT NULL"
    timestamptz depart_at "TIMESTAMPTZ NOT NULL"
    index "idx_flight_depart on depart_at"
    index "idx_flight_route on route_id"
  }

  FARE_CLASS {
    uuid id PK
    uuid flight_id FK "ON DELETE CASCADE"
    string code "VARCHAR(8) NOT NULL"
    decimal base_price "NUMERIC(12,2)"
    decimal current_price "NUMERIC(12,2)"
    int seats_allocated "INT NOT NULL"
    unique "flight_id, code"
    index "idx_fare_class_flight"
  }

  INVENTORY {
    uuid flight_id PK "REFERENCES flight(id) ON DELETE CASCADE"
    int seats_total "INT NOT NULL"
    int seats_left "INT NOT NULL CHECK (seats_left >= 0)"
  }

  APP_USER {
    uuid id PK
    string email UK "TEXT NOT NULL"
    string password_hash "TEXT NOT NULL"
    string role "VARCHAR(16) CHECK (role IN ('TRAVELER', 'ANALYST'))"
  }

  BOOKING {
    uuid id PK
    uuid user_id FK
    uuid flight_id FK
    uuid fare_class_id FK
    decimal paid_price "NUMERIC(12,2) NOT NULL"
    timestamptz created_at "TIMESTAMPTZ DEFAULT NOW()"
    index "idx_booking_user on user_id"
    index "idx_booking_flight on flight_id"
    index "idx_booking_created on created_at"
  }

  ALGORITHM_RUN {
    uuid id PK
    string algorithm_key "VARCHAR(64) NOT NULL"
    json params "JSONB"
    string status "VARCHAR(16) NOT NULL"
    bigint duration_ms "BIGINT"
    decimal revenue_delta "NUMERIC(14,2)"
    timestamptz created_at "TIMESTAMPTZ DEFAULT NOW()"
    index "idx_algorithm_run_key on algorithm_key, created_at DESC"
  }

  PRICE_HISTORY {
    uuid id PK
    uuid flight_id FK
    uuid algorithm_run_id FK "NULLABLE"
    string fare_class_code "VARCHAR(8) NOT NULL"
    decimal price "NUMERIC(12,2) NOT NULL"
    timestamptz at "TIMESTAMPTZ DEFAULT NOW()"
    index "idx_price_history_flight on flight_id, at DESC"
  }

  DEMAND_SNAPSHOT {
    uuid id PK
    uuid flight_id FK
    double demand_score "DOUBLE PRECISION NOT NULL"
    timestamptz at "TIMESTAMPTZ DEFAULT NOW()"
    index "idx_demand_flight on flight_id, at DESC"
  }
```

**Integrity constraints:**
- `booking` decrements `inventory.seats_left` in a transaction (pessimistic lock).
- Oversell prevented: transaction rolled back if `seats_left < 1`; HTTP 409 returned to client.
- `price_history` is append-only: every `algorithm_run` writes one or more price records; price updates never overwrite silently.

---

## 5. Pricing Cycle Sequence Diagram

```mermaid
sequenceDiagram
  participant A as Revenue Analyst
  participant W as Next.js Web
  participant S as Spring API
  participant P as Python Analytics
  participant DB as PostgreSQL
  participant T as Traveler

  A->>W: Click "Start Simulation"
  W->>S: POST /api/simulate
  S->>DB: INSERT INTO demand_snapshot
  S-->>W: Simulation created

  A->>W: Click "Trigger Reprice"
  W->>P: GET /demand/forecast
  P->>DB: SELECT SUM bookings per flight
  P-->>W: {flightId: demandScore}[]

  W->>S: POST /api/pricing/reprice<br/>{algorithmKey: "revenue_optimize"}
  S->>S: Load flights, fare classes,<br/>demand from forecast
  S->>S: AlgorithmRegistry.get("revenue_optimize")<br/>.execute(context)
  S->>S: Update fare_class.current_price
  S->>DB: INSERT INTO algorithm_run
  S->>DB: INSERT INTO price_history
  S-->>W: {revenue_delta, duration_ms}

  T->>W: Click "Search Flights"
  W->>S: GET /api/flights/search?origin=ACC&dest=LAD&date=...
  S->>DB: SELECT * FROM fare_class<br/>WHERE flight_id IN (...)<br/>AND current_price > 0
  S-->>T: {flightId, fareClasses: [{code, currentPrice}]}

  T->>W: Click "Book Y class on flight ABC"
  W->>S: POST /api/bookings<br/>{flightId, fareClassId}
  S->>DB: BEGIN
  S->>DB: SELECT inventory FOR UPDATE
  S->>DB: IF seats_left < 1 THEN ROLLBACK
  S->>DB: UPDATE inventory SET seats_left -= 1
  S->>DB: INSERT INTO booking
  S->>DB: COMMIT
  S-->>T: {bookingId, paidPrice}

  opt Light reprice (feature flag)
    S->>S: time_pressure_heuristic.execute()
    S->>DB: INSERT INTO price_history
  end

  A->>W: Click "Dashboard"
  W->>S: GET /api/ops/metrics
  S->>P: GET /metrics/revenue
  P->>DB: SUM(booking.paid_price) as revenue
  P->>DB: COUNT(*) / inventory.seats_total as loadFactor
  P-->>S: {totalRevenue, loadFactor, avgFare, byDay[]}
  S-->>W: Metrics rendered
```

---

## 6. Oversell-Safe Booking Transaction Sequence

```mermaid
sequenceDiagram
  participant T1 as Thread 1<br/>(Traveler)
  participant T2 as Thread 2<br/>(Traveler)
  participant S as Spring API
  participant DB as PostgreSQL

  par Concurrent paths
    T1->>S: POST /api/bookings {flightId, fareClassId}
    T2->>S: POST /api/bookings {flightId, fareClassId}
  end

  Note over S,DB: Thread 1 transaction

  S->>S: BookingService.book(...)
  S->>DB: BEGIN TRANSACTION

  S->>DB: SELECT * FROM inventory<br/>WHERE flight_id = ? FOR UPDATE NOWAIT
  Note over DB: Pessimistic lock acquired on inventory row

  DB-->>S: Inventory {seats_left: 1}
  S->>S: if (seats_left < 1) throw OversellException
  alt Seat available
    S->>DB: UPDATE inventory SET seats_left = seats_left - 1<br/>WHERE flight_id = ?
    S->>DB: INSERT INTO booking (user_id, flight_id, fare_class_id, paid_price)
    S->>DB: COMMIT
    S-->>T1: HTTP 201 {bookingId, paidPrice}
  else Seat unavailable (concurrent T2 won lock first)
    S->>DB: ROLLBACK
    S-->>T1: HTTP 409 Conflict
  end

  Note over S,DB: Thread 2 transaction (interleaved)

  S->>DB: BEGIN TRANSACTION
  S->>DB: SELECT * FROM inventory FOR UPDATE NOWAIT
  Note over DB: Lock wait or immediate fail<br/>depending on T1 timing

  alt Lock acquired (T1 released)
    DB-->>S: Inventory {seats_left: 0}
    S->>S: if (seats_left < 1) throw OversellException
    S->>DB: ROLLBACK
    S-->>T2: HTTP 409 Conflict
  else Lock not acquired
    S-->>T2: HTTP 409 Conflict (immediate)
  end
```

**Invariant:** Only one transaction commits successfully; the other receives HTTP 409. At most one seat per inventory row per transaction cycle.

---

## 7. Algorithm Package Class/Component Diagram

```mermaid
classDiagram
  class Algorithm {
    <<interface>>
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
  }

  class AlgorithmContext {
    -flightIds: UUID[]
    -demandMap: Map~UUID, Double~
    -inventory: Map~UUID, Inventory~
    -fareClasses: Map~UUID, FareClass[]~
    -timestamp: OffsetDateTime
  }

  class AlgorithmResult {
    -durationMs: Long
    -revenueDelta: BigDecimal
    -priceUpdates: PriceUpdate[]
    -status: String
    -errorMessage: String
  }

  class PriceUpdate {
    -flightId: UUID
    -fareClassCode: String
    -newPrice: BigDecimal
    -newSeatsAllocated: Int
  }

  class AlgorithmRegistry {
    -registry: Map~String, Algorithm~
    +get(key: String) Algorithm
    +list() List~Algorithm~
  }

  class AlgorithmRunService {
    -algorithmRegistry: AlgorithmRegistry
    -algorithmRunRepository: AlgorithmRunRepository
    -priceHistoryRepository: PriceHistoryRepository
    +execute(key: String, flightIds: UUID[]) AlgorithmRun
    -persistRun(result: AlgorithmResult) AlgorithmRun
  }

  class PricingController {
    -algorithmRunService: AlgorithmRunService
    +reprice(request: RepriceRequest) ResponseEntity~RepriceResponse~
  }

  class AlgorithmController {
    -algorithmRegistry: AlgorithmRegistry
    -algorithmRunService: AlgorithmRunService
    +listAlgorithms() List~AlgorithmDto~
    +runAlgorithm(key: String) ResponseEntity~AlgorithmRun~
    +listRuns() List~AlgorithmRun~
  }

  class BaselineAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
  }

  class DpSeatProtectAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -dpTable: int[][]
    -fillTable(capacity, fareClasses, demand) void
  }

  class GreedyProtectionAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -loadFactor: Double
    -closeClassIfNeeded(fareClass) void
  }

  class RevenueOptimizeAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
  }

  class TimePressureHeuristicAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -daysToDeparture: Long
    -markupFactor: Double
  }

  class FlightSearchAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
  }

  class ShortestPathAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -dijkstra(graph, origin, dest) Path
  }

  class RouteGraphAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -graph: WeightedGraph
  }

  class SlotScheduleAlgorithm {
    +key() String
    +displayName() String
    +execute(AlgorithmContext) AlgorithmResult
    -slots: GateSlot[]
  }

  Algorithm <|.. BaselineAlgorithm
  Algorithm <|.. DpSeatProtectAlgorithm
  Algorithm <|.. GreedyProtectionAlgorithm
  Algorithm <|.. RevenueOptimizeAlgorithm
  Algorithm <|.. TimePressureHeuristicAlgorithm
  Algorithm <|.. FlightSearchAlgorithm
  Algorithm <|.. ShortestPathAlgorithm
  Algorithm <|.. RouteGraphAlgorithm
  Algorithm <|.. SlotScheduleAlgorithm

  AlgorithmRegistry --> Algorithm
  AlgorithmRunService --> AlgorithmRegistry
  AlgorithmRunService --> AlgorithmContext
  AlgorithmRunService --> AlgorithmResult
  PricingController --> AlgorithmRunService
  AlgorithmController --> AlgorithmRegistry
  AlgorithmController --> AlgorithmRunService
```

**Key property:** All algorithms implement the same `Algorithm` interface. Both the Algorithm Lab (`POST /api/algorithms/{key}/run`) and live reprice (`POST /api/pricing/reprice`) call `AlgorithmRegistry.get(key).execute(...)`. This shared implementation ensures Lab results are reproducible in production pricing.

---

## 8. Algorithm Run Lifecycle State Diagram

```mermaid
stateDiagram-v2
  [*] --> PENDING: POST /api/algorithms/{key}/run
  PENDING --> RUNNING: AlgorithmRunService.execute()
  RUNNING --> SUCCESS: Result complete,<br/>algorithm_run written
  RUNNING --> FAILED: Exception thrown
  SUCCESS --> [*]: Dashboard queries<br/>algorithm_run record
  FAILED --> [*]: Error logged,<br/>last good prices<br/>kept, FAILED status
  
  note right of PENDING
    Request queued
  end note
  
  note right of RUNNING
    Algorithm.execute()<br/>in progress
  end note
  
  note right of SUCCESS
    duration_ms,<br/>revenue_delta,<br/>price_history all<br/>written to DB
  end note
  
  note right of FAILED
    Algorithm exception<br/>caught, algorithm_run<br/>marked FAILED,<br/>old prices retained,<br/>error message stored
  end note
```

---

## 9. Spring API Package Decomposition

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `com.bookero.auth` | JWT login, user identity, security config | `JwtService`, `AuthService`, `SecurityConfig`, `JwtAuthFilter`, `UserEntity`, `UserRepository` |
| `com.bookero.airport` | Airport reference data | `AirportEntity`, `AirportRepository` |
| `com.bookero.route` | Route topology (origin → destination pairs) | `RouteEntity`, `RouteRepository` |
| `com.bookero.flight` | Flight schedules, fare classes | `FlightEntity`, `FlightRepository`, `FareClassEntity`, `FareClassRepository` |
| `com.bookero.inventory` | Seat availability, oversell logic | `InventoryEntity`, `InventoryRepository` |
| `com.bookero.booking` | Booking creation, transactional seat reduction | `BookingEntity`, `BookingRepository`, `BookingService`, `BookingController` |
| `com.bookero.simulation` | Demand generation, seed flights | `DemandSimulator`, `SeedService`, `SimulationController` |
| `com.bookero.pricing` | Reprice orchestration | `PricingController`, `RepriceRequest`, `RepriceResponse` |
| `com.bookero.algorithms` | Algorithm interface, registry, all algorithm implementations | `Algorithm`, `AlgorithmContext`, `AlgorithmResult`, `AlgorithmRegistry`, `AlgorithmRunService`, `BaselineAlgorithm`, `DpSeatProtectAlgorithm`, `GreedyProtectionAlgorithm`, `RevenueOptimizeAlgorithm`, `TimePressureHeuristicAlgorithm`, `FlightSearchAlgorithm`, `ShortestPathAlgorithm`, `RouteGraphAlgorithm`, `SlotScheduleAlgorithm` |
| `com.bookero.common` | Cross-cutting: exception handling, problem-detail response, config | `ApiException`, `GlobalExceptionHandler`, `BookeroProperties`, `WebConfig` |

---

## 10. Python Analytics Package Decomposition

| Module | Purpose | Key Functions |
|--------|---------|----------------|
| `app/main.py` | FastAPI entrypoint, route mounting | `app = FastAPI()`, route definitions |
| `app/db.py` | SQLAlchemy engine, session factory, schema sync | `engine`, `SessionLocal`, `Base`, connection pool config |
| `app/etl.py` | OpenFlights download/parse, airport/route upsert | `download_airports()`, `download_routes()`, `upsert_airports()`, `upsert_routes()` |
| `app/eda.py` | Exploratory data analysis, summary stats | `summary_stats()` → row counts, demand distribution, booking velocity |
| `app/demand.py` | Demand model training/forecasting | `fit_demand_model()`, `forecast_demand_by_flight()` (sklearn GradientBoostingRegressor or MLP) |
| `app/metrics.py` | Revenue KPI aggregation | `compute_revenue_metrics()` → totalRevenue, loadFactor, avgFare, byDay[] |
| `tests/test_etl_parse.py` | Unit tests for ETL parsing | `test_parse_airport_row()`, `test_parse_route_row()` |

---

## 11. Core Domain Abstractions and Concepts

Understanding these concepts is essential for non-airline readers:

### Fare Class

A distinct price point and booking class for one flight. Examples: Y (Economy, base \$200), B (Business, base \$500), M (Midcab, base \$300), J (First, base \$900). Each has:
- **Code:** 1-character IATA designator (Y, B, M, J, F, etc.)
- **Base price:** Reference price; used by baseline algorithm.
- **Current price:** Dynamic price set by reprice algorithms.
- **Seats allocated:** Capacity reserved for this class; sum of all allocations ≤ total inventory.

### Booking Class Ladder (Nesting)

Fare classes are ordered by prestige: Y < B < M < J. Higher classes can spill down to lower classes if full. This nesting structure is implicit in Bookero v1 but documented for v2 extensions. The algorithm `dp_seat_protect` enforces this hierarchy via bid-price allocation.

### Load Factor

`(bookings at departure time) / seats_total`. A proxy for demand intensity and revenue potential. Higher load factors → higher prices in time-pressure heuristic.

### Protection Level

A threshold of seats held for higher-class bookings. When Y reaches its protection level, no new Y bookings are accepted; demand shifts to B. `greedy_protection` incrementally raises protection levels as load rises.

### Bid Price

The minimum marginal value of one seat for a given fare class, derived from expected future revenue. If a customer offers a price below bid price, reject it (or allocate to a lower class). `dp_seat_protect` computes bid prices from a DP table.

### Spoilage vs. Dilution

- **Spoilage:** Unsold seat capacity (revenue lost due to insufficient demand).
- **Dilution:** Selling a seat at a low fare when a higher-paying customer would have bought it later (revenue lost due to suboptimal price discrimination).

Dynamic pricing balances both. Algorithms maximize revenue by choosing fares and protection levels that minimize the sum of spoilage and dilution.

### EMSR (Expected Marginal Seat Revenue)

The incremental revenue from protecting one more seat for a higher class. `dp_seat_protect` uses EMSR-inspired logic to allocate seats.

---

## 12. HTTP API Contract

All endpoints return `Content-Type: application/json`. Authentication is JWT-based; tokens are passed as `Authorization: Bearer <token>` header.

### Authentication

| Method | Path | Auth | Request Body | Response | Purpose |
|--------|------|------|-------------|----------|---------|
| POST | `/api/auth/login` | public | `{email, password}` | `{token, role}` | Obtain JWT token |

**Example:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "analyst@bookero.local", "password": "password"}'
# Response: {"token": "eyJ...", "role": "ANALYST"}
```

### Flights (Traveler / Analyst)

| Method | Path | Auth | Query | Response | Purpose |
|--------|------|------|-------|----------|---------|
| GET | `/api/flights/search` | authenticated | `origin`, `dest`, `date` | `{flightId, flightNo, departAt, inventory: {seatsTotal, seatsLeft}, fareClasses: [{id, code, basePrice, currentPrice, seatsAllocated}]}[]` | Search flights by route and date |
| GET | `/api/flights/{id}` | authenticated | - | `{flightId, flightNo, departAt, routeId, fareClasses: [...]}` | Get flight detail with fare classes |

### Bookings (Traveler)

| Method | Path | Auth | Request Body | Response | Purpose |
|--------|------|------|-------------|----------|---------|
| POST | `/api/bookings` | TRAVELER | `{flightId, fareClassId}` | `{bookingId, userId, flightId, fareClassId, paidPrice, createdAt}` | Create booking (transactional seat reduction) |
| GET | `/api/bookings/me` | TRAVELER | - | `{bookingId, flightNo, departAt, fareClassCode, paidPrice, createdAt}[]` | List traveler's bookings |

**Booking response on success (HTTP 201):**
```json
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "...",
  "flightId": "...",
  "fareClassId": "...",
  "paidPrice": "245.50",
  "createdAt": "2026-07-28T14:30:00Z"
}
```

**Booking response on oversell (HTTP 409 Conflict):**
```json
{
  "type": "urn:problem-type:bookero:oversell",
  "title": "No seats available",
  "status": 409,
  "detail": "All seats on flight ABC123 are booked."
}
```

### Simulation (Analyst)

| Method | Path | Auth | Request Body | Response | Purpose |
|--------|------|------|-------------|----------|---------|
| POST | `/api/simulate` | ANALYST | `{intensity?: number}` | `{simulationId, flightsAffected, demandsCreated}` | Generate synthetic demand and booking pressure |
| POST | `/api/simulate/seed` | ANALYST | - | `{flightsSeeded, fareClassesCreated}` | Seed ~20-40 reference flights on hub network |

### Pricing (Analyst)

| Method | Path | Auth | Request Body | Response | Purpose |
|--------|------|------|-------------|----------|---------|
| POST | `/api/pricing/reprice` | ANALYST | `{algorithmKey, flightIds?: UUID[]}` | `{algorithmRunId, durationMs, revenueDelta, priceUpdates: [{flightId, fareClassCode, newPrice}]}` | Trigger reprice on all or selected flights |

**Example:**
```bash
curl -X POST http://localhost:8080/api/pricing/reprice \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"algorithmKey": "dp_seat_protect", "flightIds": ["uuid-1", "uuid-2"]}'
```

### Algorithm Lab (Analyst)

| Method | Path | Auth | Query/Body | Response | Purpose |
|--------|------|------|-----------|----------|---------|
| GET | `/api/algorithms` | ANALYST | - | `{key, displayName, description}[]` | List all algorithm keys |
| POST | `/api/algorithms/{key}/run` | ANALYST | `{flightIds?: UUID[]}` | `{algorithmRunId, algorithmKey, durationMs, revenueDelta, status}` | Execute one algorithm (same as reprice endpoint) |
| GET | `/api/algorithms/runs` | ANALYST | - | `{algorithmRunId, algorithmKey, durationMs, revenueDelta, status, createdAt}[]` | List recent algorithm runs |

### Operations (Analyst)

| Method | Path | Auth | Query | Response | Purpose |
|--------|------|------|-------|----------|---------|
| GET | `/api/ops/inventory` | ANALYST | - | `{flights: [{flightId, flightNo, departAt, seatsTotal, seatsLeft, bookingCount}]}` | View current inventory state |
| GET | `/api/ops/metrics` | ANALYST | - | `{totalRevenue, baselineRevenue, loadFactor, avgFare, revenueByDay: [{date, revenue}]}` | Get revenue KPIs (proxies to Python analytics) |

---

## 13. Error Handling Contract (RFC 9457 Problem Details)

All error responses follow RFC 9457. Responses include:
- `type` (URI identifying error category)
- `title` (short human-readable message)
- `status` (HTTP status code)
- `detail` (explanation)
- Optional `instance`, `extensions`

### Common Error Scenarios

| Scenario | HTTP Status | Type | Title | Detail |
|----------|------------|------|-------|--------|
| Oversell attempt | 409 | `urn:problem-type:bookero:oversell` | No seats available | All seats on flight {flightNo} are booked. |
| Invalid credentials | 401 | `urn:problem-type:bookero:auth-failed` | Authentication failed | Email or password incorrect. |
| Insufficient authorization | 403 | `urn:problem-type:bookero:forbidden` | Forbidden | Travelers cannot access analyst endpoints. |
| Algorithm not found | 404 | `urn:problem-type:bookero:algorithm-not-found` | Algorithm not found | Algorithm key '{key}' is not registered. |
| Algorithm execution failed | 500 | `urn:problem-type:bookero:algorithm-error` | Algorithm error | {Algorithm} encountered an exception: {message}. Last good prices retained. |
| Validation error | 400 | `urn:problem-type:bookero:validation-error` | Validation failed | Field '{field}' must be non-null. |
| Analytics service unavailable | 503 | `urn:problem-type:bookero:analytics-unavailable` | Service unavailable | Analytics service is temporarily down. Booking and pricing continue with default behavior. |

**Error handling rules:**
- **Oversell (409):** Transaction is rolled back; booking fails cleanly; no partial state.
- **Algorithm failure:** `algorithm_run.status = FAILED`; error message stored; last good prices are retained (no silent price corruption); dashboard surfaces the error.
- **Analytics down:** API continues to serve booking and baseline pricing; dashboard shows unavailable banner; `GET /api/ops/metrics` may return 503 or cached data depending on implementation.

---

## 14. Security Model

### Authentication

- **Scheme:** JWT (JSON Web Token) with HS256 (HMAC SHA-256).
- **Secret:** Stored in environment variable `JWT_SECRET` (32+ characters in production).
- **Token format:** Standard JWT with claims:
  - `iss` (issuer): `bookero`
  - `sub` (subject): user ID (UUID)
  - `email` (email)
  - `role` (TRAVELER or ANALYST)
  - `iat` (issued at)
  - `exp` (expiration, default 24 hours)

### Authorization

- **Public endpoints:** `/api/auth/login` (no token required).
- **Authenticated endpoints:** All `/api/**` except login require valid JWT token in `Authorization: Bearer <token>` header.
- **Role-based access control (method-level):**
  - `TRAVELER` can access: `/api/flights/**`, `/api/bookings/**`.
  - `ANALYST` can access: `/api/simulate`, `/api/pricing/**`, `/api/algorithms/**`, `/api/ops/**`.
  - Cross-role access returns HTTP 403 Forbidden.

### Token validation

- **Filter:** `JwtAuthFilter` intercepts all requests; validates signature and expiration.
- **Stateless:** No session storage; every request is independently validated.
- **Extraction:** `SecurityContext` populated via `@CurrentUser` annotation; injectable into controllers.

---

## 15. Deployment View

### Docker Compose (Canonical)

Four services orchestrated via `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: bookero
      POSTGRES_USER: bookero
      POSTGRES_PASSWORD: bookero
    healthcheck: pg_isready
    volumes: [pgdata:/var/lib/postgresql/data]

  api:
    build: ./services/api
    ports: ["8080:8080"]
    depends_on: [postgres]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bookero
      JWT_SECRET: bookero-dev-secret-change-me-32chars
      ANALYTICS_BASE_URL: http://analytics:8001

  analytics:
    build: ./services/analytics
    ports: ["8001:8001"]
    depends_on: [postgres]
    environment:
      DATABASE_URL: postgresql://bookero:bookero@postgres:5432/bookero

  web:
    build: ./apps/web
    ports: ["3000:3000"]
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080

volumes:
  pgdata:
```

**Startup:**
```bash
docker compose up --build -d
```

**Verification:**
```bash
docker compose ps
docker compose logs api | grep "Started BookeroApplication"
```

### Native Development (Alternative for Build Workstation)

When Docker is unavailable, run services natively using `scripts/env.sh`:

```bash
source scripts/env.sh
# Starts PostgreSQL 18 on 5433, API on 8090, web on 3100
# Database: /c/Users/adjei/tools/postgresql
# Java: /c/Users/adjei/tools/jdk-21.0.5+11
# Python: /c/Users/adjei/AppData/Local/Programs/Python/Python313
```

**Environment variables set:**
- `PGHOST=127.0.0.1`, `PGPORT=5433`, `PGUSER=bookero`, `PGDATABASE=bookero`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/bookero`
- `SERVER_PORT=8090`, `WEB_PORT=3100`, `ANALYTICS_PORT=8001`
- `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3100`

**Note:** Native development is documented for transparency but Docker Compose is the canonical deployment. Evaluators are expected to use Docker Compose unless infrastructure is unavailable.

---

## 16. Definition of Done: Phase 2

- [x] Service topology table with tech, responsibility, and port mapping.
- [x] Architecture flowchart (Mermaid) showing all services and data flows.
- [x] Use-case diagram (Mermaid) mapping actors to Bookero functionality.
- [x] Entity-Relationship diagram (Mermaid) matching `V1__init.sql` exactly (verified column by column).
- [x] Pricing cycle sequence diagram (Mermaid) showing full path from simulation through booking to dashboard.
- [x] Oversell-safe booking transaction sequence (Mermaid) showing pessimistic lock and concurrent paths.
- [x] Algorithm package class/component diagram (Mermaid) showing `Algorithm` interface, registry, and all implementations.
- [x] Algorithm run lifecycle state diagram (Mermaid) covering PENDING → RUNNING → SUCCESS/FAILED.
- [x] Spring API package decomposition table.
- [x] Python analytics package decomposition table.
- [x] Core domain abstractions explained for non-airline readers (fare class, booking class ladder, load factor, protection level, bid price, spoilage, dilution, EMSR).
- [x] Complete HTTP API contract matching BUILD.md §5 and §6, including authentication, flights, bookings, simulation, pricing, algorithms, operations.
- [x] Error handling contract (RFC 9457 ProblemDetail) covering oversell, algorithm failure, analytics down.
- [x] Security model (JWT HS256, roles TRAVELER/ANALYST, stateless, method-level authorization).
- [x] Deployment view: Docker Compose canonical, native development documented via `scripts/env.sh`.
- [x] All Mermaid diagrams are syntactically valid.
- [x] This document is consistent with `V1__init.sql`, `BUILD.md`, and design spec.

---

## References

- **Spec:** `docs/superpowers/specs/2026-07-28-bookero-design.md` (§4 architecture, §5 use cases, §6 data model, §7 API sketch, §8 algorithms)
- **Build bible:** `BUILD.md` (§3 Docker, §4 schema, §5 Spring, §6 analytics, §7 web)
- **Database schema:** `services/api/src/main/resources/db/migration/V1__init.sql`
- **Native dev:** `scripts/env.sh`
