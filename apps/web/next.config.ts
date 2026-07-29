import type { NextConfig } from "next";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

// Vercel builds its own routing and functions from the default output. Forcing
// standalone there leaves nothing for it to route, which surfaces as a platform
// 404 on every path. Standalone is only what the Dockerfile needs.
const isVercel = Boolean(process.env.VERCEL);

const nextConfig: NextConfig = {
  ...(isVercel ? {} : { output: "standalone" as const }),
  // The monorepo carries sibling lockfiles (e2e/), so the workspace root is
  // pinned rather than inferred.
  turbopack: { root: here },
  outputFileTracingRoot: here,
};

export default nextConfig;
