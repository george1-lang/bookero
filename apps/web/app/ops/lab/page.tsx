"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, AlgorithmResponse, ApiError } from "@/lib/api";
import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/LoadingState";
import styles from "./page.module.css";

export default function LabPage() {
  const [algorithms, setAlgorithms] = useState<AlgorithmResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [sortBy, setSortBy] = useState<"name" | "revenue" | "duration">("name");

  useEffect(() => {
    const loadAlgorithms = async () => {
      try {
        const data = await api.getAlgorithms();
        setAlgorithms(data);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.detail || "Failed to load algorithms");
      } finally {
        setIsLoading(false);
      }
    };

    loadAlgorithms();
  }, []);

  const sortedAlgorithms = [...algorithms].sort((a, b) => {
    if (sortBy === "revenue") {
      return (b.lastRevenueDelta || 0) - (a.lastRevenueDelta || 0);
    } else if (sortBy === "duration") {
      return (a.lastDurationMs || 0) - (b.lastDurationMs || 0);
    }
    return a.key.localeCompare(b.key);
  });

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <LoadingState label="loading algorithm lab" />
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute requiredRole="ANALYST">
      <div className={styles.container}>
        <div className={styles.header}>
          <h1 className={styles.title}>ALGORITHM LAB</h1>
          <Link href="/ops" className={styles.navLink}>
            Back to Ops
          </Link>
        </div>

        {error && <div className={styles.error}>{error}</div>}

        <div className={styles.controls}>
          <label>
            Sort by:
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
            >
              <option value="name">Name</option>
              <option value="revenue">Revenue Delta</option>
              <option value="duration">Latency</option>
            </select>
          </label>
        </div>

        <div className={styles.algorithmGrid}>
          {sortedAlgorithms.map((algo) => (
            <Link
              href={`/ops/lab/${algo.key}`}
              key={algo.key}
              className={styles.algorithmCard}
            >
              <div className={styles.cardHeader}>
                <div>
                  <div className={styles.algorithmKey}>{algo.key}</div>
                  <div className={styles.algorithmFamily}>{algo.family}</div>
                </div>
                <div className={`${styles.status} ${styles[algo.lastStatus?.toLowerCase() || 'unknown']}`}>
                  {algo.lastStatus || "—"}
                </div>
              </div>

              <p className={styles.description}>{algo.description}</p>

              <div className={styles.metrics}>
                <div className={styles.metric}>
                  <div className={styles.metricLabel}>Latency</div>
                  <div className={styles.metricValue}>
                    {algo.lastDurationMs ? `${algo.lastDurationMs}ms` : "—"}
                  </div>
                </div>
                <div
                  className={`${styles.metric} ${
                    (algo.lastRevenueDelta || 0) >= 0
                      ? styles.positive
                      : styles.negative
                  }`}
                >
                  <div className={styles.metricLabel}>Revenue Δ</div>
                  <div className={styles.metricValue}>
                    {algo.lastRevenueDelta
                      ? `${algo.lastRevenueDelta > 0 ? "+" : ""}$${algo.lastRevenueDelta.toFixed(0)}`
                      : "—"}
                  </div>
                </div>
                <div className={styles.metric}>
                  <div className={styles.metricLabel}>Last Run</div>
                  <div className={styles.metricValue}>
                    {algo.lastRunAt
                      ? new Date(algo.lastRunAt).toLocaleDateString()
                      : "—"}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>

        {sortedAlgorithms.length === 0 && (
          <div className={styles.emptyState}>
            <p>No algorithms available.</p>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
