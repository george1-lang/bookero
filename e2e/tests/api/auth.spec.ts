import { test, expect } from "@playwright/test";
import { API, ANALYST, TRAVELER, login, auth } from "./helpers";

test.describe("auth", () => {
  test("analyst logs in and receives an ANALYST token", async ({ request }) => {
    const res = await request.post(`${API}/api/auth/login`, { data: ANALYST });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.role).toBe("ANALYST");
    expect(body.token.split(".")).toHaveLength(3);
  });

  test("traveler logs in and receives a TRAVELER token", async ({ request }) => {
    const res = await request.post(`${API}/api/auth/login`, { data: TRAVELER });
    expect(res.status()).toBe(200);
    expect((await res.json()).role).toBe("TRAVELER");
  });

  test("wrong password is rejected without revealing account existence", async ({ request }) => {
    const bad = await request.post(`${API}/api/auth/login`, {
      data: { email: ANALYST.email, password: "wrong-password" },
    });
    const missing = await request.post(`${API}/api/auth/login`, {
      data: { email: "nobody@bookero.local", password: "wrong-password" },
    });
    expect(bad.status()).toBe(401);
    expect(missing.status()).toBe(401);
    expect((await bad.json()).detail).toBe((await missing.json()).detail);
  });

  test("malformed login body returns a 400 with field errors", async ({ request }) => {
    const res = await request.post(`${API}/api/auth/login`, { data: { email: "not-an-email" } });
    expect(res.status()).toBe(400);
  });

  test("protected endpoint without a token returns 401 JSON, not an HTML page", async ({ request }) => {
    const res = await request.get(`${API}/api/auth/me`);
    expect(res.status()).toBe(401);
    expect(res.headers()["content-type"] ?? "").toContain("json");
  });

  test("a tampered token is rejected", async ({ request }) => {
    const token = await login(request, ANALYST);
    const tampered = token.slice(0, -3) + "aaa";
    const res = await request.get(`${API}/api/auth/me`, { headers: auth(tampered) });
    expect(res.status()).toBe(401);
  });

  test("travelers cannot reach analyst-only endpoints", async ({ request }) => {
    const token = await login(request, TRAVELER);
    for (const path of ["/api/ops/inventory", "/api/algorithms", "/api/simulate"]) {
      const res = path === "/api/simulate"
        ? await request.post(`${API}${path}`, { headers: auth(token), data: {} })
        : await request.get(`${API}${path}`, { headers: auth(token) });
      expect(res.status(), `${path} must be forbidden for TRAVELER`).toBe(403);
    }
  });

  test("analysts cannot create traveler bookings", async ({ request }) => {
    const token = await login(request, ANALYST);
    const res = await request.post(`${API}/api/bookings`, {
      headers: auth(token),
      data: { flightId: "00000000-0000-4000-8000-000000000000", fareClassId: "00000000-0000-4000-8000-000000000000" },
    });
    expect(res.status()).toBe(403);
  });
});
