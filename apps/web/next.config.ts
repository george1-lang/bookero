import type { NextConfig } from "next";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

// Vercel builds its own routing and functions from the default output and traces
// files itself. Standalone output and a pinned workspace root are only needed for
// the Docker image and to stop the local build inferring the wrong monorepo root,
// so both are kept off Vercel.
const isVercel = Boolean(process.env.VERCEL);

const selfHosted: NextConfig = {
  output: "standalone",
  turbopack: { root: here },
  outputFileTracingRoot: here,
};

const nextConfig: NextConfig = isVercel ? {} : selfHosted;

export default nextConfig;
