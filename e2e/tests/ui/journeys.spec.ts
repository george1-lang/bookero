import { test, expect, Page } from "@playwright/test";

const ANALYST = { email: "analyst@bookero.local", password: "password" };
const TRAVELER = { email: "traveler@bookero.local", password: "password" };

async function signIn(page: Page, who: { email: string; password: string }) {
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(who.email);
  await page.getByLabel(/password/i).fill(who.password);
  await page.getByRole("button", { name: /access system|sign in|log ?in/i }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20_000 });
}

/** Any uncaught page error or failed request is a bug, even if the DOM still renders. */
function trackPageFailures(page: Page) {
  const failures: string[] = [];
  page.on("pageerror", (e) => failures.push(`pageerror: ${e.message}`));
  page.on("console", (m) => {
    if (m.type() === "error" && !/favicon/i.test(m.text())) {
      failures.push(`console: ${m.text()}`);
    }
  });
  page.on("response", (r) => {
    if (r.status() >= 500) failures.push(`${r.status()} ${r.url()}`);
  });
  return failures;
}

test.describe("traveller journey", () => {
  test("login page renders the brand and both demo accounts", async ({ page }) => {
    const failures = trackPageFailures(page);
    await page.goto("/login");
    await expect(page.locator("body")).toContainText(/bookero/i);
    await expect(page.getByText(ANALYST.email)).toBeVisible();
    await expect(page.getByText(TRAVELER.email)).toBeVisible();
    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("bad credentials show a readable error, not a crash", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel(/email/i).fill(TRAVELER.email);
    await page.getByLabel(/password/i).fill("definitely-wrong");
    await page.getByRole("button", { name: /access system|sign in|log ?in/i }).click();
    await expect(page.locator("body")).toContainText(/invalid|incorrect|failed/i, { timeout: 15_000 });
    await expect(page).toHaveURL(/\/login/);
  });

  test("traveller searches, opens a flight and books a seat", async ({ page }) => {
    const failures = trackPageFailures(page);
    await signIn(page, TRAVELER);

    await expect(page).toHaveURL(/\/$|\/\?/);

    // The route chips are the discovery path a first-time traveller uses.
    const chip = page.getByRole("button").filter({ hasText: /^[A-Z]{3} to [A-Z]{3}/ }).first();
    await expect(chip).toBeVisible({ timeout: 20_000 });
    await chip.click();

    await page.getByRole("button", { name: /search/i }).click();

    const firstResult = page.getByRole("link").filter({ hasText: /BK\d+/ }).first();
    await expect(firstResult).toBeVisible({ timeout: 20_000 });
    await firstResult.click();

    await expect(page).toHaveURL(/\/flights\//);
    await expect(page.locator("body")).toContainText(/fare/i);

    await page.getByRole("button", { name: /book/i }).first().click();
    await expect(page).toHaveURL(/\/bookings/, { timeout: 20_000 });
    await expect(page.locator("body")).toContainText(/BK\d+/);

    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("travellers are kept out of the ops console", async ({ page }) => {
    await signIn(page, TRAVELER);
    await page.goto("/ops");
    await expect(page).not.toHaveURL(/\/ops$/, { timeout: 15_000 });
  });
});

test.describe("analyst journey", () => {
  test("ops console lists inventory and exposes the control deck", async ({ page }) => {
    const failures = trackPageFailures(page);
    await signIn(page, ANALYST);
    await expect(page).toHaveURL(/\/ops/);

    await expect(page.getByRole("button", { name: /seed network/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /run simulation/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^reprice$/i })).toBeVisible();
    await expect(page.locator("table")).toContainText(/BK\d+/, { timeout: 20_000 });

    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("repricing from the console reports a result", async ({ page }) => {
    await signIn(page, ANALYST);
    await page.getByRole("button", { name: /^reprice$/i }).click();
    await expect(page.getByRole("status")).toContainText(/ms/, { timeout: 60_000 });
  });

  test("dashboard renders revenue KPIs and a chart", async ({ page }) => {
    const failures = trackPageFailures(page);
    await signIn(page, ANALYST);
    await page.goto("/ops/dashboard");
    await expect(page.locator("body")).toContainText(/revenue/i, { timeout: 20_000 });
    await expect(page.locator("svg").first()).toBeVisible();
    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("algorithm lab lists every algorithm", async ({ page }) => {
    const failures = trackPageFailures(page);
    await signIn(page, ANALYST);
    await page.goto("/ops/lab");
    for (const name of ["Baseline", "Greedy", "DP Seat", "Revenue", "Time Pressure", "Demand ML"]) {
      await expect(page.locator("body")).toContainText(new RegExp(name, "i"), { timeout: 20_000 });
    }
    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("lab detail runs an algorithm and renders its documentation flowchart", async ({ page }) => {
    const failures = trackPageFailures(page);
    await signIn(page, ANALYST);
    await page.goto("/ops/lab/dp_seat_protect");

    await expect(page.locator("body")).toContainText(/dp seat/i, { timeout: 20_000 });
    await page.getByRole("button", { name: /run/i }).first().click();
    await expect(page.locator("body")).toContainText(/success/i, { timeout: 60_000 });

    // The markdown pack must load and its Mermaid block must become an SVG diagram.
    await expect(page.locator("body")).toContainText(/pseudocode|complexity/i, { timeout: 20_000 });
    await expect(page.locator("figure svg").first()).toBeVisible({ timeout: 30_000 });

    expect(failures, failures.join("\n")).toEqual([]);
  });
});
