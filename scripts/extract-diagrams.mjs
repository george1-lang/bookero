// Pulls every ```mermaid block out of docs/ into deliverables/diagrams/ as .mmd
// files, ready to render to PNG or SVG with the Mermaid CLI.
// Usage: node scripts/extract-diagrams.mjs
import { readFile, writeFile, mkdir, readdir } from "node:fs/promises";
import { dirname, resolve, relative } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const DOCS = resolve(here, "../docs");
const OUT = resolve(here, "../deliverables/diagrams");

const FENCE = /```mermaid\s*\n([\s\S]*?)```/g;
/** The nearest preceding heading is used to name the file. */
const HEADING = /^#{1,6}\s+(.*)$/gm;

function slug(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
}

async function markdownFiles(dir) {
  const found = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "superpowers") continue;
      found.push(...(await markdownFiles(full)));
    } else if (entry.name.endsWith(".md")) {
      found.push(full);
    }
  }
  return found;
}

await mkdir(OUT, { recursive: true });

const index = [];
for (const file of (await markdownFiles(DOCS)).sort()) {
  const text = await readFile(file, "utf8");
  const source = relative(resolve(here, ".."), file).replace(/\\/g, "/");

  const headings = [...text.matchAll(HEADING)].map((m) => ({ at: m.index, title: m[1].trim() }));
  let n = 0;

  for (const match of text.matchAll(FENCE)) {
    n += 1;
    const nearest = headings.filter((h) => h.at < match.index).at(-1);
    const base = `${slug(source.replace(/^docs\//, "").replace(/\.md$/, ""))}-${String(n).padStart(2, "0")}-${slug(nearest?.title ?? "diagram")}`;
    const target = resolve(OUT, `${base}.mmd`);
    await writeFile(target, match[1].trim() + "\n", "utf8");
    index.push({ file: `${base}.mmd`, source, section: nearest?.title ?? "(top of file)" });
  }
}

const manifest = [
  "# Diagram index",
  "",
  "Extracted by `node scripts/extract-diagrams.mjs`. Render with:",
  "",
  "```bash",
  "npx -y @mermaid-js/mermaid-cli -i deliverables/diagrams/<name>.mmd -o <name>.png -b transparent -s 3",
  "```",
  "",
  "| File | Source document | Section |",
  "|---|---|---|",
  ...index.map((d) => `| \`${d.file}\` | \`${d.source}\` | ${d.section} |`),
  "",
].join("\n");

await writeFile(resolve(OUT, "INDEX.md"), manifest, "utf8");
console.log(`extracted ${index.length} diagrams to ${OUT}`);
