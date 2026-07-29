// Read-mostly smoke test of a deployed Bookero stack, driven through a real browser.
// Unlike the Playwright suite it never resets or re-seeds, so it is safe to run
// against a live demo.
//
// Usage: WEB=https://your-app.vercel.app node e2e/verify-production.mjs
import { chromium } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const WEB = process.env.WEB ?? "https://bookero-ruby.vercel.app";
const SHOTS = resolve(here, "../deliverables/screenshots/production");

const ANALYST = { email: "analyst@bookero.local", password: "password" };
const TRAVELER = { email: "traveler@bookero.local", password: "password" };

const results = [];
const problems = [];

function watch(page) {
  page.on("console", (m) => {
    const t = m.text();
    if (m.type() === "error" && !/favicon/i.test(t)) problems.push(`console: ${t.slice(0, 160)}`);
  });
  page.on("pageerror", (e) => problems.push(`pageerror: ${e.message.slice(0, 160)}`));
  page.on("requestfailed", (r) => {
    const f = r.failure()?.errorText ?? "";
    if (r.url().includes("/api/") || /onrender/.test(r.url())) {
      problems.push(`request failed: ${r.url().slice(0, 90)} ${f}`);
    }
  });
}

async function step(name, fn) {
  try {
    const detail = await fn();
    results.push(`  PASS  ${name}${detail ? ` (${detail})` : ""}`);
    return true;
  } catch (e) {
    results.push(`  FAIL  ${name}: ${String(e.message).split("\n")[0].slice(0, 140)}`);
    return false;
  }
}

async function signIn(page, who) {
  await page.goto(`${WEB}/login`, { waitUntil: "networkidle" });
  await page.locator("#email").fill(who.email);
  await page.locator("#password").fill(who.password);
  await page.getByRole("button", { name: /access system/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 45_000 });
}

await mkdir(SHOTS, { recursive: true });
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
watch(page);

console.log(`verifying ${WEB}\n`);

await step("login page loads", async () => {
  await page.goto(`${WEB}/login`, { waitUntil: "networkidle", timeout: 60_000 });
  const body = await page.locator("body").innerText();
  if (!/bookero/i.test(body)) throw new Error("wordmark missing");
  await page.screenshot({ path: `${SHOTS}/01-login.png`, fullPage: true });
});

await step("analyst signs in", async () => {
  await signIn(page, ANALYST);
  return page.url().replace(WEB, "");
});

await step("ops inventory shows seeded flights", async () => {
  await page.goto(`${WEB}/ops`, { waitUntil: "networkidle" });
  await page.waitForTimeout(3000);
  const rows = await page.locator("table tbody tr").count();
  if (rows === 0) throw new Error("no inventory rows, check CORS on the api service");
  await page.screenshot({ path: `${SHOTS}/02-ops.png`, fullPage: true });
  return `${rows} flights`;
});

await step("revenue dashboard renders KPIs", async () => {
  await page.goto(`${WEB}/ops/dashboard`, { waitUntil: "networkidle" });
  await page.waitForTimeout(3000);
  const body = await page.locator("body").innerText();
  if (/analytics offline/i.test(body)) throw new Error("analytics reported offline");
  if (!/revenue/i.test(body)) throw new Error("no revenue content");
  await page.screenshot({ path: `${SHOTS}/03-dashboard.png`, fullPage: true });
});

await step("algorithm lab lists the catalog", async () => {
  await page.goto(`${WEB}/ops/lab`, { waitUntil: "networkidle" });
  await page.waitForTimeout(3000);
  const body = await page.locator("body").innerText();
  const found = ["Baseline", "Greedy", "DP Seat", "Revenue", "Demand ML"].filter((n) =>
    new RegExp(n, "i").test(body),
  );
  if (found.length < 5) throw new Error(`only found ${found.join(", ")}`);
  await page.screenshot({ path: `${SHOTS}/04-lab.png`, fullPage: true });
  return `${found.length}/5 named strategies`;
});

await step("lab detail runs an algorithm and renders its flowchart", async () => {
  await page.goto(`${WEB}/ops/lab/dp_seat_protect`, { waitUntil: "networkidle" });
  await page.getByRole("button", { name: /run/i }).first().click();
  await page.waitForFunction(() => /SUCCESS/i.test(document.body.innerText), null, { timeout: 90_000 });
  await page.waitForSelector("figure svg", { timeout: 45_000 });
  await page.screenshot({ path: `${SHOTS}/05-lab-detail.png`, fullPage: true });
});

await step("traveller searches and books a seat", async () => {
  await page.evaluate(() => sessionStorage.clear());
  await signIn(page, TRAVELER);
  await page.waitForTimeout(2500);

  const chip = page.getByRole("button").filter({ hasText: /^[A-Z]{3} to [A-Z]{3}/ }).first();
  await chip.waitFor({ timeout: 30_000 });
  await chip.click();
  await page.getByRole("button", { name: /search/i }).click();

  const first = page.getByRole("link").filter({ hasText: /BK\d+/ }).first();
  await first.waitFor({ timeout: 30_000 });
  await first.click();
  await page.waitForURL(/\/flights\//, { timeout: 30_000 });
  await page.screenshot({ path: `${SHOTS}/06-flight.png`, fullPage: true });

  await page.getByRole("button", { name: /book/i }).first().click();
  await page.waitForURL(/\/bookings/, { timeout: 45_000 });
  await page.screenshot({ path: `${SHOTS}/07-bookings.png`, fullPage: true });
});

await browser.close();

console.log(results.join("\n"));
const failed = results.filter((r) => r.includes("FAIL")).length;
console.log(`\n${results.length - failed}/${results.length} checks passed`);

if (problems.length) {
  console.log("\nbrowser problems observed:");
  for (const p of [...new Set(problems)].slice(0, 12)) console.log(`  ${p}`);
}
console.log(`\nscreenshots: ${SHOTS}`);
process.exitCode = failed ? 1 : 0;
