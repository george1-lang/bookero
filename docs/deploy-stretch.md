# Deployment Guide (Free Tiers Only)

**Scope:** how to run Bookero locally, and how to put it on the public internet without
paying anything. Every option below is a genuine free tier, not a trial that expires
into a bill. Card-on-file requirements and idle-sleep behaviour are called out
explicitly, because those are the things that break a demo.

---

## 1. Local run

### 1a. Docker Compose (documented default)

```bash
docker compose up --build
```

| Service | Image or build | Port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | System of record |
| `api` | `services/api` | 8080 | Domain, pricing, algorithms |
| `analytics` | `services/analytics` | 8001 | ETL, demand model, revenue metrics |
| `web` | `apps/web` | 3000 | Traveller and analyst UI |

Then prepare the demo world:

```bash
API=http://localhost:8080 ANALYTICS=http://localhost:8001 ./scripts/seed.sh
```

Open <http://localhost:3000> and sign in as `analyst@bookero.local` / `password`.

### 1b. Native run (what was used to build and verify this)

No Docker required. PostgreSQL, Java 21, Node 22 and Python 3.13 must be present;
`scripts/env.sh` pins the paths used here.

```bash
source scripts/env.sh
./scripts/stack.sh up        # postgres 5433, analytics 8001, api 8090, web 3100
./scripts/seed.sh
./scripts/stack.sh status
./scripts/stack.sh down
```

Ports differ from Compose because 8080 and 3000 were already taken on the build machine
by pgAdmin and another service.

---

## 2. Honest status

| Claim | Evidence |
|---|---|
| The native stack runs end to end | 39 Spring tests, 12 analytics tests and 46 Playwright end-to-end tests, all passing against the running stack |
| The Compose files are complete and valid | Four services, healthchecks, dependency ordering, build args for the web image; reviewed by hand |
| Compose has been executed | **No.** Docker was not installed on the build workstation |

Nothing in the application is Compose-specific, so the residual risk is image build
issues rather than application behaviour. If a Compose run is a grading requirement,
execute it once on a machine with Docker and keep the output.

---

## 3. Free hosting options

Bookero is four moving parts: a Postgres database, a JVM service, a Python service and a
Next.js front end. Almost no provider gives all four away on one platform, so the
realistic free strategy is to split across providers and accept cold starts.

### 3a. Comparison

| Platform | Free allowance | Card required | Sleeps when idle | Best used for |
|---|---|---|---|---|
| **Neon** | Postgres, 0.5 GB storage | No | Scales to zero, wakes in about a second | The database |
| **Supabase** | Postgres, 500 MB, 2 projects | No | Pauses after about 7 days idle | Alternative database |
| **Render** | Web services, 750 instance-hours per month | No | Yes, after 15 minutes; cold start 30 to 60 s | API and analytics |
| **Vercel** | Hobby plan, generous for Next.js | No | No | The web front end |
| **Fly.io** | Small shared-CPU machines within an allowance | Yes | Can scale to zero | Faster wake than Render, if you accept a card |
| **Koyeb** | One free service | Yes | No | A single always-on service |
| **Railway** | Trial credit, then paid | Yes | n/a | **Not free.** Excluded |

### 3b. Recommended free stack

**Neon for the database, Render for the two back-end services, Vercel for the web app.**
No card is needed anywhere, and each piece sits on a provider that is good at that
particular workload.

```
Vercel (web)  ->  Render (api)  ->  Neon (Postgres)
                       \-------->  Render (analytics)  ->  Neon
```

The one real cost is Render's idle sleep. After 15 minutes without traffic the API and
analytics containers spin down, and the next request takes roughly 30 to 60 seconds
while the JVM restarts. Mitigations, in order of preference:

1. **Warm it up before the demo.** Open the site 5 minutes early and sign in. For a
   scheduled evaluation slot this is the honest, zero-effort answer.
2. **Ping it on a schedule.** A free cron service hitting `/actuator/health` every 10
   minutes keeps it awake during the evaluation window. Switch it off afterwards so the
   750 monthly instance-hours are not consumed.
3. **Demo locally and treat the deployment as evidence it ships.** Perfectly reasonable
   for a graded demo, and it removes network risk on the day.

### 3c. Limits you will actually hit

