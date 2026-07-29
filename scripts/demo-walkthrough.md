# Evaluator Demo Walkthrough - Bookero

**Duration:** 5-7 minutes  
**Audience:** Project evaluators, faculty  
**Prerequisites:** Docker Compose running or native stack running (see pre-flight checklist below)

---

## Pre-Flight Checklist (10 minutes before demo)

- [ ] Start the stack: `docker compose up --build -d` (or `source scripts/env.sh` for native).
- [ ] Verify services are healthy: `docker compose ps` (all should say "running") or `curl http://localhost:8090/api/auth/login -X POST -d '{"email":"analyst@bookero.local","password":"password"}'` for native.
- [ ] Open a web browser and navigate to `http://localhost:3100` (native) or `http://localhost:3000` (Docker Compose).
- [ ] Verify the login page loads (no 404 or connection errors).
- [ ] Pre-authenticate: Open another browser tab and do a quick login as analyst to populate JWT cache (optional but speeds up demo start).
- [ ] Run a reset (see recovery action below) to ensure the world is clean:

```bash
# Get analyst token
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"analyst@bookero.local","password":"password"}' \
  -s | jq -r '.token' > /tmp/token.txt

# Reset
curl -X POST http://localhost:8090/api/simulate/reset \
  -H "Authorization: Bearer $(cat /tmp/token.txt)" \
  -H "Content-Type: application/json"
```

---

## Demo Script

### 1. **Sign In as Analyst** (0:00 - 0:30, 30 seconds)

**What to show:** Authentication and role-based access.

**Actions:**

1. In browser, click "Sign In" (or navigate to login page if not already there).
2. Enter credentials:
   - Email: `analyst@bookero.local`
   - Password: `password`
3. Click "Sign In" or submit form.
4. Verify redirect to `/ops` (operations dashboard) or `/` (home) with analyst-specific menu (should show "Lab", "Simulate", "Dashboard" options; should NOT show "Book" option).

**Narration:**

"Bookero has two user roles: travelers and analysts. I'm signing in as an analyst, which gives me access to the pricing simulator and algorithm comparison lab. Travelers would only see search and booking."

---

### 2. **Show Seeded Network and Initial Inventory** (0:30 - 1:15, 45 seconds)

**What to show:** The network is pre-seeded with flights and capacity.

**Actions:**

1. On the `/ops` dashboard (or inventory view), verify you see a list of flights:
   - Flight numbers (e.g., BK138, BK139, ...)
   - Departure times
   - Total seats (column labeled "Total Seats" or "Capacity")
   - Seats left (should be high; close to total, since no bookings have occurred)
   - Load factor (should be 0% or near 0%)
2. Scroll through a few flights to show variety.
3. **Note the load factors:** All should be 0% or very low (< 5%).

**Narration:**

"The system starts with 60 flights seeded on the ACC (Accra) hub-and-spoke network. Each flight has 120 to 180 seats across four fare classes: Y (economy), B (business), M (midcab), and J (first). Notice all load factors are at 0%; the network is empty."

---

### 3. **Run Demand Simulation** (1:15 - 2:30, 75 seconds)

**What to show:** Synthetic demand injection and inventory update.

**Actions:**

1. On the operations dashboard, find and click "Run Simulation" or "Simulate Demand" button (or similar).
2. Enter intensity: `5` (or use default if provided).
3. Click "Start Simulation" or submit.
4. **Wait for result** (should complete in < 5 seconds). Result message should say something like:
   - "Simulation complete: 4110 bookings created, 60 demand snapshots recorded."
5. **Refresh the inventory view** (press F5 or click "Refresh" or "Reload").
6. Verify inventory updates:
   - Seats left should decrease (e.g., from 180 to 90).
   - Load factor should increase (e.g., from 0% to 50%).
   - Scroll through to show several flights with varying load factors.

**Narration:**

"Now I'm running a demand simulation. This generates synthetic bookings across all flights using a learned demand model. Notice the load factors jumped from 0% to 50% on average; we've captured 4110 bookings across the 60 flights. The fares are still at their base prices."

---

### 4. **Open Algorithm Lab and Compare Algorithms** (2:30 - 4:30, 120 seconds)

**What to show:** Algorithm execution and performance comparison.

**Actions:**

1. Navigate to `/ops/lab` (or find "Algorithm Lab" link in the menu).
2. Verify the lab loads and shows a list of algorithms:
   - baseline, route_graph, shortest_path, flight_search, slot_schedule, greedy_protection, dp_seat_protect, revenue_optimize, time_pressure_heuristic, demand_ml
