"use client";

import { useEffect, useId, useRef, useState } from "react";
import styles from "./MermaidBlock.module.css";

/**
 * Mermaid ships a large parser bundle and touches the DOM, so it is imported
 * lazily on first render rather than at module load.
 */
export function MermaidBlock({ chart }: { chart: string }) {
  const host = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const id = useId().replace(/:/g, "");

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "strict",
          fontFamily: "var(--font-mono), monospace",
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
            edgeLabelBackground: "#08090A",
            fontSize: "13px",
          },
        });
        const { svg } = await mermaid.render(`mmd-${id}`, chart);
        if (!cancelled && host.current) host.current.innerHTML = svg;
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "diagram failed to render");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [chart, id]);

  if (error) {
    return (
      <figure className={styles.wrap}>
        <figcaption className={styles.caption}>diagram unavailable</figcaption>
        <pre className={styles.fallback}>{chart}</pre>
      </figure>
    );
  }

  return (
    <figure className={styles.wrap}>
      <figcaption className={styles.caption}>fig — flowchart</figcaption>
      <div ref={host} className={styles.canvas} role="img" aria-label="Algorithm flowchart" />
    </figure>
  );
}
