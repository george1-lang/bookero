# Phase 5: User Testing Protocol - Bookero

**Date:** 2026-07-29  
**Scope:** Usability evaluation of Bookero by representative analysts and travelers.

---

## 1. Testing Objectives

- Assess whether travelers can search, view dynamic fares, and book without confusion.
- Assess whether analysts can seed the network, simulate demand, trigger reprices, and interpret the Algorithm Lab.
- Identify usability blockers (errors, unclear labels, slow feedback).
- Gather qualitative feedback on feature prioritization and mental model clarity.
- Confirm accessibility compliance: keyboard navigation, focus visibility, color contrast, motion.

---

## 2. Participant Profile

### Travelers (4-6 participants)

- **Background:** No airline industry experience required. Familiar with online booking (Expedia, Google Flights, or similar).
- **Age:** 25-55 years old.
- **Comfort with tech:** Intermediate (can use a web browser, make a booking, but not necessarily a developer).
- **Motivation:** Compensate with a gift card or small honorarium; travel-related incentive preferred.

### Analysts (2-3 participants)

- **Background:** Operations researcher, data analyst, or revenue management background. Familiar with SQL or Python.
- **Age:** 30-60 years old.
- **Comfort with tech:** Advanced (comfortable with APIs, dashboards, parameter tuning).
- **Motivation:** Professional interest; unpaid participation acceptable.

---

## 3. Environment and Credentials

### Deployment

- **URL:** `http://localhost:3100` (native) or `http://localhost:3000` (Docker Compose).
- **Services:** PostgreSQL, Spring API (8090 or 8080), Analytics (8001), Next.js web (3100 or 3000).
- **Start time:** Test lead starts the stack 10 minutes before first participant session.

### Traveler Credentials

| Account | Email | Password | Role |
|---------|-------|----------|------|
| Sample Traveler | traveler@bookero.local | password | TRAVELER |

### Analyst Credentials

| Account | Email | Password | Role |
|---------|-------|----------|------|
| Sample Analyst | analyst@bookero.local | password | ANALYST |

### Pre-Flight Checklist (10 minutes before test session)

- [ ] Verify stack is running: `docker compose ps` or `ps aux | grep java`.
- [ ] Verify web is accessible: `curl http://localhost:3100` or `http://localhost:3000`.
- [ ] Verify API responds: `curl http://localhost:8090/api/auth/login -X POST -d '{"email":"analyst@bookero.local","password":"password"}'`.
- [ ] Seed flights: Click "Seed Network" or run `POST /api/simulate/seed` via curl.
- [ ] Create 1 booking as traveler: Log in, search ACC-JFK, book a Y seat (to have baseline data for comparison).
- [ ] Run one simulation as analyst: `POST /api/simulate {intensity: 3}`.
- [ ] Run baseline repricing: `POST /api/pricing/reprice {algorithmKey: "baseline"}`.
- [ ] Verify Lab loads: Visit `/ops/lab` and confirm algorithms list.

### Recovery Actions (If Demo Goes Wrong)

If a test step fails (booking fails, price does not update, Lab hangs), run this reset command as analyst:

```bash
curl -X POST http://localhost:8090/api/simulate/reset \
  -H "Authorization: Bearer <analyst_token>" \
  -H "Content-Type: application/json"
```

This resets the world to a clean seeded state (60 flights, 240 fare classes, no bookings).

---

## 4. Scripted Tasks

### Task 1: Traveler Sign-In and Flight Search

**Objective:** Verify traveler can authenticate and discover flights.

**Steps:**

1. Open browser to `http://localhost:3100` (or `3000`).
2. Click "Sign In" or "Login".
3. Enter email: `traveler@bookero.local`.
4. Enter password: `password`.
5. Click "Sign In" or submit form.
6. Verify you are redirected to a search page (or home page with search form).
7. In the search form, select:
   - Origin: `ACC` (Accra hub).
   - Destination: `JFK` (New York).
   - Departure date: tomorrow (or any date with scheduled flights).
