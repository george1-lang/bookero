"use client";

import { useEffect, useState } from "react";
import styles from "./SplitFlap.module.css";

interface SplitFlapProps {
  value: number | string;
  digits?: number;
}

export function SplitFlap({ value, digits = 5 }: SplitFlapProps) {
  const [displayValue, setDisplayValue] = useState<string>(
    String(value).padStart(digits, " ")
  );
  const [isFlipping, setIsFlipping] = useState(false);

  useEffect(() => {
    const newValue = String(value).padStart(digits, " ");
    if (newValue !== displayValue) {
      setIsFlipping(true);
      setTimeout(() => {
        setDisplayValue(newValue);
        setIsFlipping(false);
      }, 300);
    }
  }, [value, displayValue, digits]);

  return (
    <div className={styles.splitFlap}>
      {displayValue.split("").map((char, idx) => (
        <div
          key={idx}
          className={`${styles.cell} ${isFlipping ? styles.flipping : ""}`}
          style={{
            animationDelay: isFlipping ? `${idx * 20}ms` : "0ms",
          }}
        >
          <div className={styles.face}>{char}</div>
          <div className={styles.seam} />
        </div>
      ))}
    </div>
  );
}
