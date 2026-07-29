// Renders every extracted .mmd to PNG. Doubles as a syntax check: a diagram that
// fails here would render as an error box in the Algorithm Lab.
// Usage: node scripts/render-diagrams.mjs
import { readdir, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const DIAGRAMS = resolve(here, "../deliverables/diagrams");
const OUT = resolve(here, "../deliverables/diagrams/png");

const THEME = {
  theme: "base",
  themeVariables: {
    background: "#0E1113",
    primaryColor: "#151A1D",
    primaryTextColor: "#EDE7DA",
    primaryBorderColor: "#FFB100",
    lineColor: "#7C8A90",
    secondaryColor: "#1E2427",
    tertiaryColor: "#08090A",
    mainBkg: "#151A1D",
    nodeBorder: "#FFB100",
    clusterBkg: "#0E1113",
    clusterBorder: "#2A3236",
    titleColor: "#FFB100",
    attributeBackgroundColorOdd: "#151A1D",
    attributeBackgroundColorEven: "#0E1113",
    rowOdd: "#151A1D",
    rowEven: "#0E1113",
    textColor: "#EDE7DA",
    labelTextColor: "#EDE7DA",
    noteBkgColor: "#151A1D",
    noteTextColor: "#EDE7DA",
    noteBorderColor: "#2A3236",
    actorBkg: "#151A1D",
    actorTextColor: "#EDE7DA",
    actorBorder: "#FFB100",
    signalTextColor: "#EDE7DA",
    sequenceNumberColor: "#08090A",
    classText: "#EDE7DA",
    altBackground: "#0E1113",
    fontSize: "16px",
  },
};

function run(cmd, args) {
  return new Promise((done) => {
    const child = spawn(cmd, args, { shell: true, stdio: ["ignore", "pipe", "pipe"] });
    let err = "";
    child.stderr.on("data", (d) => (err += d));
    child.on("close", (code) => done({ code, err }));
  });
}

await mkdir(OUT, { recursive: true });
const config = resolve(OUT, "mermaid-theme.json");
await writeFile(config, JSON.stringify(THEME, null, 2));

const files = (await readdir(DIAGRAMS)).filter((f) => f.endsWith(".mmd")).sort();
const failed = [];

for (const file of files) {
  const name = basename(file, ".mmd");
  const { code, err } = await run("npx", [
    "-y", "@mermaid-js/mermaid-cli",
    "-i", resolve(DIAGRAMS, file),
    "-o", resolve(OUT, `${name}.png`),
    "-c", config,
    "-b", "#0E1113",
    "-w", "2400",
    "-s", "2",
    "--quiet",
  ]);
  if (code === 0) {
    console.log(`  ok    ${name}.png`);
  } else {
    failed.push({ name, err: err.trim().split("\n").slice(-4).join(" ") });
    console.log(`  FAIL  ${name}`);
  }
}

console.log(`\n${files.length - failed.length}/${files.length} rendered to ${OUT}`);
if (failed.length) {
  for (const f of failed) console.error(`  ${f.name}: ${f.err}`);
  process.exitCode = 1;
}