8. Click "Search" or "Find Flights".
9. Verify results show at least 1 flight with fare classes.

**Success Criterion:**

- [ ] User successfully authenticates (no 401 error).
- [ ] Search form is visible and navigable.
- [ ] Search returns a result list with at least 1 flight.
- [ ] Flight detail shows fare classes (Y, B, M, J) with visible prices.

**Result:**
(To be filled during testing)

---

### Task 2: Traveler Verifies Fare and Books a Seat

**Objective:** Verify dynamic pricing is visible and booking is transactional.

**Steps:**

1. From Task 1 results, click on any ACC-JFK flight.
2. On the flight detail page, note the "Current Price" shown for the Y (Economy) class. Record it: `____________`.
3. Click "Book" or "Add to Basket" for Y class.
4. On the booking confirmation page, verify the "Paid Price" or "Final Price" matches the price you noted in step 2.
5. Verify the booking shows a confirmation number (e.g., booking ID, trip ID).
6. Click "View Bookings" or navigate to `/bookings`.
7. Verify your booking appears in the list with the same confirmation number, fare class, and price.

**Success Criterion:**

- [ ] Fare shown on flight detail matches fare charged at booking.
- [ ] No surprises (markup, taxes, fees not explained) appear at checkout.
- [ ] Booking confirmation is returned immediately (< 2 seconds).
- [ ] Booking appears in "My Bookings" list within 1 second.

**Result:**
(To be filled during testing)

---

### Task 3: Analyst Seeds the Network and Runs Simulation

**Objective:** Verify analyst can set up the experiment environment.

**Steps:**

1. Open browser to `http://localhost:3100` (or `3000`).
2. Click "Sign In" or "Login".
3. Enter email: `analyst@bookero.local`.
4. Enter password: `password`.
5. Click "Sign In" or submit form.
6. Navigate to `/ops` (Operations Dashboard) or find a "Seed" or "Initialize" button.
7. Click "Seed Network" or equivalent button.
8. Verify a success message appears (e.g., "60 flights seeded on ACC hub").
9. Navigate to the simulation control (e.g., a "Simulate" button on `/ops`).
10. Set intensity to 5 (or accept default if provided).
11. Click "Run Simulation" or "Start Simulation".
12. Verify a progress message or result: `"Generated 60 demand snapshots, 4110 synthetic bookings"` or similar.
13. Navigate to the inventory view (e.g., `/ops` dashboard).
14. Verify inventory shows 60 flights with updated seat counts and load factors.

**Success Criterion:**

- [ ] Analyst successfully authenticates (no 403 Forbidden).
- [ ] Seed operation completes and displays confirmation.
- [ ] Simulation runs and displays booking count.
- [ ] Inventory view updates to show bookings (seats_left decreases, load_factor increases).

**Result:**
(To be filled during testing)

---

### Task 4: Analyst Compares Two Algorithms in the Lab

**Objective:** Verify the Algorithm Lab displays metrics correctly and comparison is intuitive.

**Steps:**

1. From Task 3, you are logged in as analyst.
2. Navigate to `/ops/lab` (Algorithm Lab) or find a "Lab" or "Compare Algorithms" link.
3. Verify a list of algorithms is displayed with keys: baseline, greedy_protection, dp_seat_protect, revenue_optimize, demand_ml, etc.
4. Locate and click "Run" next to `dp_seat_protect`.
5. Verify algorithm runs and results appear (should complete in < 1 second).
6. Note the displayed metrics:
   - Duration (ms): `____________`
   - Revenue delta (%): `____________`
   - Load factor: `____________`
   - Fares moved: `____________`
7. Locate and click "Run" next to `greedy_protection`.
8. Verify algorithm runs; note metrics again:
   - Duration (ms): `____________`
   - Revenue delta (%): `____________`
   - Load factor: `____________`
   - Fares moved: `____________`
9. If a "Compare" view is available, click it to see side-by-side results.
10. Verify you can read latency (should show single-digit or double-digit ms) and revenue delta without squinting or scrolling excessively.

