"use client";

import { api, AlgorithmResponse, ApiError } from "@/lib/api";
import { useEffect, useState } from "react";
import styles from "./OpsControls.module.css";

type Status = { tone: "ok" | "bad"; text: string } | null;

export function OpsControls({ onChanged }: { onChanged: () => void }) {
  const [algorithms, setAlgorithms] = useState<AlgorithmResponse[]>([]);
  const [algorithmKey, setAlgorithmKey] = useState("dp_seat_protect");
  const [intensity, setIntensity] = useState(5);
  const [busy, setBusy] = useState<string | null>(null);
  const [status, setStatus] = useState<Status>(null);

  useEffect(() => {
    api.getAlgorithms().then(setAlgorithms).catch(() => setAlgorithms([]));
  }, []);

  const run = async (name: string, action: () => Promise<string>) => {
    setBusy(name);
    setStatus(null);
    try {
      setStatus({ tone: "ok", text: await action() });
      onChanged();
    } catch (err) {
      setStatus({ tone: "bad", text: (err as ApiError).detail ?? `${name} failed` });
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className={styles.deck} aria-label="Operations controls">
      <div className={styles.rule}>
        <span className={styles.ruleLabel}>control deck</span>
      </div>

      <div className={styles.row}>
        <div className={styles.cell}>
          <span className={styles.cellLabel}>network</span>
          <button
            type="button"
            className={styles.action}
            disabled={busy !== null}
            onClick={() =>
              run("seed", async () => {
                const r = await api.seedNetwork();
                return `${r.flights} flights · ${r.fareClasses} fare classes · ${r.routes} routes`;
              })
            }
          >
            {busy === "seed" ? "seeding…" : "seed network"}
          </button>
        </div>

        <div className={styles.cell}>
          <span className={styles.cellLabel}>
            demand intensity <b className={styles.value}>{intensity}</b>
          </span>
          <div className={styles.sliderRow}>
            <input
              type="range"
              min={1}
              max={10}
              step={1}
              value={intensity}
              className={styles.slider}
              aria-label="Demand intensity"
              onChange={(e) => setIntensity(Number(e.target.value))}
            />
            <button
              type="button"
              className={styles.action}
              disabled={busy !== null}
              onClick={() =>
                run("simulate", async () => {
                  const r = await api.simulate(intensity);
                  return `${r.demandSnapshots} snapshots · ${r.syntheticBookings} synthetic bookings · ${r.durationMs} ms`;
                })
              }
            >
              {busy === "simulate" ? "simulating…" : "run simulation"}
            </button>
          </div>
        </div>

        <div className={styles.cell}>
          <span className={styles.cellLabel}>reprice with</span>
          <div className={styles.sliderRow}>
            <select
              className={styles.select}
              value={algorithmKey}
              aria-label="Pricing algorithm"
              onChange={(e) => setAlgorithmKey(e.target.value)}
            >
              {algorithms.map((a) => (
                <option key={a.key} value={a.key}>
                  {a.displayName}
                </option>
              ))}
            </select>
            <button
              type="button"
              className={`${styles.action} ${styles.primary}`}
              disabled={busy !== null || algorithms.length === 0}
              onClick={() =>
                run("reprice", async () => {
                  const r = await api.reprice(algorithmKey);
                  const delta = Number(r.revenueDelta ?? 0);
                  return `${r.algorithmKey} · ${r.durationMs} ms · ${r.priceUpdates?.length ?? 0} fares moved · Δ ${delta >= 0 ? "+" : ""}${delta.toFixed(2)}`;
                })
              }
            >
              {busy === "reprice" ? "repricing…" : "reprice"}
            </button>
          </div>
        </div>
      </div>

      {status && (
        <p className={status.tone === "ok" ? styles.ok : styles.bad} role="status">
          {status.text}
        </p>
      )}
    </section>
  );
}
