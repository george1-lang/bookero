import { test, expect } from "@playwright/test";
import { API, ANALYTICS, ANALYST, login, auth, ensureSeeded } from "./helpers";

test.describe("analytics service", () => {
  test("health reports service and database state", async ({ request }) => {
    const res = await request.get(`${ANALYTICS}/health`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("ok");
    expect(body.database).toBe("up");
  });

  test("reference data was ingested by the ETL", async ({ request }) => {
    const res = await request.get(`${ANALYTICS}/eda/summary`);
    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    const counts = body.rowCounts ?? body.counts ?? body;
    expect(Number(counts.airport ?? counts.airports), "OpenFlights airports must be loaded")
      .toBeGreaterThan(1000);
    expect(Number(counts.route ?? counts.routes), "OpenFlights routes must be loaded")
      .toBeGreaterThan(1000);
  });

  test("revenue metrics expose the baseline comparison the evaluation depends on", async ({ request }) => {
    const res = await request.get(`${ANALYTICS}/metrics/revenue`);
    expect(res.status(), await res.text()).toBe(200);
    const m = await res.json();

    for (const field of [
      "totalRevenue", "baselineRevenue", "loadFactor",
      "avgFare", "seatsSold", "seatsTotal", "bookingCount", "revenueByDay",
    ]) {
      expect(m, `metrics payload is missing ${field}`).toHaveProperty(field);
    }
    expect(Number(m.loadFactor)).toBeGreaterThanOrEqual(0);
    expect(Number(m.loadFactor)).toBeLessThanOrEqual(1);
    expect(Array.isArray(m.revenueByDay)).toBe(true);
  });

  test("demand forecast returns bounded scores even before training", async ({ request }) => {
    const res = await request.get(`${ANALYTICS}/demand/forecast`);
    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.forecasts)).toBe(true);
    for (const f of body.forecasts) {
      expect(Number(f.demandScore)).toBeGreaterThanOrEqual(0);
      expect(Number(f.demandScore)).toBeLessThanOrEqual(1);
    }
  });

  test("demand model trains and reports held-out error", async ({ request }) => {
    const res = await request.post(`${ANALYTICS}/demand/train`);
    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(Number(body.samples)).toBeGreaterThan(0);
    expect(Number.isFinite(Number(body.mae))).toBe(true);
    expect(Number.isFinite(Number(body.rmse))).toBe(true);
  });

  test("the API proxies metrics so the browser never has to cross origins", async ({ request }) => {
    const token = await login(request, ANALYST);
    await ensureSeeded(request, token);
    const res = await request.get(`${API}/api/ops/metrics`, { headers: auth(token) });
    expect(res.status(), await res.text()).toBe(200);
    const m = await res.json();
    expect(m).toHaveProperty("totalRevenue");
    expect(m).toHaveProperty("baselineRevenue");
  });
});