**Success Criterion:**

- [ ] Lab loads without errors.
- [ ] Algorithm list is complete and readable.
- [ ] Running an algorithm completes within 5 seconds and displays results.
- [ ] Metrics are numeric, labeled clearly (e.g., "Duration (ms):", "Revenue Delta (%):").
- [ ] Comparison view (if available) is readable and shows both algorithms' results clearly.

**Result:**
(To be filled during testing)

---

### Task 5: Analyst Reads Revenue Dashboard and Explains Change

**Objective:** Verify the revenue dashboard accurately reflects algorithm changes and is interpretable.

**Steps:**

1. From Task 4, you are in the Algorithm Lab.
2. Navigate to the Revenue Dashboard (e.g., `/ops/dashboard` or a "Dashboard" link).
3. Verify the dashboard displays:
   - Total revenue (e.g., "$2.5M" or similar large number).
   - Load factor (e.g., "65.1%").
   - Average fare (e.g., "$439").
   - A chart or table showing revenue by day or by route.
4. Locate the line or chart labeled "Total Revenue" or similar.
5. In your own words, explain what changed from the baseline. For example:
   - "Revenue increased because more seats were sold."
   - "Average fare decreased, but total revenue went up due to volume."
   - "Load factor stayed the same; no pricing change had an effect."
6. Note your explanation: `____________________________________________________________________`.
7. (Optional) Filter or drill down to a specific route (e.g., ACC-JFK).
8. Verify the drill-down updates the metrics and shows route-specific data.

**Success Criterion:**

- [ ] Dashboard loads and displays numeric metrics (revenue, load factor, avg fare).
- [ ] Metrics are accurate (match `/api/ops/metrics` API response).
- [ ] Participant can articulate the relationship between pricing change and revenue change.
- [ ] Dashboard is readable (font size, contrast, data labels are clear).

**Result:**
(To be filled during testing)

---

## 5. System Usability Scale (SUS) Questionnaire

**Instructions:** For each statement, select your level of agreement:

- **1** = Strongly Disagree
- **2** = Disagree
- **3** = Neutral
- **4** = Agree
- **5** = Strongly Agree

| # | Statement | 1 | 2 | 3 | 4 | 5 |
|---|-----------|---|---|---|---|---|
| 1 | I think that I would like to use this system frequently. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 2 | I found the system unnecessarily complex. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 3 | I thought the system was easy to use. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 4 | I think that I would need the support of a technical person to be able to use this system. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5 | I found the various functions in this system were well integrated. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 6 | I thought there was too much inconsistency in this system. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 7 | I would imagine that most people would learn to use this system very quickly. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 8 | I found the system very cumbersome to use. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 9 | I felt very confident using the system. | [ ] | [ ] | [ ] | [ ] | [ ] |
| 10 | I needed to learn a lot of things before I could get going with this system. | [ ] | [ ] | [ ] | [ ] | [ ] |