| Limit | Value | Impact on Bookero |
|---|---|---|
| Neon storage | 0.5 GB | The OpenFlights load is 6,072 airports and 37,042 routes plus roughly 8,000 bookings. Comfortably inside. |
| Render memory | 512 MB per service | The JVM must be told about the container, or it is OOM-killed. See 4.2. |
| Render build minutes | Limited per month | The Maven build is the slow one. Avoid repeated pushes that trigger rebuilds. |
| Render instance-hours | 750 per month | Two services that mostly sleep will fit. Two services awake 24/7 will not. |
| Vercel bandwidth | 100 GB per month | Irrelevant at demo scale |

---

## 4. Step by step on the recommended stack

### 4.1 Database on Neon

1. Create a project at neon.tech. No card required. Pick a region near you.
2. On the project dashboard, open the **Connect** panel (labelled "Connection Details"
   on some plans). Choose **Connection string**, tick **Show password**, and copy it.
   To see it again later, or if you lose it, go to **Roles** and use **Reset password**.
3. Nothing else to do here. Flyway creates the schema on the API's first boot.

#### Reading the connection string

Neon hands you a single URI. It follows a fixed shape:

```
postgresql://neondb_owner:npg_A1b2C3d4E5f6@ep-cool-frost-12345678.eu-central-1.aws.neon.tech/neondb?sslmode=require
             └──── user ───┘ └──── password ────┘ └──────────────── host ─────────────────────┘ └ db ┘ └── options ──┘
```

- **user** is between `//` and the first `:`
- **password** is between that `:` and the `@`
- everything after the `@` is host, database and options

Two services consume this differently, which is the part that catches people out:

| Service | Variable | What to paste |
|---|---|---|
| `analytics` (Python) | `DATABASE_URL` | The **whole URI**, exactly as Neon gives it |
| `api` (Spring) | `SPRING_DATASOURCE_URL` | JDBC form with the credentials **removed** |
| `api` (Spring) | `SPRING_DATASOURCE_USERNAME` | Just the user |
| `api` (Spring) | `SPRING_DATASOURCE_PASSWORD` | Just the password |

JDBC does not accept credentials inline the way the `postgresql://` URI does, so for the
Spring service you prefix the host part with `jdbc:postgresql://` and pass the user and
password as their own variables. Using the example above:

```
SPRING_DATASOURCE_URL       jdbc:postgresql://ep-cool-frost-12345678.eu-central-1.aws.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME  neondb_owner
SPRING_DATASOURCE_PASSWORD  npg_A1b2C3d4E5f6

DATABASE_URL                postgresql://neondb_owner:npg_A1b2C3d4E5f6@ep-cool-frost-12345678.eu-central-1.aws.neon.tech/neondb?sslmode=require
```

Keep `?sslmode=require`. Neon refuses unencrypted connections, and dropping it produces
a connection error that does not obviously point at TLS.

### 4.2 API on Render

New Web Service, connect the repository, root directory `services/api`, environment
Docker so it uses `services/api/Dockerfile`. Health check path `/actuator/health`.

| Key | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx.region.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | from Neon |
| `SPRING_DATASOURCE_PASSWORD` | from Neon |
| `JWT_SECRET` | a fresh random string of 32 characters or more, **not** the repository default |
| `ANALYTICS_BASE_URL` | `https://bookero-analytics.onrender.com` |
| `CORS_ALLOWED_ORIGINS` | `https://bookero.vercel.app` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70 -Xss512k -XX:TieredStopAtLevel=1` |

The `JAVA_TOOL_OPTIONS` line matters. Without it the JVM sizes its heap for the host
rather than the 512 MB container and gets killed.

### 4.3 Analytics on Render

The same again with root directory `services/analytics`, plus:

| Key | Value |
|---|---|
| `DATABASE_URL` | `postgresql://user:pass@ep-xxx.region.aws.neon.tech/neondb?sslmode=require` |
| `DATA_DIR` | `/tmp/data` |
| `CORS_ALLOWED_ORIGINS` | `https://bookero.vercel.app` |

`DATA_DIR` must be writable. Render's filesystem is ephemeral, so the cached OpenFlights
download and the trained model are lost on restart. Both recover on demand: the ETL
re-downloads, and `/demand/forecast` falls back to its documented heuristic until
`/demand/train` is called again.

### 4.4 Web on Vercel

Import the repository, root directory `apps/web`.

