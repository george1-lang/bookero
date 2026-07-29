import { request } from "@playwright/test";

const API = process.env.API_BASE_URL ?? "http://localhost:8090";
const ANALYTICS = process.env.ANALYTICS_BASE_URL ?? "http://localhost:8001";

/**
 * The booking and repricing suites consume real inventory, so every run starts from
 * a freshly seeded world. Reference data and user accounts are untouched.
 */
export default async function globalSetup() {
  const ctx = await request.newContext();

  const health = await ctx.get(`${ANALYTICS}/health`).catch(() => null);
  if (!health?.ok()) {
    throw new Error(`analytics is not reachable at ${ANALYTICS} - start the stack first`);
  }

  const login = await ctx.post(`${API}/api/auth/login`, {
    data: { email: "analyst@bookero.local", password: "password" },
  });
  if (!login.ok()) {
    throw new Error(`api is not reachable at ${API} - start the stack first`);
  }
  const { token } = await login.json();

  const reset = await ctx.post(`${API}/api/simulate/reset`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: 120_000,
  });
  if (!reset.ok()) {
    throw new Error(`could not reset the demo world: ${await reset.text()}`);
  }
  console.log(`[e2e] ${JSON.stringify(await reset.json())}`);

  await ctx.post(`${API}/api/simulate`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { intensity: 4 },
    timeout: 120_000,
  });

  await ctx.dispose();
}
