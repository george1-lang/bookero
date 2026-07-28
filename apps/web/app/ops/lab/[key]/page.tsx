"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SplitFlap } from "@/components/SplitFlap";
import { api, AlgorithmRunResponse, ApiError } from "@/lib/api";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";
import Markdown from "react-markdown";
import { MermaidBlock } from "@/components/MermaidBlock";
import remarkGfm from "remark-gfm";
import { LoadingState } from "@/components/LoadingState";
import styles from "./page.module.css";

interface AlgoDetail {
  key: string;
  displayName: string;
  description: string;
}

export default function AlgorithmDetailPage({
  params,
}: {
  params: Promise<{ key: string }>;
}) {
  const { key } = use(params);
  const [algorithm, setAlgorithm] = useState<AlgoDetail | null>(null);
  const [docs, setDocs] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [isRunning, setIsRunning] = useState(false);
  const [lastRun, setLastRun] = useState<AlgorithmRunResponse | null>(null);
  const router = useRouter();

  useEffect(() => {
    const loadData = async () => {
      try {
        // Load algorithm metadata
        const algorithms = await api.getAlgorithms();
        const algo = algorithms.find((a) => a.key === key);
        if (!algo) {
          setError("Algorithm not found");
          setIsLoading(false);
          return;
        }

        setAlgorithm({
          key: algo.key,
          displayName: algo.displayName,
          description: algo.description,
        });

        // Try to load documentation
        try {
          const res = await fetch(`/docs/algorithms/${key}.md`);
          if (res.ok) {
            const text = await res.text();
            setDocs(text);
          } else {
            setDocs(
              `# ${algo.displayName}\n\nDocumentation for this algorithm is not yet available.`
            );
          }
        } catch {
          setDocs(
            `# ${algo.displayName}\n\nDocumentation for this algorithm is not yet available.`
          );
        }
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.detail || "Failed to load algorithm");
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, [key]);

  const handleRun = async () => {
    setIsRunning(true);
    try {
      const result = await api.runAlgorithm(key);
      setLastRun(result);
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError.detail || "Failed to run algorithm");
    } finally {
      setIsRunning(false);
    }
  };

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <LoadingState label="loading algorithm" />
        </div>
      </ProtectedRoute>
    );
  }

  if (!algorithm) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <p style={{ color: "var(--stop)" }}>{error}</p>
          <Link href="/ops/lab" className={styles.backLink}>
            ← Back to Lab
          </Link>
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute requiredRole="ANALYST">
      <div className={styles.container}>
        <div className={styles.header}>
          <Link href="/ops/lab" className={styles.backBtn}>
            ← Back
          </Link>
          <h1 className={styles.title}>{algorithm.displayName}</h1>
        </div>

        {error && <div className={styles.error}>{error}</div>}

        <div className={styles.runPanel}>
          <div className={styles.runInfo}>
            <div className={styles.infoLabel}>Key:</div>
            <div className={styles.infoValue}>{algorithm.key}</div>
          </div>
          <button
            onClick={handleRun}
            disabled={isRunning}
            className={styles.runBtn}
          >
            {isRunning ? "RUNNING..." : "RUN ALGORITHM"}
          </button>
        </div>

        {lastRun && (
          <div className={styles.resultPanel}>
            <h2 className={styles.resultTitle}>LAST RUN RESULT</h2>

            <div className={styles.resultMetrics}>
              <div className={styles.resultMetric}>
                <div className={styles.metricLabel}>Status</div>
                <div className={`${styles.metricValue} ${styles[lastRun.status.toLowerCase()]}`}>
                  {lastRun.status}
                </div>
              </div>

              <div className={styles.resultMetric}>
                <div className={styles.metricLabel}>Duration</div>
                <div className={styles.metricValue}>
                  {lastRun.durationMs}ms
                </div>
              </div>

              <div
                className={`${styles.resultMetric} ${
                  lastRun.revenueDelta >= 0
                    ? styles.positive
                    : styles.negative
                }`}
              >
                <div className={styles.metricLabel}>Revenue Δ</div>
                <div className={styles.metricValue}>
                  {lastRun.revenueDelta > 0 ? "+" : ""}$
                  {lastRun.revenueDelta.toFixed(0)}
                </div>
              </div>

              <div className={styles.resultMetric}>
                <div className={styles.metricLabel}>Flights Affected</div>
                <div className={styles.metricValue}>
                  {lastRun.flightsAffected}
                </div>
              </div>
            </div>

            {lastRun.priceUpdates.length > 0 && (
              <div className={styles.priceUpdates}>
                <h3 className={styles.updatesTitle}>Price Updates</h3>
                <table>
                  <thead>
                    <tr>
                      <th>Flight</th>
                      <th>Class</th>
                      <th>Old Price</th>
                      <th>New Price</th>
                      <th>Change</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lastRun.priceUpdates.map((update, idx) => (
                      <tr key={idx}>
                        <td className={styles.mono}>
                          {update.flightNo}
                        </td>
                        <td className={styles.mono}>
                          {update.fareClassCode}
                        </td>
                        <td className={styles.mono}>
                          <SplitFlap
                            value={Math.round(update.oldPrice)}
                            digits={5}
                          />
                        </td>
                        <td className={styles.mono}>
                          <SplitFlap
                            value={Math.round(update.newPrice)}
                            digits={5}
                          />
                        </td>
                        <td
                          className={`${styles.mono} ${
                            update.newPrice > update.oldPrice
                              ? styles.positive
                              : update.newPrice < update.oldPrice
                                ? styles.negative
                                : ""
                          }`}
                        >
                          {update.newPrice > update.oldPrice ? "+" : ""}$
                          {(update.newPrice - update.oldPrice).toFixed(2)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {lastRun.message && (
              <div className={styles.message}>{lastRun.message}</div>
            )}
          </div>
        )}

        <div className={styles.docPanel}>
          <div className={styles.markdown}>
            <Markdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ className, children, ...props }) {
                  if (/language-mermaid/.test(className ?? "")) {
                    return <MermaidBlock chart={String(children).trim()} />;
                  }
                  return (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {docs}
            </Markdown>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
