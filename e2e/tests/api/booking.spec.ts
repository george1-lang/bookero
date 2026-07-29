import { test, expect } from "@playwright/test";
import { API, ANALYST, TRAVELER, login, auth, ensureSeeded, bookableFlight, tightestFlight } from "./helpers";

test.describe("search, booking and oversell protection", () => {
  test("seeded network is searchable by origin, destination and date", async ({ request }) => {
    const analyst = await login(request, ANALYST);
    await ensureSeeded(request, analyst);
    const flight = await bookableFlight(request, analyst);

    const traveler = await login(request, TRAVELER);
    const date = String(flight.departAt).slice(0, 10);
    const res = await request.get(
      `${API}/api/flights/search?origin=${flight.origin}&dest=${flight.dest}&date=${date}`,
      { headers: auth(traveler) },
    );
    expect(res.status(), await res.text()).toBe(200);
    const results = await res.json();
    expect(Array.isArray(results)).toBe(true);
    expect(results.length, "search must find the seeded flight").toBeGreaterThan(0);

    const found = results[0];
    expect(found.fareClasses.length, "every flight carries a fare ladder").toBeGreaterThan(0);
    expect(found.seatsLeft).toBeGreaterThanOrEqual(0);
    for (const fc of found.fareClasses) {
      expect(Number(fc.currentPrice)).toBeGreaterThan(0);
      expect(Number(fc.basePrice)).toBeGreaterThan(0);
    }
  });

  test("search with no matching route returns an empty list, not an error", async ({ request }) => {
    const traveler = await login(request, TRAVELER);
    const res = await request.get(
      `${API}/api/flights/search?origin=ZZZ&dest=QQQ&date=2030-01-01`,
      { headers: auth(traveler) },
    );
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual([]);
  });

  test("booking decrements inventory and appears in the traveller's history", async ({ request }) => {
    const analyst = await login(request, ANALYST);
    await ensureSeeded(request, analyst);
    const before = await bookableFlight(request, analyst);

    const traveler = await login(request, TRAVELER);
    const detail = await request.get(`${API}/api/flights/${before.flightId}`, { headers: auth(traveler) });
    expect(detail.status(), await detail.text()).toBe(200);
    const fareClass = (await detail.json()).fareClasses[0];

    const booked = await request.post(`${API}/api/bookings`, {
      headers: auth(traveler),
      data: { flightId: before.flightId, fareClassId: fareClass.id },
    });
    expect([200, 201], `create booking: ${await booked.text()}`).toContain(booked.status());
    const booking = await booked.json();
    expect(Number(booking.paidPrice)).toBeGreaterThan(0);

    const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(analyst) });
    const after = (await inv.json()).find((r: any) => r.flightId === before.flightId);
    expect(after.seatsLeft, "a booking must consume exactly one seat").toBe(before.seatsLeft - 1);

    const mine = await request.get(`${API}/api/bookings/me`, { headers: auth(traveler) });
    expect(mine.status()).toBe(200);
    const ids = (await mine.json()).map((b: any) => b.id);
    expect(ids).toContain(booking.id);
  });

  test("booking a nonexistent flight returns 404", async ({ request }) => {
    const traveler = await login(request, TRAVELER);
    const res = await request.post(`${API}/api/bookings`, {
      headers: auth(traveler),
      data: {
        flightId: "00000000-0000-4000-8000-000000000000",
        fareClassId: "00000000-0000-4000-8000-000000000000",
      },
    });
    expect(res.status()).toBe(404);
  });

  test("concurrent bookings never oversell the last seats", async ({ request }) => {
    const analyst = await login(request, ANALYST);
    await ensureSeeded(request, analyst);
    const flight = await tightestFlight(request, analyst);
    const traveler = await login(request, TRAVELER);

    const detail = await request.get(`${API}/api/flights/${flight.flightId}`, { headers: auth(traveler) });
    const fareClassId = (await detail.json()).fareClasses[0].id;

    const seatsLeft: number = flight.seatsLeft;
    const attempts = seatsLeft + 8; // deliberately overshoot the remaining capacity

    const results = await Promise.all(
      Array.from({ length: attempts }, () =>
        request.post(`${API}/api/bookings`, {
          headers: auth(traveler),
          data: { flightId: flight.flightId, fareClassId },
        }),
      ),
    );

    const ok = results.filter((r) => r.status() === 200 || r.status() === 201).length;
    const conflict = results.filter((r) => r.status() === 409).length;

    expect(ok, "successful bookings cannot exceed the seats that existed").toBe(seatsLeft);
    expect(conflict, "every excess attempt must be rejected with 409").toBe(attempts - seatsLeft);

    const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(analyst) });
    const after = (await inv.json()).find((r: any) => r.flightId === flight.flightId);
    expect(after.seatsLeft, "a sold-out flight sits at exactly zero, never negative").toBe(0);
  });
});