3. **Run the baseline algorithm** (click "Run" next to baseline):
   - Verify it completes quickly (< 1 second).
   - Note displayed metrics:
     - Duration: should be ~2 ms
     - Fares moved: should be 0
     - Revenue delta: should be 0 (control group)
4. **Run dp_seat_protect** (click "Run" next to dp_seat_protect):
   - Wait for completion (< 1 second).
   - Note metrics:
     - Duration: should be ~8 ms
     - Fares moved: should be 240 (all fares repriced)
     - Revenue delta: positive or negative (this is the key metric)
5. **Run greedy_protection** (click "Run" next to greedy_protection):
   - Wait for completion (< 1 second).
   - Note metrics:
     - Duration: should be ~5 ms
     - Fares moved: should be low (< 10, since thresholds may not trigger)
6. **Compare side-by-side** (if UI supports it):
   - Show that dp_seat_protect reprices more aggressively than greedy_protection.
   - Point out latency difference: all are fast (single-digit ms), but route_graph is slower (100+ ms for offline analysis).

**Narration:**

"The Algorithm Lab lets us run each algorithm on the current inventory and demand, and see the results immediately. Baseline is our control: fares reset to list price, no revenue change. dp_seat_protect uses dynamic programming to allocate seats across fare classes, repricing all 240 fares. greedy_protection uses a simpler rule: close cheap classes when load exceeds a threshold. Notice the latencies are all low (single-digit milliseconds), so algorithms are suitable for synchronous pricing decisions."

---

### 5. **Switch to Traveler Role and Book** (4:30 - 5:45, 75 seconds)

**What to show:** The traveler experience sees dynamic prices.

**Actions:**

1. Open a new browser tab (or log out and log back in).
2. Log in as traveler:
   - Email: `traveler@bookero.local`
   - Password: `password`
3. Verify you land on a search page (not `/ops`).
4. Search for a flight:
   - Origin: `ACC`
   - Destination: `JFK`
   - Date: tomorrow (or any available date)
5. Click "Search".
6. Verify results show flights with **dynamic fares** (fares should not all be identical, since we just repriced).
7. Click on a flight (e.g., ACC-JFK departure at 10 AM).
8. **Note the Y (Economy) fare displayed** (e.g., "$439" or repriced amount).
9. Click "Book" for the Y class.
10. Verify booking confirmation appears within 2 seconds (should show booking ID, paid price, confirmation).

**Narration:**

"Now I'm switching to the traveler role. From the traveler's perspective, they search for flights on ACC-JFK and see dynamic prices set by the analyst's repricing algorithms. Notice the Y class shows a specific price; this is what the pricing algorithm determined would optimize revenue. The booking completes transactionally: if this were the last seat, other concurrent bookings would fail with HTTP 409 (oversold protection)."

---

### 6. **Return to Analyst, Show Dashboard Revenue Update** (5:45 - 6:45, 60 seconds)

**What to show:** Revenue and load factor metrics reflect the booking and pricing.

**Actions:**

1. Go back to the analyst tab or log in again as analyst.
2. Navigate to the Revenue Dashboard (e.g., `/ops/dashboard` or a "Dashboard" link).
3. Verify dashboard displays:
   - **Total Revenue:** (e.g., "$2.5M" or similar, depending on simulation intensity)
   - **Load Factor:** (e.g., "65.1%", reflecting 4110+ 1 bookings from the demo)
   - **Average Fare:** (e.g., "$439" or less if repriced downward)
   - **Revenue by Day / Route:** Show a table or chart breaking down revenue.
4. **Highlight one route** (e.g., ACC-JFK) and note:
   - Bookings on that route: e.g., 296
   - Revenue on that route: e.g., "$395,026"
   - This demonstrates the end-to-end flow: simulate -> reprice -> book -> metrics update.

**Narration:**

"Finally, back on the analyst dashboard: we can see the realized revenue across all flights. The simulation generated 4110 bookings, and we just added 1 more via the traveler booking. Total load factor is 65.1%, meaning the cabin is moderately full. The average fare is $439 (either baseline or after repricing, depending on which algorithm was last run). The dashboard breaks down revenue by route so analysts can identify high-performing and underperforming legs."

---

### 7. **Point to Documentation** (6:45 - 7:00, 15 seconds)

**What to show:** Completeness of design and implementation documentation.

**Actions:**

