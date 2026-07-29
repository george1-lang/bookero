import { APIRequestContext, expect } from "@playwright/test";

export const API = process.env.API_BASE_URL ?? "http://localhost:8090";
export const ANALYTICS = process.env.ANALYTICS_BASE_URL ?? "http://localhost:8001";

export const ANALYST = { email: "analyst@bookero.local", password: "password" };
export const TRAVELER = { email: "traveler@bookero.local", password: "password" };

export const ALGORITHM_KEYS = [
  "baseline",
  "route_graph",
  "shortest_path",
  "flight_search",
  "slot_schedule",
  "greedy_protection",
  "dp_seat_protect",
  "revenue_optimize",
  "time_pressure_heuristic",
  "demand_ml",
] as const;

export async function login(
  request: APIRequestContext,
  who: { email: string; password: string },
): Promise<string> {
  const res = await request.post(`${API}/api/auth/login`, { data: who });
  expect(res.status(), `login ${who.email}: ${await res.text()}`).toBe(200);
  const body = await res.json();
  expect(body.token, "login response must carry a token").toBeTruthy();
  return body.token as string;
}

export function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

/** Seeds the demo network if it is not already present; returns the flight count available. */
export async function ensureSeeded(request: APIRequestContext, token: string): Promise<number> {
  const inv = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
  expect(inv.status(), `ops inventory: ${await inv.text()}`).toBe(200);
  const rows = await inv.json();
  if (Array.isArray(rows) && rows.length > 0) return rows.length;

  const seeded = await request.post(`${API}/api/simulate/seed`, { headers: auth(token) });
  expect(seeded.status(), `seed: ${await seeded.text()}`).toBe(200);
  const after = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
  const afterRows = await after.json();
  expect(afterRows.length, "seeding must produce flights").toBeGreaterThan(0);
  return afterRows.length;
}

/** The flight closest to selling out, so an oversell probe stays small but meaningful. */
export async function tightestFlight(request: APIRequestContext, token: string) {
  const res = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
  const rows = await res.json();
  const open = rows
    .filter((r: any) => r.seatsLeft > 0)
    .sort((a: any, b: any) => a.seatsLeft - b.seatsLeft);
  expect(open[0], "expected at least one flight with seats left").toBeTruthy();
  return open[0];
}

/** Picks a flight that still has seats, plus one of its fare classes. */
export async function bookableFlight(request: APIRequestContext, token: string) {
  const res = await request.get(`${API}/api/ops/inventory`, { headers: auth(token) });
  const rows = await res.json();
  const open = rows.find((r: any) => r.seatsLeft > 0);
  expect(open, "expected at least one flight with seats left").toBeTruthy();
  return open;
}
