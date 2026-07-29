"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, ApiError, MetricsResponse } from "@/lib/api";
import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/LoadingState";
import styles from "./page.module.css";

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<MetricsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadMetrics = async () => {
      try {
        const data = await api.getMetrics();
        setMetrics(data);
        if (data.available === false) {
          setError("Analytics offline - figures below are not live.");
        }
      } catch {
        setError("Analytics service unavailable. Showing last known data.");
      } finally {
        setIsLoading(false);
      }
    };

    loadMetrics();
  }, []);

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <LoadingState label="loading revenue metrics" />
        </div>
      </ProtectedRoute>
    );
  }

  if (!metrics) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <div className={styles.offlineBanner}>
            <span>ANALYTICS OFFLINE</span> - showing last known data
          </div>

          <div className={styles.header}>
            <h1 className={styles.title}>REVENUE DASHBOARD</h1>
            <Link href="/ops" className={styles.navLink}>
              Back to Ops
            </Link>
          </div>
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute requiredRole="ANALYST">
      <div className={styles.container}>
        {error && <div className={styles.offlineBanner}>{error}</div>}

        <div className={styles.header}>
          <h1 className={styles.title}>REVENUE DASHBOARD</h1>
          <Link href="/ops" className={styles.navLink}>
            Back to Ops
          </Link>
        </div>

        <div className={styles.kpiRow}>
          <div className={styles.kpiCard}>
            <div className={styles.kpiLabel}>Total Revenue</div>
            <div className={styles.kpiValue}>
              ${metrics.totalRevenue.toFixed(0)}
            </div>
          </div>

          <div className={styles.kpiCard}>
            <div className={styles.kpiLabel}>Baseline Revenue</div>
            <div className={styles.kpiValue}>
              ${metrics.baselineRevenue.toFixed(0)}
            </div>
          </div>

          <div
            className={`${styles.kpiCard} ${
              metrics.revenueDelta >= 0 ? styles.positive : styles.negative
            }`}
          >
            <div className={styles.kpiLabel}>Revenue Delta</div>
            <div className={styles.kpiValue}>
              ${metrics.revenueDelta.toFixed(0)}
            </div>
            <div className={styles.kpiPercent}>
              {metrics.revenueDeltaPct > 0 ? "+" : ""}
              {metrics.revenueDeltaPct.toFixed(1)}%
            </div>
          </div>

          <div className={styles.kpiCard}>
            <div className={styles.kpiLabel}>Load Factor</div>
            <div className={styles.kpiValue}>
              {(metrics.loadFactor * 100).toFixed(0)}%
            </div>
          </div>

          <div className={styles.kpiCard}>
            <div className={styles.kpiLabel}>Avg Fare</div>
            <div className={styles.kpiValue}>
              ${metrics.avgFare.toFixed(2)}
            </div>
          </div>

          <div className={styles.kpiCard}>
            <div className={styles.kpiLabel}>Seats Sold</div>
            <div className={styles.kpiValue}>
              {metrics.seatsSold}/{metrics.seatsTotal}
            </div>
          </div>
        </div>

        <div className={styles.chartSection}>
          <h2 className={styles.chartTitle}>REVENUE BY DAY</h2>
          <div className={styles.chartContainer}>
            <svg
              width="100%"
              height="200"
              viewBox="0 0 800 200"
              className={styles.chart}
            >
              <defs>
                <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--amber)" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="var(--amber)" stopOpacity="0" />
                </linearGradient>
              </defs>

              {metrics.revenueByDay.length > 0 && (
                <>
                  <polyline
                    points={metrics.revenueByDay
                      .map((d, i) => {
                        const x =
                          (i / (metrics.revenueByDay.length - 1 || 1)) * 750 +
                          25;
                        const maxRev = Math.max(
                          ...metrics.revenueByDay.map((d) => d.revenue)
                        );
                        const y = 150 - (d.revenue / maxRev) * 120;
                        return `${x},${y}`;
                      })
                      .join(" ")}
                    fill="none"
                    stroke="var(--amber)"
                    strokeWidth="2"
                  />

                  {metrics.revenueByDay.map((d, i) => {
                    const x =
                      (i / (metrics.revenueByDay.length - 1 || 1)) * 750 + 25;
                    const maxRev = Math.max(
                      ...metrics.revenueByDay.map((d) => d.revenue)
                    );
                    const y = 150 - (d.revenue / maxRev) * 120;
                    return (
                      <circle
                        key={i}
                        cx={x}
                        cy={y}
                        r="4"
                        fill="var(--amber)"
                      />
                    );
                  })}
                </>
              )}

              <line x1="25" y1="150" x2="775" y2="150" stroke="var(--line)" />
            </svg>
          </div>
        </div>

        <div className={styles.routeBreakdown}>
          <h2 className={styles.sectionTitle}>REVENUE BY ROUTE</h2>
          <table>
            <thead>
              <tr>
                <th>Route</th>
                <th>Revenue</th>
                <th>Bookings</th>
              </tr>
            </thead>
            <tbody>
              {metrics.byRoute.map((r) => (
                <tr key={r.route}>
                  <td>{r.route}</td>
                  <td className={styles.mono}>
                    ${r.revenue.toFixed(0)}
                  </td>
                  <td className={styles.mono}>{r.bookings}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className={styles.footer}>
          <small>Last updated: {new Date(metrics.generatedAt).toLocaleString()}</small>
        </div>
      </div>
    </ProtectedRoute>
  );
}
