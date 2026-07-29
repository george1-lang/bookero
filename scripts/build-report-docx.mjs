// Builds deliverables/Bookero-Technical-Report.docx from docs/06-technical-report.md,
// replacing every Mermaid code block with the rendered PNG and appending a figure
// appendix of the system diagrams, algorithm flowcharts and UI screenshots.
//
// Requires pandoc on PATH (scripts/env.sh adds the portable copy).
// Usage: node scripts/build-report-docx.mjs
import { readFile, writeFile, mkdir, readdir, access } from "node:fs/promises";
import { dirname, resolve, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(here, "..");
const REPORT = resolve(ROOT, "docs/06-technical-report.md");
const PNG = resolve(ROOT, "deliverables/diagrams/png");
const SHOTS = resolve(ROOT, "deliverables/screenshots");
const OUT = resolve(ROOT, "deliverables/Bookero-Technical-Report.docx");
const WORK = resolve(ROOT, "deliverables/.report-build");

/** Diagrams inserted into the Method section, in reading order. */
const SYSTEM_FIGURES = [
  ["02-system-design-01-2-architecture-flowchart.png", "Service architecture and data flows"],
  ["bookero-use-case.png", "UML use case diagram: actors, system boundary, include and extend"],
  ["02-system-design-03-4-entity-relationship-diagram-schema-exact-match-to-v1-init-.png", "Entity relationship diagram, matching V1__init.sql"],
  ["02-system-design-06-7-algorithm-package-class-component-diagram.png", "Algorithm package: one shared execution path"],
  ["02-system-design-04-5-pricing-cycle-sequence-diagram.png", "Pricing cycle sequence"],
  ["02-system-design-05-6-oversell-safe-booking-transaction-sequence.png", "Oversell-safe booking transaction"],
  ["02-system-design-07-8-algorithm-run-lifecycle-state-diagram.png", "Algorithm run lifecycle"],
];

const SCREENSHOTS = [
  ["01-login.png", "Sign in"],
  ["02-traveler-search.png", "Traveller flight search with route discovery"],
  ["03-traveler-bookings.png", "Traveller booking history"],
  ["04-ops-inventory.png", "Analyst inventory console and control deck"],
  ["05-ops-dashboard.png", "Revenue dashboard"],
  ["06-algorithm-lab.png", "Algorithm Lab index"],
  ["07-lab-dp-seat-protect.png", "Algorithm Lab detail with rendered flowchart"],
];

function run(cmd, args, opts = {}) {
  // shell:true is needed for npx on Windows, and it also means arguments containing
  // spaces must be quoted here or the shell will split them into separate tokens.
  const quoted = args.map((a) => (/\s/.test(a) ? `"${a}"` : a));
  return new Promise((done) => {
    const child = spawn(cmd, quoted, { shell: true, stdio: ["ignore", "pipe", "pipe"], ...opts });
    let out = "", err = "";
    child.stdout.on("data", (d) => (out += d));
    child.stderr.on("data", (d) => (err += d));
    child.on("close", (code) => done({ code, out, err }));
  });
}

const exists = async (p) => access(p).then(() => true, () => false);

await mkdir(WORK, { recursive: true });

let markdown = await readFile(REPORT, "utf8");

// 1. Render the report's own Mermaid blocks and swap them for images.
const blocks = [...markdown.matchAll(/```mermaid\s*\n([\s\S]*?)```/g)];
let i = 0;
for (const block of blocks) {
  i += 1;
  const mmd = resolve(WORK, `report-${i}.mmd`);
  const png = resolve(PNG, `report-figure-${i}.png`);
  await writeFile(mmd, block[1].trim() + "\n", "utf8");
  const { code } = await run("npx", [
    "-y", "@mermaid-js/mermaid-cli", "-i", mmd, "-o", png,
    "-c", resolve(PNG, "mermaid-theme.json"), "-b", "#0E1113", "-w", "2400", "-s", "2", "--quiet",
  ]);
  if (code !== 0) {
    console.error(`  could not render report figure ${i}; leaving the code block in place`);
    continue;
  }
  markdown = markdown.replace(
    block[0],
    `![Figure ${i}. System architecture](${png.replace(/\\/g, "/")})\n`,
  );
  console.log(`  report figure ${i} rendered`);
}

// 2. Figure appendix: system diagrams, then every algorithm flowchart, then the UI.
const algorithmFigures = (await readdir(PNG))
  .filter((f) => f.startsWith("algorithms-") && f.endsWith(".png"))
  .sort();

const appendix = ["\n\n\\newpage\n", "## Appendix B: Figures\n", "### B.1 System design\n"];

for (const [file, caption] of SYSTEM_FIGURES) {
  const p = resolve(PNG, file);
  if (await exists(p)) appendix.push(`![${caption}](${p.replace(/\\/g, "/")})\n`);
  else console.error(`  missing system figure: ${file}`);
}

appendix.push("\n### B.2 Algorithm flowcharts\n");
for (const file of algorithmFigures) {
  const key = basename(file, ".png")
    .replace(/^algorithms-/, "")
    .replace(/-01-mermaid-flowchart$/, "")
    .replace(/-/g, "_");
  appendix.push(`![Flowchart: ${key}](${resolve(PNG, file).replace(/\\/g, "/")})\n`);
}

appendix.push("\n### B.3 User interface\n");
for (const [file, caption] of SCREENSHOTS) {
  const p = resolve(SHOTS, file);
  if (await exists(p)) appendix.push(`![${caption}](${p.replace(/\\/g, "/")})\n`);
  else console.error(`  missing screenshot: ${file}`);
}

markdown += appendix.join("\n");

const staged = resolve(WORK, "report.md");
await writeFile(staged, markdown, "utf8");

// 3. Convert. Image paths are absolute, so no resource path is needed.
const { code, err } = await run("pandoc", [
  staged,
  "-o", OUT,
  "--from", "markdown+pipe_tables+backtick_code_blocks+implicit_figures",
  "--to", "docx",
  "--toc", "--toc-depth=2",
  "--metadata", "title=Bookero: Dynamic Pricing for a Single Airline",
  "--metadata", "subtitle=Phase 6 Technical Report",
]);

if (code !== 0) {
  console.error(err);
  process.exitCode = 1;
} else {
  console.log(`\nwritten to ${OUT}`);
}
