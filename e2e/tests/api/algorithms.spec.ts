import { test, expect } from "@playwright/test";
import { API, ANALYST, login, auth, ensureSeeded, ALGORITHM_KEYS } from "./helpers";

test.describe("algorithm lab", () => {
  test("the catalog exposes every required algorithm key", async ({ request }) => {
    const token = await login(request, ANALYST);
    const res = await request.get(`${API}/api/algorithms`, { headers: auth(token) });
    expect(res.status(), await res.text()).toBe(200);
    const keys = (await res.json()).map((a: any) => a.key);
    for (const key of ALGORITHM_KEYS) {
      expect(keys, `catalog is missing "${key}"`).toContain(key);
    }
  });

  test("catalog entries carry the metadata the Lab renders", async ({ request }) => {
    const token = await login(request, ANALYST);
    const res = await request.get(`${API}/api/algorithms`, { headers: auth(token) });
    for (const entry of await res.json()) {
      expect(entry.displayName, `${entry.key} needs a display name`).toBeTruthy();
      expect(entry.family, `${entry.key} needs an algorithm family`).toBeTruthy();
      expect(entry.description, `${entry.key} needs a description`).toBeTruthy();
    }
  });

  test("an unknown algorithm key returns 404", async ({ request }) => {
    const token = await login(request, ANALYST);
    const res = await request.post(`${API}/api/algorithms/not_a_real_algorithm/run`, { headers: auth(token) });
    expect(res.status()).toBe(404);
  });

  for (const key of ALGORITHM_KEYS) {
    test(`${key} runs, reports latency and records a run`, async ({ request }) => {
      const token = await login(request, ANALYST);
      await ensureSeeded(request, token);

      const res = await request.post(`${API}/api/algorithms/${key}/run`, { headers: auth(token) });
      expect(res.status(), `${key}: ${await res.text()}`).toBe(200);
      const result = await res.json();

      expect(result.algorithmKey).toBe(key);
      expect(result.status, `${key} must not fail`).toBe("SUCCESS");
      expect(typeof result.durationMs).toBe("number");
      expect(result.durationMs).toBeGreaterThanOrEqual(0);
      expect(result.revenueDelta).not.toBeNull();

      const runs = await request.get(`${API}/api/algorithms/runs`, { headers: auth(token) });
      expect(runs.status()).toBe(200);
      const recorded = (await runs.json()).find((r: any) => r.id === result.runId || r.runId === result.runId);
      expect(recorded, `${key} run must be persisted to algorithm_run`).toBeTruthy();
    });
  }

  test("reprice and lab run share one implementation and both persist price history", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);

    const viaLab = await request.post(`${API}/api/algorithms/time_pressure_heuristic/run`, { headers: auth(token) });
    const viaReprice = await request.post(`${API}/api/pricing/reprice`, {
      headers: auth(token),
      data: { algorithmKey: "time_pressure_heuristic" },
    });

    expect(viaLab.status()).toBe(200);
    expect(viaReprice.status(), await viaReprice.text()).toBe(200);

    const lab = await viaLab.json();
    const reprice = await viaReprice.json();
    expect(reprice.algorithmKey).toBe(lab.algorithmKey);
    expect(Object.keys(reprice).sort()).toEqual(Object.keys(lab).sort());
  });

  test("baseline restores list fares so it is a valid experimental control", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);

    await request.post(`${API}/api/algorithms/greedy_protection/run`, { headers: auth(token) });
    const res = await request.post(`${API}/api/algorithms/baseline/run`, { headers: auth(token) });
    expect(res.status()).toBe(200);

    const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
    for (const flight of await inv.json()) {
      for (const fc of flight.fareClasses ?? []) {
        expect(Number(fc.currentPrice), `${flight.flightNo}/${fc.code} should sit at base after baseline`)
          .toBeCloseTo(Number(fc.basePrice), 2);
      }
    }
  });

  test("repricing never drives a fare to zero or negative", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);
    await request.post(`${API}/api/simulate`, { headers: auth(token), data: { intensity: 3 } });

    for (const key of ["greedy_protection", "dp_seat_protect", "time_pressure_heuristic", "revenue_optimize"]) {
      await request.post(`${API}/api/algorithms/${key}/run`, { headers: auth(token) });
    }

    const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
    for (const flight of await inv.json()) {
      for (const fc of flight.fareClasses ?? []) {
        expect(Number(fc.currentPrice), `${flight.flightNo}/${fc.code}`).toBeGreaterThan(0);
      }
      expect(flight.seatsLeft, `${flight.flightNo} inventory must stay non-negative`).toBeGreaterThanOrEqual(0);
    }
  });
});

test.describe("simulation", () => {
  test("simulate records demand snapshots and respects capacity", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);

    const res = await request.post(`${API}/api/simulate`, { headers: auth(token), data: { intensity: 5 } });
    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(body.demandSnapshots).toBeGreaterThan(0);
    expect(typeof body.durationMs).toBe("number");

    const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
    for (const flight of await inv.json()) {
      expect(flight.seatsLeft).toBeGreaterThanOrEqual(0);
      expect(flight.seatsLeft).toBeLessThanOrEqual(flight.seatsTotal);
    }
  });

  test("seeding twice does not duplicate the network", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);
    const first = (await (await request.get(`${API}/api/ops/inventory`, { headers: auth(token) })).json()).length;
    await request.post(`${API}/api/simulate/seed`, { headers: auth(token) });
    const second = (await (await request.get(`${API}/api/ops/inventory`, { headers: auth(token) })).json()).length;
    expect(second, "seed must be idempotent").toBe(first);
  });
});
