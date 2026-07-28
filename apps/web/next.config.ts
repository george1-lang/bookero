import type { NextConfig } from "next";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

const nextConfig: NextConfig = {
  output: "standalone",
  // The monorepo carries sibling lockfiles (e2e/), so the workspace root is
  // pinned rather than inferred.
  turbopack: { root: here },
  outputFileTracingRoot: here,
};

export default nextConfig;