**Scoring:** Convert responses to a 0-100 scale (see [SUS Calculation Instructions](https://www.usability.gov/how-to-and-tools/methods/system-usability-scale.html)). Average score: `____________`.

---

## 6. Free-Text Feedback

Please provide open-ended comments:

### What was the most intuitive part of the system?

`____________________________________________________________________`

`____________________________________________________________________`

### What was the most confusing part?

`____________________________________________________________________`

`____________________________________________________________________`

### What would you change first if you could redesign one thing?

`____________________________________________________________________`

`____________________________________________________________________`

### Any other comments or suggestions?

`____________________________________________________________________`

`____________________________________________________________________`

---

## 7. Accessibility Checklist

Test lead should verify the following without participant input:

### Keyboard Navigation

- [ ] All interactive elements (buttons, links, form inputs) are reachable via Tab key.
- [ ] Tab order follows visual/reading order (left-to-right, top-to-bottom).
- [ ] Tab can move forward (Tab key) and backward (Shift+Tab key) without getting stuck.
- [ ] No keyboard trap (user can always escape a focused element).

### Focus Visibility

- [ ] When an element receives keyboard focus, a visible focus indicator appears (outline, highlight, or underline).
- [ ] Focus indicator is distinct from the element's normal appearance.
- [ ] Focus indicator has sufficient contrast (WCAG AA minimum 3:1 ratio).

### Color Contrast

- [ ] Text on background has >= 4.5:1 contrast ratio (WCAG AA for body text).
- [ ] UI controls and icons have >= 3:1 contrast ratio (WCAG AA for non-text content).
- [ ] No information is conveyed by color alone (e.g., red button for error must also have text "Error").

### Reduced Motion

- [ ] Auto-playing animations (e.g., data loading spinners) can be paused or disabled.
- [ ] Motion is not excessive (< 3 animations per screen, duration < 2 seconds).
- [ ] System respects `prefers-reduced-motion` media query (if using CSS animations).

### Form Labeling

- [ ] All form inputs have associated `<label>` elements or aria-label attributes.
- [ ] Form validation errors are announced to screen readers.
- [ ] Required fields are marked with `required` attribute or aria-required.

### Result Summary

| Check | Pass | Fail | Notes |
|-------|------|------|-------|
| Keyboard navigation | [ ] | [ ] | |
| Focus visibility | [ ] | [ ] | |
| Color contrast | [ ] | [ ] | |
| Reduced motion | [ ] | [ ] | |
| Form labeling | [ ] | [ ] | |

---

## 8. Results Summary

### Participant Information

| Role | Participant ID | Age | Tech Comfort | Date | Time |
|------|---|---|---|---|---|
| Traveler | T1 | ___ | High / Medium / Low | ___ | ___ |
| Traveler | T2 | ___ | High / Medium / Low | ___ | ___ |
| Analyst | A1 | ___ | High / Medium / Low | ___ | ___ |
| Analyst | A2 | ___ | High / Medium / Low | ___ | ___ |

### Task Completion Rate

| Task | Travelers | Analysts | Overall |
|------|-----------|----------|---------|
| 1. Sign-in & Search | __% | __% | __% |
| 2. Verify Fare & Book | __% | N/A | __% |
| 3. Seed & Simulate | N/A | __% | __% |
| 4. Algorithm Lab | N/A | __% | __% |
| 5. Dashboard | N/A | __% | __% |

### Critical Usability Issues (to be filled after testing)

| Issue | Severity | Frequency | Recommendation |
|-------|----------|-----------|---|
| Example: Booking confirmation does not appear | Blocker | 2/4 travelers | Display confirmation inline; add 2-second delay before redirect. |
| | | | |
| | | | |

### Average SUS Score

Travelers: `____________` (target >= 70 is "Acceptable"; >= 80 is "Good")  
Analysts: `____________`

### Key Insights

(To be filled after testing: Summarize participant feedback, patterns, mental models, and top recommendations.)

`____________________________________________________________________`

`____________________________________________________________________`

`____________________________________________________________________`

---

## 9. Definition of Done

- [x] Participant profile defined (travelers and analysts).
- [x] Environment checklist provided (10-minute pre-flight, credentials, recovery actions).
- [x] Five scripted tasks documented with numbered steps, success criteria, and result fields.
- [x] SUS questionnaire included (10 statements, 5-point scale).
- [x] Free-text feedback section provided (3 open-ended questions).
- [x] Accessibility checklist included (keyboard, focus, contrast, motion, form labeling).
- [x] Results summary table provided (participant info, task completion, issues, SUS scores, insights).
- [x] All sections explicitly marked as "to be filled in during the session" or "by test lead".

---

## References

- **SUS Calculation:** [System Usability Scale (SUS) - Usability.gov](https://www.usability.gov/how-to-and-tools/methods/system-usability-scale.html)
- **WCAG Accessibility Guidelines:** [Web Content Accessibility Guidelines 2.1](https://www.w3.org/WAI/WCAG21/quickref/)
- **Build environment:** `BUILD.md` §3 (Docker Compose), §15 (deployment view).
