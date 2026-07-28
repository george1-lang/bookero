import { defineConfig, devices } from "@playwright/test";

const WEB = process.env.WEB_BASE_URL ?? "http://localhost:3100";
const API = process.env.API_BASE_URL ?? "http://localhost:8090";

export default defineConfig({
  testDir: "./tests",
  // The booking and repricing suites mutate shared inventory, so the whole run is serial.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: WEB,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
  projects: [
    {
      name: "api",
      testMatch: /api\/.*\.spec\.ts/,
      use: { baseURL: API },
    },
    {
      name: "chromium",
      testMatch: /ui\/.*\.spec\.ts/,
      dependencies: ["api"],
      use: { ...devices["Desktop Chrome"], baseURL: WEB },
    },
  ],
});