| Key | Value |
|---|---|
| `NEXT_PUBLIC_API_URL` | `https://bookero-api.onrender.com` |
| `NEXT_PUBLIC_ANALYTICS_URL` | `https://bookero-analytics.onrender.com` |

These are read at build time, so changing them requires a redeploy, not a restart. After
the first deploy, go back to Render and set `CORS_ALLOWED_ORIGINS` to the Vercel URL that
was actually issued.

### 4.5 Seed the deployed instance

```bash
API=https://bookero-api.onrender.com \
ANALYTICS=https://bookero-analytics.onrender.com \
./scripts/seed.sh
```

Allow for the cold start on the first call. Run it twice if the first attempt times out;
every step is idempotent.

---

## 5. Before exposing this publicly

This is a course project with deliberately open demo credentials. Everything below must
change before the URL is shared beyond an evaluator.

- [ ] **`JWT_SECRET`.** The repository default `bookero-dev-secret-change-me-32chars` is
      public. Anyone holding it can mint an ANALYST token for your deployment.
- [ ] **Demo account passwords.** `analyst@bookero.local` and `traveler@bookero.local`
      both use `password`, and the bcrypt hashes are committed in `V2__seed_users.sql`.
      Rotate them or delete the rows.
- [ ] **`CORS_ALLOWED_ORIGINS`.** Pin it to the deployed web origin only.
- [ ] **`POST /api/simulate/reset`.** It deletes every booking, flight, fare and audit
      row. It is analyst-only, but an analyst token is trivial to obtain while the demo
      password stands. Disable it outside a demo.
- [ ] **Database credentials.** Use the platform's secret store, never a committed file.
- [ ] **TLS.** Provided automatically by Render, Vercel and Neon. Do not disable it.
- [ ] **Backups.** Neon's free tier keeps a short restore window. Take a manual dump
      before the evaluation if the data matters.

---

## 6. Environment variable reference

### API (`services/api`)

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/bookero` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `bookero` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `bookero` | Database password |
| `JWT_SECRET` | `bookero-dev-secret-change-me-32chars` | HS256 signing key, 32 characters or more |
| `JWT_TTL_MINUTES` | `720` | Token lifetime in minutes |
| `ANALYTICS_BASE_URL` | `http://localhost:8001` | Analytics service base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed browser origins |
| `REPRICE_AFTER_BOOKING` | `true` | Run a light reprice after each booking |
| `SERVER_PORT` | `8080` | Listen port |

### Analytics (`services/analytics`)

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | `postgresql://bookero:bookero@localhost:5432/bookero` | SQLAlchemy connection URL |
| `DATA_DIR` | `./data` | Writable directory for raw downloads and the trained model |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins allowed to call analytics directly |
| `OPENFLIGHTS_AIRPORTS_URL` | GitHub raw airports.dat | Override to pin or mirror the reference data |
| `OPENFLIGHTS_ROUTES_URL` | GitHub raw routes.dat | Override to pin or mirror the reference data |

### Web (`apps/web`)

| Variable | Default | Meaning |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8090` | API base URL, baked in at build time |
| `NEXT_PUBLIC_ANALYTICS_URL` | `http://localhost:8001` | Analytics base URL, baked in at build time |

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| First request after a quiet period takes 30 to 60 s | Render free tier cold start | Warm it up before the demo, or ping `/actuator/health` on a schedule |
| API restarts in a loop on Render | JVM sized for the host, not the 512 MB container | Set `JAVA_TOOL_OPTIONS` as in 4.2 |
| Browser console shows a CORS error | `CORS_ALLOWED_ORIGINS` does not match the deployed origin exactly | Use the full origin including scheme, no trailing slash, then restart the API |
| `POST /api/simulate/seed` returns 400 "No routes in the database" | The ETL has not run | `curl -X POST $ANALYTICS/etl/run`, or just run `./scripts/seed.sh` |
| Dashboard shows "Analytics offline" | The analytics container is asleep or down | Expected degradation. Booking and pricing keep working. Hit `/health` to wake it |
| Fares look unchanged after repricing | Repricing is idempotent, so an identical second run moves nothing | Run `baseline` first to restore list fares, then the strategy |

---

## 8. Cost

Zero on the recommended stack, provided you stay inside the allowances in section 3c and
do not keep both Render services awake around the clock. Neon and Vercel do not ask for a
card, and neither does Render for its free web services. Fly.io and Koyeb do ask for a
card even on their free allowances, which is why they are not the primary recommendation.
