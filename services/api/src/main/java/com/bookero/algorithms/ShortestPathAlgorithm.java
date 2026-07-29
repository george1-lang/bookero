package com.bookero.algorithms;

import com.bookero.airport.AirportRepository;
import com.bookero.route.RouteRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ShortestPathAlgorithm implements Algorithm {

  private final RouteRepository routeRepository;
  private final AirportRepository airportRepository;

  public ShortestPathAlgorithm(RouteRepository routeRepository, AirportRepository airportRepository) {
    this.routeRepository = routeRepository;
    this.airportRepository = airportRepository;
  }

  @Override
  public String key() {
    return "shortest_path";
  }

  @Override
  public String displayName() {
    return "Shortest Path (Dijkstra)";
  }

  @Override
  public String family() {
    return "Shortest path";
  }

  @Override
  public String description() {
    return "Dijkstra's algorithm with binary heap to find shortest paths from origin to all reachable destinations.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    // Anchored on the carrier's own hub, not an arbitrary row from the airport table.
    var hub = ctx.getFlights().values().stream()
        .map(f -> f.getRoute().getOrigin().getCode())
        .findFirst()
        .orElseGet(() -> routeRepository.findBusiestOrigin().orElse(null));

    if (hub == null) {
      return AlgorithmResult.success(0L, java.math.BigDecimal.ZERO, List.of(), 0,
          Map.of("visitedNodes", 0, "relaxedEdges", 0, "reachableAirports", 0,
              "note", "No routes loaded; run the analytics ETL first."));
    }

    var routes = routeRepository.findAll();
    var graph = buildGraph(routes);

    var result = dijkstra(graph, hub);

    var finite = result.distances().values().stream()
        .filter(d -> d < Integer.MAX_VALUE)
        .toList();
    int reachable = finite.size();
    long farthest = finite.stream().mapToInt(Integer::intValue).max().orElse(0);
    double meanKm = finite.stream().mapToInt(Integer::intValue).average().orElse(0.0);

    Map<String, Object> metrics = Map.ofEntries(
        Map.entry("originHub", (Object) hub),
        Map.entry("visitedNodes", (Object) result.visitedCount()),
        Map.entry("relaxedEdges", (Object) result.relaxedCount()),
        Map.entry("reachableAirports", (Object) reachable),
        Map.entry("farthestReachableKm", (Object) farthest),
        Map.entry("meanShortestPathKm", (Object) Math.round(meanKm))
    );

    return AlgorithmResult.success(0L, java.math.BigDecimal.ZERO, List.of(), 0, metrics);
  }

  private Map<String, Map<String, Integer>> buildGraph(java.util.List<com.bookero.route.RouteEntity> routes) {
    Map<String, Map<String, Integer>> graph = new HashMap<>();

    for (var route : routes) {
      var origin = route.getOrigin().getCode();
      var dest = route.getDestination().getCode();
      var distance = route.getDistanceKm() != null ? route.getDistanceKm() : 1;

      graph.computeIfAbsent(origin, k -> new HashMap<>()).put(dest, distance);
      graph.computeIfAbsent(dest, k -> new HashMap<>()).put(origin, distance);
    }

    return graph;
  }

  private record DijkstraResult(Map<String, Integer> distances, int visitedCount, int relaxedCount) {}

  private DijkstraResult dijkstra(Map<String, Map<String, Integer>> graph, String start) {
    Map<String, Integer> distances = new HashMap<>();
    Set<String> visited = new HashSet<>();
    PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(distances::get));

    // Initialize
    for (var node : graph.keySet()) {
      distances.put(node, Integer.MAX_VALUE);
    }
    distances.put(start, 0);
    pq.offer(start);

    int relaxedEdges = 0;

    while (!pq.isEmpty()) {
      var current = pq.poll();
      if (visited.contains(current)) continue;
      visited.add(current);

      var neighbors = graph.get(current);
      if (neighbors != null) {
        for (var entry : neighbors.entrySet()) {
          var neighbor = entry.getKey();
          var weight = entry.getValue();

          int newDist = distances.get(current) + weight;
          if (newDist < distances.get(neighbor)) {
            distances.put(neighbor, newDist);
            pq.offer(neighbor);
            relaxedEdges++;
          }
        }
      }
    }

    return new DijkstraResult(distances, visited.size(), relaxedEdges);
  }
}