1. If in browser, navigate to `http://localhost:3100/docs` or similar (if docs are served statically).
2. Or open file browser and point to `docs/` directory:
   - `docs/01-problem-framing.md` (problem statement, worked example, success criteria)
   - `docs/02-system-design.md` (architecture, ER diagram, sequence diagrams, API contract)
   - `docs/05-evaluation.md` (benchmark results, experiment results, interpretation)
   - `docs/algorithms/dp_seat_protect.md` (pseudocode, complexity, performance numbers)
   - (and 9 others)
   - `docs/user-testing.md` (usability protocol, SUS, accessibility)
3. Briefly show one algorithm doc (e.g., dp_seat_protect):
   - Purpose
   - Pseudocode
   - Mermaid flowchart
   - Performance results (latency table)

**Narration:**

"All design, implementation, and evaluation work is documented in the docs/ folder. Each algorithm has its own file with pseudocode, complexity analysis, and measured performance numbers. The evaluation doc contains the full experimental methodology, threats to validity, and interpretation of results. User testing protocol is also documented so future teams can replicate the evaluation."

---

## Recovery Actions

If at any step the demo goes wrong:

### Booking fails (HTTP 409 or 500)

```bash
# Reset the world and re-seed
curl -X POST http://localhost:8090/api/simulate/reset \
  -H "Authorization: Bearer $(cat /tmp/token.txt)" \
  -H "Content-Type: application/json"
```

Then re-run Task 3 (simulation) and Task 4 (algorithm run) before resuming Task 5.

### Algorithm Lab does not load or times out

```bash
# Restart the API service
docker compose restart api
# (or manually restart the Java process if native)

# Wait 10 seconds for startup, then try again.
```

### Dashboard shows stale metrics or is blank

```bash
# Restart analytics service
docker compose restart analytics
# (or manually restart the Python process if native)

# Wait 5 seconds, then refresh the browser (F5).
```

### Entire stack is unresponsive

```bash
docker compose down
docker compose up --build -d
# (or kill and restart services if native)

# Wait 30 seconds for services to become healthy.
```

---

## Timing Notes

- **0:00 - 0:30:** Sign-in (30 sec)
- **0:30 - 1:15:** Show inventory (45 sec)
- **1:15 - 2:30:** Simulate (75 sec)
- **2:30 - 4:30:** Algorithm Lab (120 sec, the longest segment)
- **4:30 - 5:45:** Book as traveler (75 sec)
- **5:45 - 6:45:** Dashboard (60 sec)
- **6:45 - 7:00:** Documentation (15 sec)

**Total: 420 seconds (7 minutes)** with some buffer for questions and unexpected delays.

---

## Contingency: If Running Short on Time

If you have only 5 minutes, skip Task 6 (dashboard); jump directly from traveler booking (Task 5) to documentation (Task 7). Narrative:

"The dashboard (not shown due to time) aggregates revenue and load factors across all flights. Documentation for design, algorithms, and evaluation is in the docs/ folder."

---

## Contingency: If Running Long on Time

If there is extra time and evaluators want to dive deeper:

1. **Show the API directly:** Use curl to call `GET /api/flights/search?origin=ACC&dest=JFK` and show the JSON response with dynamic fares.
2. **Show the database:** Connect to PostgreSQL and run a query to show bookings, price_history, and algorithm_runs.
3. **Run a second algorithm:** Pick a different one (e.g., revenue_optimize) and compare side-by-side in the Lab.
4. **Export metrics:** Show how to export a CSV or query results from the analytics service.

---

## Credentials Summary

| Role | Email | Password |
|------|-------|----------|
| Analyst | analyst@bookero.local | password |
| Traveler | traveler@bookero.local | password |

---

## URLs

| Service | Docker | Native |
|---------|--------|--------|
| Web | http://localhost:3000 | http://localhost:3100 |
| API | http://localhost:8080 | http://localhost:8090 |
| Analytics | http://localhost:8001 | http://localhost:8001 |
| PostgreSQL | localhost:5432 | localhost:5433 |

---

## Definition of Done

- [x] Pre-flight checklist documented (stack start, service health, pre-auth).
- [x] 7 demo tasks scripted with clear actions, narration, and timing.
- [x] All URLs and credentials provided (Docker and native).
- [x] Recovery actions documented (reset world, restart services, troubleshoot).
- [x] Timing budget per step (total 5-7 minutes).
- [x] Contingencies for short/long runs provided.
- [x] Documentation pointers provided (where to find design docs and algorithm files).

---

## References

- **Build instructions:** `BUILD.md` §3 (Docker Compose), §15 (native dev).
- **System design:** `docs/02-system-design.md` (architecture, API contract).
- **Evaluation:** `docs/05-evaluation.md` (benchmark results, experiment interpretation).
- **Algorithms:** `docs/algorithms/*.md` (one file per algorithm key).
