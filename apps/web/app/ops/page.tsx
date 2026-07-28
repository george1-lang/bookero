"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SplitFlap } from "@/components/SplitFlap";
import { api, ApiError, InventoryResponse } from "@/lib/api";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { LoadingState } from "@/components/LoadingState";
import { OpsControls } from "@/components/OpsControls";
import styles from "./page.module.css";

export default function OpsPage() {
  const [inventory, setInventory] = useState<InventoryResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadInventory = useCallback(async () => {
    try {
      const data = await api.getOpsInventory();
      setInventory(data);
      setError("");
    } catch (err) {
      setError((err as ApiError).detail || "Failed to load inventory");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadInventory();
  }, [loadInventory]);

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="ANALYST">
        <div className={styles.container}>
          <LoadingState label="loading inventory" />
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute requiredRole="ANALYST">
      <div className={styles.container}>
        <div className={styles.header}>
          <h1 className={styles.title}>OPS INVENTORY</h1>
          <div className={styles.navLinks}>
            <Link href="/ops/dashboard" className={styles.navLink}>
              Dashboard
            </Link>
            <Link href="/ops/lab" className={styles.navLink}>
              Algorithm Lab
            </Link>
          </div>
        </div>

        <OpsControls onChanged={loadInventory} />

        {error && <div className={styles.error}>{error}</div>}

        <div className={styles.inventoryTable}>
          <table>
            <thead>
              <tr>
                <th>Flight</th>
                <th>Route</th>
                <th>Departure</th>
                <th>Seats</th>
                <th>Load Factor</th>
                <th>Fare Classes</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map((inv) => (
                <tr key={inv.flightId}>
                  <td>
                    <span className={styles.flightNo}>{inv.flightNo}</span>
                  </td>
                  <td>
                    <span className={styles.route}>
                      {inv.origin} → {inv.dest}
                    </span>
                  </td>
                  <td>
                    <span className={styles.mono}>
                      {new Date(inv.departAt).toLocaleString()}
                    </span>
                  </td>
                  <td>
                    <span className={styles.mono}>
                      {inv.seatsLeft}/{inv.seatsTotal}
                    </span>
                  </td>
                  <td>
                    <div className={styles.loadFactorBar}>
                      <div
                        className={styles.loadFactorFill}
                        style={{
                          width: `${Math.min(inv.loadFactor * 100, 100)}%`,
                        }}
                      />
                      <span className={styles.loadFactorLabel}>
                        {(inv.loadFactor * 100).toFixed(0)}%
                      </span>
                    </div>
                  </td>
                  <td>
                    <div className={styles.fareClasses}>
                      {inv.fareClasses.map((fc) => (
                        <div key={fc.code} className={styles.fareClass}>
                          <span className={styles.code}>{fc.code}</span>
                          <span className={styles.price}>
                            ${fc.currentPrice.toFixed(0)}
                          </span>
                        </div>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {inventory.length === 0 && (
          <div className={styles.emptyState}>
            <p>No flights in inventory. Seed flights to begin.</p>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
