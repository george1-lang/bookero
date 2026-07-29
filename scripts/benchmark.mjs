// Measures every algorithm against a freshly seeded, simulated world and writes
// data/processed/benchmark.json plus a markdown table on stdout.
// Usage: node scripts/benchmark.mjs   (stack must be running)
import { writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const API = process.env.API_BASE_URL ?? "http://localhost:8090";
const ANALYTICS = process.env.ANALYTICS_BASE_URL ?? "http://localhost:8001";

const KEYS = [
  "baseline", "route_graph", "shortest_path", "flight_search", "slot_schedule",
  "greedy_protection", "dp_seat_protect", "revenue_optimize",
  "time_pressure_heuristic", "demand_ml",
];

const REPEATS = 3;

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

console.error("resetting and seeding the world…");
const seed = await json(`${API}/api/simulate/reset`, { method: "POST", headers: authed });
console.error(`  ${seed.flights} flights on the ${seed.hub} hub`);

console.error("running the demand simulation…");
const sim = await json(`${API}/api/simulate`, {
  method: "POST", headers: authed, body: JSON.stringify({ intensity: 5 }),
});
console.error(`  ${sim.demandSnapshots} snapshots, ${sim.syntheticBookings} synthetic bookings`);

console.error("training the demand model…");
let training = null;
try {
  training = await json(`${ANALYTICS}/demand/train`, { method: "POST" });
  console.error(`  ${training.model}: MAE ${training.mae}, RMSE ${training.rmse}`);
} catch (e) {
  console.error(`  skipped: ${e.message}`);
}

const loadFactor = async () => {
  const rows = await json(`${API}/api/ops/inventory`, { headers: authed });
  const total = rows.reduce((a, r) => a + r.seatsTotal, 0);
  const left = rows.reduce((a, r) => a + r.seatsLeft, 0);
  return { flights: rows.length, loadFactor: total ? (total - left) / total : 0 };
};

const results = [];
for (const key of KEYS) {
  // Fares are restored to base before every sample: repricing is idempotent, so a
  // second consecutive run would legitimately move nothing and measure nothing.
  const samples = [];
  let observed = null;
  for (let i = 0; i < REPEATS; i++) {
    if (key !== "baseline") {
      await json(`${API}/api/algorithms/baseline/run`, { method: "POST", headers: authed });
    }
    const run = await json(`${API}/api/algorithms/${key}/run`, { method: "POST", headers: authed });
    samples.push(run.durationMs);
    if (i === 0) observed = run;
  }
  const last = observed;

  const { flights, loadFactor: lf } = await loadFactor();
  samples.sort((a, b) => a - b);
  results.push({
    key,
    status: last.status,
    medianMs: samples[Math.floor(samples.length / 2)],
    minMs: samples[0],
    maxMs: samples[samples.length - 1],
    revenueDelta: Number(last.revenueDelta ?? 0),
    priceUpdates: (last.priceUpdates ?? []).length,
    flightsAffected: last.flightsAffected,
    flights,
    loadFactor: Number(lf.toFixed(4)),
    metrics: last.metrics ?? {},
  });
  console.error(`  ${key.padEnd(24)} ${String(samples[Math.floor(samples.length / 2)]).padStart(6)} ms`);
}

const metrics = await json(`${API}/api/ops/metrics`, { headers: authed });
const report = { generatedAt: new Date().toISOString(), seed, simulation: sim, training, results, metrics };

const out = resolve(here, "../data/processed/benchmark.json");
await mkdir(dirname(out), { recursive: true });
await writeFile(out, JSON.stringify(report, null, 2));

console.log("| Key | Status | Median ms | Min ms | Max ms | Fares moved | Revenue delta vs base |");
console.log("|---|---|---:|---:|---:|---:|---:|");
for (const r of results) {
  console.log(`| \`${r.key}\` | ${r.status} | ${r.medianMs} | ${r.minMs} | ${r.maxMs} | ${r.priceUpdates} | ${r.revenueDelta.toFixed(2)} |`);
}
console.error(`\nwritten to ${out}`);
