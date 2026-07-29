// Controlled comparison of pricing strategies on realized revenue.
//
// For each strategy the world is reset to an identical starting point, a first wave
// of demand books seats at list fares, the strategy reprices, and a second wave books
// at the new fares. Because the simulator is seeded, every arm sees the same demand,
// so the only difference between arms is the pricing decision.
//
// Usage: node scripts/experiment.mjs   (stack must be running)
import { writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const API = process.env.API_BASE_URL ?? "http://localhost:8090";

const STRATEGIES = [
  "baseline",
  "greedy_protection",
  "dp_seat_protect",
  "time_pressure_heuristic",
  "revenue_optimize",
  "demand_ml",
];

const WAVE_ONE = Number(process.env.WAVE_ONE ?? 3);
const WAVE_TWO = Number(process.env.WAVE_TWO ?? 7);

async function json(url, options = {}) {
  const res = await fetch(url, options);
  if (!res.ok) throw new Error(`${options.method ?? "GET"} ${url} -> ${res.status} ${await res.text()}`);
  return res.json();
}

const { token } = await json(`${API}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "analyst@bookero.local", password: "password" }),
});
const authed = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

const arms = [];
for (const strategy of STRATEGIES) {
  process.stderr.write(`arm ${strategy.padEnd(24)}`);

  await json(`${API}/api/simulate/reset`, { method: "POST", headers: authed });
  await json(`${API}/api/simulate`, {
    method: "POST", headers: authed, body: JSON.stringify({ intensity: WAVE_ONE }),
  });

  const run = await json(`${API}/api/pricing/reprice`, {
    method: "POST", headers: authed, body: JSON.stringify({ algorithmKey: strategy }),
  });

  await json(`${API}/api/simulate`, {
    method: "POST", headers: authed, body: JSON.stringify({ intensity: WAVE_TWO }),
  });

  const m = await json(`${API}/api/ops/metrics`, { headers: authed });
  arms.push({
    strategy,
    durationMs: run.durationMs,
    faresMoved: (run.priceUpdates ?? []).length,
    totalRevenue: Number(m.totalRevenue ?? 0),
    baselineRevenue: Number(m.baselineRevenue ?? 0),
    loadFactor: Number(m.loadFactor ?? 0),
    avgFare: Number(m.avgFare ?? 0),
    seatsSold: Number(m.seatsSold ?? 0),
    bookingCount: Number(m.bookingCount ?? 0),
  });
  process.stderr.write(` revenue ${arms.at(-1).totalRevenue.toFixed(0)}\n`);
}

const control = arms.find((a) => a.strategy === "baseline");
for (const arm of arms) {
  arm.liftVsControl = control.totalRevenue
    ? (arm.totalRevenue - control.totalRevenue) / control.totalRevenue
    : 0;
}

const report = { generatedAt: new Date().toISOString(), waveOneIntensity: WAVE_ONE, waveTwoIntensity: WAVE_TWO, arms };
const out = resolve(here, `../data/processed/experiment-w${WAVE_ONE}-w${WAVE_TWO}.json`);
await mkdir(dirname(out), { recursive: true });
await writeFile(out, JSON.stringify(report, null, 2));

console.log("| Strategy | Revenue | vs control | Load factor | Avg fare | Seats sold | Reprice ms |");
console.log("|---|---:|---:|---:|---:|---:|---:|");
for (const a of arms) {
  const lift = a.strategy === "baseline" ? "control" : `${(a.liftVsControl * 100).toFixed(2)}%`;
  console.log(
    `| \`${a.strategy}\` | ${a.totalRevenue.toFixed(2)} | ${lift} | ` +
      `${(a.loadFactor * 100).toFixed(1)}% | ${a.avgFare.toFixed(2)} | ${a.seatsSold} | ${a.durationMs} |`,
  );
}
console.error(`\nwritten to ${out}`);
