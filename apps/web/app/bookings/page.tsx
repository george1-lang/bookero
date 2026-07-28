"use client";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { api, ApiError } from "@/lib/api";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/LoadingState";
import styles from "./page.module.css";

interface Booking {
  id: string;
  flightNo: string;
  origin: string;
  dest: string;
  departAt: string;
  fareClassCode: string;
  paidPrice: number;
  createdAt: string;
}

export default function BookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const router = useRouter();

  useEffect(() => {
    const loadBookings = async () => {
      try {
        const data = await api.getBookings();
        setBookings(data);
      } catch (err) {
        const apiError = err as ApiError;
        setError(apiError.detail || "Failed to load bookings");
      } finally {
        setIsLoading(false);
      }
    };

    loadBookings();
  }, []);

  if (isLoading) {
    return (
      <ProtectedRoute requiredRole="TRAVELER">
        <div className={styles.container}>
          <LoadingState label="loading bookings" />
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute requiredRole="TRAVELER">
      <div className={styles.container}>
        <div className={styles.header}>
          <button onClick={() => router.back()} className={styles.backBtn}>
            ← Back
          </button>
          <h1 className={styles.title}>YOUR BOOKINGS</h1>
        </div>

        {error && <div className={styles.error}>{error}</div>}

        <div className={styles.bookingsList}>
          {bookings.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>🎫</div>
              <p>No bookings yet.</p>
              <button
                onClick={() => router.push("/")}
                className={styles.searchLink}
              >
                Search and book a flight
              </button>
            </div>
          ) : (
            bookings.map((booking) => (
              <div key={booking.id} className={styles.boardingPass}>
                <div className={styles.passTop}>
                  <div className={styles.airline}>BOOKERO</div>
                  <div className={styles.passNumber}>
                    Booking #{booking.id.substring(0, 8)}
                  </div>
                </div>

                <div className={styles.route}>
                  <div className={styles.airport}>
                    <div className={styles.code}>{booking.origin}</div>
                    <div className={styles.name}>Departure</div>
                  </div>
                  <div className={styles.path}>
                    <div className={styles.pathLine} />
                    <div className={styles.plane}>✈</div>
                  </div>
                  <div className={styles.airport}>
                    <div className={styles.code}>{booking.dest}</div>
                    <div className={styles.name}>Arrival</div>
                  </div>
                </div>

                <div className={styles.details}>
                  <div className={styles.detail}>
                    <div className={styles.label}>Flight</div>
                    <div className={styles.mono}>{booking.flightNo}</div>
                  </div>
                  <div className={styles.detail}>
                    <div className={styles.label}>Class</div>
                    <div className={styles.mono}>{booking.fareClassCode}</div>
                  </div>
                  <div className={styles.detail}>
                    <div className={styles.label}>Departs</div>
                    <div className={styles.mono}>
                      {new Date(booking.departAt).toLocaleDateString()}
                    </div>
                  </div>
                  <div className={styles.detail}>
                    <div className={styles.label}>Booked</div>
                    <div className={styles.mono}>
                      {new Date(booking.createdAt).toLocaleDateString()}
                    </div>
                  </div>
                </div>

                <div className={styles.passStub}>
                  <div className={styles.stubPrice}>
                    <div className={styles.stubLabel}>PAID</div>
                    <div className={styles.priceAmount}>
                      ${booking.paidPrice.toFixed(2)}
                    </div>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
