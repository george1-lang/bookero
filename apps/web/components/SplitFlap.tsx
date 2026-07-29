"use client";

import { useEffect, useRef, useState } from "react";
import styles from "./SplitFlap.module.css";

interface SplitFlapProps {
  value: number | string;
  /** Minimum cell count; the display is padded on the left to reach it. */
  digits?: number;
  /** Optional unit shown in its own trailing cell, e.g. a currency code. */
  suffix?: string;
  size?: "sm" | "md" | "lg";
}

const FLIP_MS = 260;
const STAGGER_MS = 45;

/**
 * Solari-board numerals. Each cell hinges through 90 degrees, swaps its glyph while
 * edge-on, then hinges back to flat - so a cell always comes to rest upright.
 */
export function SplitFlap({ value, digits = 0, suffix, size = "md" }: SplitFlapProps) {
  const format = (v: number | string) => String(v).padStart(digits, " ");

  const [shown, setShown] = useState(() => format(value));
  const [flipping, setFlipping] = useState(false);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    const next = format(value);
    if (next === shown) return;

    setFlipping(true);
    const swapAt = FLIP_MS / 2 + next.length * STAGGER_MS;
    timers.current.push(setTimeout(() => setShown(next), swapAt));
    timers.current.push(setTimeout(() => setFlipping(false), swapAt + FLIP_MS));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, digits]);

  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  return (
    <span
      className={`${styles.board} ${styles[size]}`}
      role="img"
      aria-label={`${value}${suffix ? ` ${suffix}` : ""}`}
    >
      {shown.split("").map((char, i) => (
        <span
          key={i}
          className={`${styles.cell} ${flipping ? styles.flipping : ""}`}
          style={{ animationDelay: `${i * STAGGER_MS}ms` }}
          aria-hidden="true"
        >
          <span className={styles.glyph}>{char === " " ? " " : char}</span>
          <span className={styles.seam} />
        </span>
      ))}
      {suffix && (
        <span className={`${styles.cell} ${styles.suffix}`} aria-hidden="true">
          <span className={styles.glyph}>{suffix}</span>
          <span className={styles.seam} />
        </span>
      )}
    </span>
  );
}
