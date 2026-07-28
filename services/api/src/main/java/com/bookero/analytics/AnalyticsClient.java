package com.bookero.analytics;

import com.bookero.common.BookeroProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin client for the Python analytics service. Every call degrades to an empty
 * Optional rather than propagating: the design requires that pricing and booking
 * keep working when analytics is unreachable.
 */
@Component
public class AnalyticsClient {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(8);

  private final RestClient client;

  public AnalyticsClient(BookeroProperties properties, RestClient.Builder builder) {
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) TIMEOUT.toMillis());
    factory.setReadTimeout((int) TIMEOUT.toMillis());
    this.client = builder
        .baseUrl(properties.analyticsBaseUrl())
        .requestFactory(factory)
        .build();
  }

  /** Latest per-flight demand scores in [0,1]. Empty when analytics is down. */
  public Optional<Map<UUID, Double>> demandForecast() {
    return get("/demand/forecast", ForecastResponse.class)
        .map(r -> r.forecasts().stream()
            .filter(f -> f.flightId() != null && f.demandScore() != null)
            .collect(java.util.stream.Collectors.toMap(
                f -> UUID.fromString(f.flightId()),
                f -> Math.clamp(f.demandScore(), 0.0, 1.0),
                (a, b) -> b)));
  }

  /** Raw revenue KPI payload, proxied straight through to the ops dashboard. */
  public Optional<Map<String, Object>> revenueMetrics() {
    @SuppressWarnings("unchecked")
    Optional<Map<String, Object>> body = get("/metrics/revenue", Map.class).map(m -> (Map<String, Object>) m);
    return body;
  }

  public boolean isHealthy() {
    return get("/health", Map.class).isPresent();
  }

  private <T> Optional<T> get(String path, Class<T> type) {
    try {
      return Optional.ofNullable(client.get().uri(path).retrieve().body(type));
    } catch (Exception e) {
      log.warn("analytics {} unavailable: {}", path, e.getMessage());
      return Optional.empty();
    }
  }

  private record ForecastResponse(List<Forecast> forecasts, String model) {
    private ForecastResponse {
      forecasts = forecasts == null ? List.of() : forecasts;
    }
  }

  private record Forecast(String flightId, Double demandScore) {
  }
}
