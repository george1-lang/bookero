// The Algorithm Lab renders the same markdown that ships in docs/algorithms,
// so it is copied into public/ at build time rather than duplicated by hand.
import { cp, mkdir, readdir, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, "../../../docs/algorithms");
const target = resolve(here, "../public/docs/algorithms");

if (!existsSync(source)) {
  console.warn(`[copy-docs] ${source} not found - Lab docs will render placeholders`);
  process.exit(0);
}

await rm(target, { recursive: true, force: true });
await mkdir(target, { recursive: true });
await cp(source, target, { recursive: true });

const copied = (await readdir(target)).filter((f) => f.endsWith(".md"));
console.log(`[copy-docs] ${copied.length} algorithm docs -> ${join("public", "docs", "algorithms")}`);
