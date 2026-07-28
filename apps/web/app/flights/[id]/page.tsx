"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SplitFlap } from "@/components/SplitFlap";
import { api, ApiError } from "@/lib/api";
import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";
import { LoadingState } from "@/components/LoadingState";
import styles from "./page.module.css";

interface FareClass {
  id: string;
  code: string;
  currentPrice: number;
  basePrice: number;
  seatsAllocated: number;
}

interface FlightDetail {
  id: string;
  flightNo: string;
  departAt: string;
  origin: { code: string; name: string; city: string };
  dest: { code: string; name: string; city: string };
  distanceKm: number;
  seatsLeft: number;
  seatsTotal: number;
  fareClasses: FareClass[];
  lowestFare: number;
}

export default function FlightDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [flight, setFlight] = useState<FlightDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [bookingFareId, setBookingFareId] = useState<string | null>(null);
  const [isBooking, setIsBooking] = useState(false);
  const [bookingError, setBookingError] = useState("");
  const router = useRouter();

  useEffect(() => {
    const loadFlight = async () => {
      try {
        const data = await api.getFlightDetail(id);
        setFlight(data);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.detail || "Failed to load flight");
      } finally {
        setIsLoading(false);
      }
    };

    loadFlight();
  }, [id]);

  const handleBook = async (fareClassId: string) => {
    setBookingFareId(fareClassId);
    setBookingError("");
    setIsBooking(true);
    try {
      await api.createBooking(id, fareClassId);
      router.push("/bookings");
    } catch (err: unknown) {
      const apiError = err as ApiError;
      if (apiError.status === 409) {
        setBookingError("This class is sold out. Try another.");
      } else {
        setBookingError(apiError.detail || "Booking failed");
      }
    } finally {
      setIsBooking(false);
      setBookingFareId(null);
    }
  };

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="TRAVELER">
        <div className={styles.container}>
          <LoadingState label="loading flight" />
        </div>
      </ProtectedRoute>
    );
  }

  if (!flight) {
    return (
      <ProtectedRoute requiredRole="TRAVELER">
        <div className={styles.container}>
          <p style={{ color: "var(--stop)" }}>{error}</p>
        </div>
      </ProtectedRoute>
    );
  }

  const loadFactor = (
    ((flight.seatsTotal - flight.seatsLeft) / flight.seatsTotal) *
    100
  ).toFixed(0);

  return (
    <ProtectedRoute requiredRole="TRAVELER">
      <div className={styles.container}>
        <div className={styles.header}>
          <button onClick={() => router.back()} className={styles.backBtn}>
            ← Back
          </button>
          <h1 className={styles.flightNo}>{flight.flightNo}</h1>
        </div>

        <div className={styles.banner}>
          <div className={styles.routeSection}>
            <div className={styles.airport}>
              <div className={styles.code}>{flight.origin.code}</div>
              <div className={styles.name}>{flight.origin.name}</div>
              <div className={styles.city}>{flight.origin.city}</div>
            </div>
            <div className={styles.pathSection}>
              <div className={styles.time}>
                {new Date(flight.departAt).toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </div>
              <div className={styles.pathDots}>... ✈ ...</div>
              <div className={styles.distance}>{flight.distanceKm} km</div>
            </div>
            <div className={styles.airport}>
              <div className={styles.code}>{flight.dest.code}</div>
              <div className={styles.name}>{flight.dest.name}</div>
              <div className={styles.city}>{flight.dest.city}</div>
            </div>
          </div>

          <div className={styles.stats}>
            <div className={styles.stat}>
              <div className={styles.statLabel}>Date</div>
              <div className={styles.statValue}>
                {new Date(flight.departAt).toLocaleDateString()}
              </div>
            </div>
            <div className={styles.stat}>
              <div className={styles.statLabel}>Load Factor</div>
              <div className={styles.statValue}>{loadFactor}%</div>
            </div>
            <div className={styles.stat}>
              <div className={styles.statLabel}>Seats Available</div>
              <div className={styles.statValue}>
                {flight.seatsLeft}/{flight.seatsTotal}
              </div>
            </div>
          </div>
        </div>

        {bookingError && <div className={styles.error}>{bookingError}</div>}

        <div className={styles.fareClasses}>
          <h2 className={styles.fareTitle}>FARE CLASSES</h2>
          <div className={styles.fareGrid}>
            {flight.fareClasses.map((fc) => (
              <div key={fc.id} className={styles.fareCard}>
                <div className={styles.fareHeader}>
                  <div className={styles.fareCode}>{fc.code}</div>
                  <div className={styles.seatsInfo}>
                    {fc.seatsAllocated} seats
                  </div>
                </div>

                <div className={styles.priceSection}>
                  <div className={styles.priceLabel}>Current Price</div>
                  <div className={styles.splitFlapContainer}>
                    <SplitFlap
                      value={Math.round(fc.currentPrice)}
                      digits={6}
                    />
                  </div>
                  <div className={styles.basePrice}>
                    Base: ${fc.basePrice.toFixed(2)}
                  </div>

                  {fc.currentPrice > fc.basePrice && (
                    <div className={styles.delta}>
                      <span className={styles.deltaUp}>
                        ↑ +${(fc.currentPrice - fc.basePrice).toFixed(2)}
                      </span>
                    </div>
                  )}
                  {fc.currentPrice < fc.basePrice && (
                    <div className={styles.delta}>
                      <span className={styles.deltaDown}>
                        ↓ -${(fc.basePrice - fc.currentPrice).toFixed(2)}
                      </span>
                    </div>
                  )}
                </div>

                <button
                  onClick={() => handleBook(fc.id)}
                  disabled={isBooking || flight.seatsLeft === 0}
                  className={styles.bookBtn}
                >
                  {isBooking && bookingFareId === fc.id
                    ? "BOOKING..."
                    : "BOOK THIS CLASS"}
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
