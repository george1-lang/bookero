# Bookero deliverables

Everything an evaluator needs, in one place. Each artefact here is generated from the
repository, so nothing is hand-maintained and nothing can drift from the code.

---

## What is in here

| Path | What it is |
|---|---|
| `Bookero-Technical-Report.docx` | Phase 6 technical report, with every diagram embedded as an image and a figure appendix |
| `diagrams/*.mmd` | Mermaid source for every diagram in `docs/`, one file per diagram |
| `diagrams/INDEX.md` | Which diagram came from which document and section |
| `diagrams/png/*.png` | Every diagram rendered at 2400 px wide |
| `diagrams/png/bookero-use-case.png` | UML use case diagram with actors and system boundary, rendered from PlantUML |
| `screenshots/*.png` | The seven screens an evaluator will be shown, captured from the running app |

---

## Regenerating everything

All commands run from the repository root. Source the toolchain first.

```bash
source scripts/env.sh
```

### 1. Diagrams

```bash
node scripts/extract-diagrams.mjs    # docs/**.md  ->  deliverables/diagrams/*.mmd
node scripts/render-diagrams.mjs     # *.mmd       ->  deliverables/diagrams/png/*.png
```

`render-diagrams.mjs` doubles as a syntax check. It exits non-zero if any diagram fails
to render, which is the same failure an evaluator would see in the Algorithm Lab.

The UML use case diagram is PlantUML rather than Mermaid, because Mermaid has no use
case diagram type and cannot draw actors:

```bash
java -jar "$PLANTUML_JAR" -tpng -Playout=smetana -Sdpi=160 \
  -o "$(pwd -W)/deliverables/diagrams/png" docs/uml/use-case.puml
```

### 2. Screenshots

The stack must be running.

```bash
./scripts/stack.sh up
node e2e/capture-screenshots.mjs
```

### 3. The report

```bash
node scripts/build-report-docx.mjs
```

This renders the report's own Mermaid blocks to PNG, swaps the code blocks for those
images, appends the figure appendix, and converts to `.docx` with pandoc.

---

## Turning a diagram into a standalone image

The PNGs are already rendered. If you want a different size or format:

```bash
# PNG at a specific width
npx -y @mermaid-js/mermaid-cli \
  -i deliverables/diagrams/<name>.mmd -o <name>.png -w 3000 -b "#0E1113"

# SVG, which scales without loss and is better for print
npx -y @mermaid-js/mermaid-cli \
  -i deliverables/diagrams/<name>.mmd -o <name>.svg -b transparent
```

See `diagrams/INDEX.md` for the mapping from file name back to the source document.

---

## Phase artefacts, and where they live

| Phase | Deliverable | Path |
|---|---|---|
| 1. Problem framing | Problem and computational thinking pillars | `docs/01-problem-framing.md` |
| 2. System design | Architecture, use cases, ER, sequences, class diagram | `docs/02-system-design.md` |
| 3. App development | Working system | `apps/web`, `services/api`, `docker-compose.yml` |
| 4. Data pipeline | ETL, demand model, revenue metrics | `services/analytics`, `docs/algorithms/*.md` |
| 5. Evaluation | Measured results and user testing | `docs/05-evaluation.md`, `docs/user-testing.md` |
| 6. Documentation | Technical report | `docs/06-technical-report.md`, `Bookero-Technical-Report.docx` |
| Demo | 5 to 7 minute walkthrough | `scripts/demo-walkthrough.md` |
| Deployment | Free-tier hosting guide | `docs/deploy-stretch.md` |

Raw measurements behind every number in the evaluation and the report:

- `data/processed/benchmark.json`: per-algorithm latency, median of 3 runs
- `data/processed/experiment-w3-w7.json`: revenue A/B at low starting load
- `data/processed/experiment-w7-w9.json`: revenue A/B at high starting load

Regenerate them with `node scripts/benchmark.mjs` and `node scripts/experiment.mjs`
against a running stack.

---

## Demo credentials

| Role | Email | Password |
|---|---|---|
| Revenue analyst | `analyst@bookero.local` | `password` |
| Traveller | `traveler@bookero.local` | `password` |

These are deliberately open for the demo. `docs/deploy-stretch.md` section 5 lists what
must change before the system is exposed publicly.
