"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SplitFlap } from "@/components/SplitFlap";
import { api } from "@/lib/api";
import Link from "next/link";
import { useState } from "react";
import styles from "./page.module.css";

interface Flight {
  id: string;
  flightNo: string;
  departAt: string;
  origin: { code: string; name: string };
  dest: { code: string; name: string };
  lowestFare: number;
  seatsLeft: number;
  seatsTotal: number;
}

export default function SearchPage() {
  const [origin, setOrigin] = useState("");
  const [dest, setDest] = useState("");
  const [date, setDate] = useState("");
  const [flights, setFlights] = useState<Flight[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [hasSearched, setHasSearched] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    setHasSearched(true);
    try {
      const results = await api.searchFlights(origin, dest, date);
      setFlights(results);
    } catch (err: unknown) {
      const error = err as { detail?: string };
      setError(error.detail || "Search failed");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ProtectedRoute requiredRole="TRAVELER">
      <div className={styles.container}>
        <div className={styles.header}>
          <h1 className={styles.title}>FLIGHT SEARCH</h1>
          <p className={styles.subtitle}>Book your next journey</p>
        </div>

        <form onSubmit={handleSearch} className={styles.searchForm}>
          <div className={styles.formGrid}>
            <div className={styles.field}>
              <label htmlFor="origin">Origin</label>
              <input
                id="origin"
                type="text"
                placeholder="e.g., ACC, JFK"
                value={origin}
                onChange={(e) => setOrigin(e.target.value.toUpperCase())}
                required
              />
            </div>
            <div className={styles.field}>
              <label htmlFor="dest">Destination</label>
              <input
                id="dest"
                type="text"
                placeholder="e.g., LHR, ORD"
                value={dest}
                onChange={(e) => setDest(e.target.value.toUpperCase())}
                required
              />
            </div>
            <div className={styles.field}>
              <label htmlFor="date">Departure Date</label>
              <input
                id="date"
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                required
              />
            </div>
          </div>
          <button type="submit" disabled={isLoading} className={styles.submit}>
            {isLoading ? "SEARCHING..." : "SEARCH FLIGHTS"}
          </button>
        </form>

        {error && <div className={styles.error}>{error}</div>}

        <div className={styles.results}>
          {hasSearched && flights.length === 0 && !isLoading && (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>✈</div>
              <p>No flights found for your search.</p>
              <p className={styles.emptyHint}>Try different dates or routes.</p>
            </div>
          )}

          {flights.map((flight) => (
            <Link
              href={`/flights/${flight.id}`}
              key={flight.id}
              className={styles.ticket}
            >
              <div className={styles.ticketBody}>
                <div className={styles.route}>
                  <div className={styles.airport}>
                    <div className={styles.code}>{flight.origin.code}</div>
                    <div className={styles.name}>{flight.origin.name}</div>
                  </div>
                  <div className={styles.path}>
                    <div className={styles.pathLine} />
                    <div className={styles.plane}>✈</div>
                  </div>
                  <div className={styles.airport}>
                    <div className={styles.code}>{flight.dest.code}</div>
                    <div className={styles.name}>{flight.dest.name}</div>
                  </div>
                </div>

                <div className={styles.details}>
                  <div className={styles.flightNo}>
                    <span className={styles.label}>Flight</span>
                    <span className={styles.mono}>{flight.flightNo}</span>
                  </div>
                  <div className={styles.time}>
                    <span className={styles.label}>Departs</span>
                    <span className={styles.mono}>
                      {new Date(flight.departAt).toLocaleString()}
                    </span>
                  </div>
                  <div className={styles.seats}>
                    <span className={styles.label}>Seats</span>
                    <span className={styles.mono}>
                      {flight.seatsLeft}/{flight.seatsTotal}
                    </span>
                  </div>
                </div>
              </div>

              <div className={styles.stub}>
                <div className={styles.stubPrice}>
                  <div className={styles.stubLabel}>LOWEST</div>
                  <div className={styles.priceAmount}>
                    <SplitFlap value={Math.round(flight.lowestFare)} digits={6} />
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </ProtectedRoute>
  );
}
