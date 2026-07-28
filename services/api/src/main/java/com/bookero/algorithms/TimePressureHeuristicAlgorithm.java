package com.bookero.algorithms;

import com.bookero.flight.FlightRepository;
import com.bookero.inventory.InventoryEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Component
public class TimePressureHeuristicAlgorithm implements Algorithm {

  private final FlightRepository flightRepository;

  public TimePressureHeuristicAlgorithm(FlightRepository flightRepository) {
    this.flightRepository = flightRepository;
  }

  @Override
  public String key() {
    return "time_pressure_heuristic";
  }

  @Override
  public String displayName() {
    return "Time Pressure Heuristic";
  }

  @Override
  public String family() {
    return "Heuristic";
  }

  @Override
  public String description() {
    return "Adjust price by formula: multiplier = 1 + a·(1 - d/D)^p + b·loadFactor^q, where d=days to departure, D=total days (30).";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    var fareClasses = ctx.getFareClasses();
    var inventory = ctx.getInventory();
    var priceUpdates = new ArrayList<PriceUpdate>();

    // Formula parameters
    double a = 0.3;  // Time pressure coefficient
    double p = 2.0;  // Time pressure exponent
    double b = 0.2;  // Load factor coefficient
    double q = 1.5;  // Load factor exponent
    int totalDays = 30;

    for (var flightId : ctx.flightIds()) {
      var flight = flightRepository.findById(flightId).orElse(null);
      if (flight == null) continue;

      var fares = fareClasses.get(flightId);
      var inv = inventory.get(flightId);
      if (fares == null || inv == null) continue;

      // Calculate days to departure
      long daysToDeparture = java.time.temporal.ChronoUnit.DAYS.between(Instant.now(), flight.getDepartAt());
      daysToDeparture = Math.max(0, daysToDeparture);

      // Calculate load factor
      double loadFactor = (double) (inv.getSeatsTotal() - inv.getSeatsLeft()) / inv.getSeatsTotal();
      loadFactor = Math.min(1.0, Math.max(0.0, loadFactor));

      // Calculate multiplier
      double timeComponent = a * Math.pow(1.0 - (double) daysToDeparture / totalDays, p);
      double loadComponent = b * Math.pow(loadFactor, q);
      double multiplier = 1.0 + timeComponent + loadComponent;

      // Clamp multiplier
      multiplier = Math.min(2.0, Math.max(0.5, multiplier));

      // Apply to all fares
      for (var fare : fares) {
        BigDecimal newPrice = fare.getBasePrice()
            .multiply(BigDecimal.valueOf(multiplier))
            .setScale(2, RoundingMode.HALF_UP);

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
          newPrice = BigDecimal.ONE;
        }

        if (!fare.getCurrentPrice().equals(newPrice)) {
          priceUpdates.add(new PriceUpdate(
              flightId,
              fare.getFlight().getFlightNo(),
              fare.getCode(),
              fare.getCurrentPrice(),
              newPrice
          ));
        }
      }
    }

    Map<String, Object> metrics = Map.ofEntries(
        Map.entry("faresUpdated", (Object) priceUpdates.size()),
        Map.entry("formula", (Object) "1 + 0.3*(1-d/30)^2 + 0.2*loadFactor^1.5"),
        Map.entry("multiplierBand", (Object) "[0.5, 2.0]")
    );

    return AlgorithmResult.success(0L, BigDecimal.ZERO, priceUpdates, ctx.flightIds().size(), metrics);
  }
}
