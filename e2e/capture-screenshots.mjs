// Captures the screens an evaluator will be shown, into deliverables/screenshots/.
// Requires the stack to be running: ./scripts/stack.sh up
import { chromium } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(here, "../deliverables/screenshots");
const WEB = process.env.WEB_BASE_URL ?? "http://localhost:3100";

const ANALYST = { email: "analyst@bookero.local", password: "password" };
const TRAVELER = { email: "traveler@bookero.local", password: "password" };

async function signIn(page, who) {
  await page.goto(`${WEB}/login`, { waitUntil: "networkidle" });
  await page.locator("#email").fill(who.email);
  await page.locator("#password").fill(who.password);
  await page.getByRole("button", { name: /access system/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 20_000 });
}

async function shoot(page, path, file, settle = 2500) {
  await page.goto(`${WEB}${path}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(settle);
  await page.screenshot({ path: `${OUT}/${file}`, fullPage: true });
  console.log(`  ${file}`);
}

await mkdir(OUT, { recursive: true });
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });

console.log("capturing:");
await shoot(page, "/login", "01-login.png");

await signIn(page, TRAVELER);
await shoot(page, "/", "02-traveler-search.png");
await shoot(page, "/bookings", "03-traveler-bookings.png");

await page.evaluate(() => sessionStorage.clear());
await signIn(page, ANALYST);
await shoot(page, "/ops", "04-ops-inventory.png");
await shoot(page, "/ops/dashboard", "05-ops-dashboard.png");
await shoot(page, "/ops/lab", "06-algorithm-lab.png");
await shoot(page, "/ops/lab/dp_seat_protect", "07-lab-dp-seat-protect.png", 6000);

await browser.close();
console.log(`written to ${OUT}`);
