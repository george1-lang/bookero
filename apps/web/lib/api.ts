export interface ApiError {
  type?: string;
  title: string;
  status: number;
  detail: string;
}

export interface SearchFlightResponse {
  id: string;
  flightNo: string;
  departAt: string;
  origin: { code: string; name: string; city: string };
  dest: { code: string; name: string; city: string };
  distanceKm: number;
  seatsLeft: number;
  seatsTotal: number;
  fareClasses: {
    id: string;
    code: string;
    currentPrice: number;
    basePrice: number;
    seatsAllocated: number;
  }[];
  lowestFare: number;
}

export interface FlightDetailResponse extends SearchFlightResponse {}

export interface BookingRequest {
  flightId: string;
  fareClassId: string;
}

export interface BookingResponse {
  id: string;
  flightNo: string;
  fareClassCode: string;
  paidPrice: number;
  createdAt: string;
}

export interface BookingHistoryResponse {
  id: string;
  flightNo: string;
  origin: string;
  dest: string;
  departAt: string;
  fareClassCode: string;
  paidPrice: number;
  createdAt: string;
}

export interface AlgorithmResponse {
  key: string;
  displayName: string;
  family: string;
  description: string;
  lastDurationMs: number;
  lastRevenueDelta: number;
  lastStatus: string;
  lastRunAt: string;
}

export interface AlgorithmRunResponse {
  runId: string;
  algorithmKey: string;
  status: string;
  durationMs: number;
  revenueDelta: number;
  flightsAffected: number;
  priceUpdates: {
    flightId: string;
    flightNo: string;
    fareClassCode: string;
    oldPrice: number;
    newPrice: number;
  }[];
  message: string;
  metrics?: Record<string, unknown>;
}

export interface SeedResponse {
  flights: number;
  fareClasses: number;
  routes: number;
}

export interface SimulateResponse {
  demandSnapshots: number;
  syntheticBookings: number;
  durationMs: number;
}

export interface InventoryResponse {
  flightId: string;
  flightNo: string;
  origin: string;
  dest: string;
  departAt: string;
  seatsTotal: number;
  seatsLeft: number;
  loadFactor: number;
  fareClasses: {
    code: string;
    currentPrice: number;
    seatsAllocated: number;
  }[];
}

export interface MetricsResponse {
  /** False when the analytics service was unreachable and the payload is zeroed. */
  available?: boolean;
  totalRevenue: number;
  baselineRevenue: number;
  revenueDelta: number;
  revenueDeltaPct: number;
  loadFactor: number;
  avgFare: number;
  seatsSold: number;
  seatsTotal: number;
  bookingCount: number;
  revenueByDay: { date: string; revenue: number; bookings: number }[];
  byRoute: { route: string; revenue: number; bookings: number }[];
  generatedAt: string;
}

function getApiUrl(): string {
  return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8090";
}

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("token");
}

async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${getApiUrl()}${path}`;
  const token = getToken();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...options,
    headers: { ...headers, ...(options.headers || {}) } as HeadersInit,
  });

  if (res.status === 401) {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("token");
      sessionStorage.removeItem("user");
      window.location.href = "/login";
    }
    throw new Error("Unauthorized");
  }

  let data;
  try {
    data = await res.json();
  } catch {
    data = null;
  }

  if (!res.ok) {
    const error = data as ApiError;
    throw {
      status: res.status,
      title: error?.title || "Error",
      detail: error?.detail || "An error occurred",
    } as ApiError;
  }

  return data as T;
}

export const api = {
  async searchFlights(
    origin: string,
    dest: string,
    date: string
  ): Promise<SearchFlightResponse[]> {
    const params = new URLSearchParams({
      origin,
      dest,
      date,
    });
    return apiFetch(`/api/flights/search?${params}`);
  },

  async getFlightDetail(id: string): Promise<FlightDetailResponse> {
    return apiFetch(`/api/flights/${id}`);
  },

  async getBookings(): Promise<BookingHistoryResponse[]> {
    return apiFetch("/api/bookings/me");
  },

  async createBooking(
    flightId: string,
    fareClassId: string
  ): Promise<BookingResponse> {
    return apiFetch("/api/bookings", {
      method: "POST",
      body: JSON.stringify({ flightId, fareClassId }),
    });
  },

  async getAlgorithms(): Promise<AlgorithmResponse[]> {
    return apiFetch("/api/algorithms");
  },

  async runAlgorithm(key: string): Promise<AlgorithmRunResponse> {
    return apiFetch(`/api/algorithms/${key}/run`, {
      method: "POST",
    });
  },

  async getOpsInventory(): Promise<InventoryResponse[]> {
    return apiFetch("/api/ops/inventory");
  },

  async seedNetwork(): Promise<SeedResponse> {
    return apiFetch("/api/simulate/seed", { method: "POST" });
  },

  async simulate(intensity: number): Promise<SimulateResponse> {
    return apiFetch("/api/simulate", {
      method: "POST",
      body: JSON.stringify({ intensity }),
    });
  },

  async reprice(algorithmKey: string, flightIds?: string[]): Promise<AlgorithmRunResponse> {
    return apiFetch("/api/pricing/reprice", {
      method: "POST",
      body: JSON.stringify({ algorithmKey, flightIds }),
    });
  },

  async getAlgorithmRuns(): Promise<AlgorithmRunResponse[]> {
    return apiFetch("/api/algorithms/runs");
  },

  // Routed through the API rather than straight at analytics: the proxy keeps the
  // browser same-origin and returns a zeroed payload with available:false when the
  // analytics service is down, so the dashboard degrades instead of failing.
  async getMetrics(): Promise<MetricsResponse> {
    return apiFetch("/api/ops/metrics");
  },
};
