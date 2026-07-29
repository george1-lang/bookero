package com.bookero.algorithms;

import com.bookero.airport.AirportEntity;
import com.bookero.airport.AirportRepository;
import com.bookero.route.RouteEntity;
import com.bookero.route.RouteRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RouteGraphAlgorithm implements Algorithm {

  private final AirportRepository airportRepository;
  private final RouteRepository routeRepository;

  public RouteGraphAlgorithm(AirportRepository airportRepository, RouteRepository routeRepository) {
    this.airportRepository = airportRepository;
    this.routeRepository = routeRepository;
  }

  @Override
  public String key() {
    return "route_graph";
  }

  @Override
  public String displayName() {
    return "Route Graph Analysis";
  }

  @Override
  public String family() {
    return "Graph";
  }

  @Override
  public String description() {
    return "Build a weighted adjacency structure over airport/route topology. Prices unchanged.";
  }

  @Override
  public AlgorithmResult execute(AlgorithmContext ctx) {
    String carrierHub = ctx.getFlights().values().stream()
        .map(f -> f.getRoute().getOrigin().getCode())
        .findFirst()
        .orElse("n/a");

    var routes = routeRepository.findAll();

    // Build adjacency list (undirected)
    Map<String, Set<String>> adjacency = new HashMap<>();
    Map<String, Map<String, Integer>> distances = new HashMap<>();

    for (var route : routes) {
      var origin = route.getOrigin().getCode();
      var dest = route.getDestination().getCode();
      var distance = route.getDistanceKm() != null ? route.getDistanceKm() : 0;

      adjacency.computeIfAbsent(origin, k -> new HashSet<>()).add(dest);
      adjacency.computeIfAbsent(dest, k -> new HashSet<>()).add(origin);

      distances.computeIfAbsent(origin, k -> new HashMap<>()).put(dest, distance);
      distances.computeIfAbsent(dest, k -> new HashMap<>()).put(origin, distance);
    }

    int nodes = adjacency.size();
    int edges = routes.size();

    // Count connected components using union-find
    var uf = new UnionFind(nodes);
    var nodeToIndex = new HashMap<String, Integer>();
    int idx = 0;
    for (var node : adjacency.keySet()) {
      nodeToIndex.put(node, idx++);
    }

    for (var route : routes) {
      int u = nodeToIndex.get(route.getOrigin().getCode());
      int v = nodeToIndex.get(route.getDestination().getCode());
      uf.union(u, v);
    }

    int components = uf.countComponents();

    // Busiest airport in the reference network, which is not the carrier's own hub.
    String hub = null;
    int maxDegree = 0;
    for (var entry : adjacency.entrySet()) {
      if (entry.getValue().size() > maxDegree) {
        maxDegree = entry.getValue().size();
        hub = entry.getKey();
      }
    }

    double avgDegree = edges > 0 ? (double) (edges * 2) / nodes : 0;

    Map<String, Object> metrics = Map.ofEntries(
        Map.entry("nodes", (Object) nodes),
        Map.entry("edges", (Object) edges),
        Map.entry("components", (Object) components),
        Map.entry("avgDegree", (Object) String.format("%.2f", avgDegree)),
        Map.entry("busiestAirport", (Object) hub),
        Map.entry("busiestAirportDegree", (Object) maxDegree),
        Map.entry("carrierHub", (Object) carrierHub)
    );

    return AlgorithmResult.success(0L, java.math.BigDecimal.ZERO, List.of(), 0, metrics);
  }

  private static class UnionFind {
    int[] parent;
    int[] rank;

    UnionFind(int n) {
      parent = new int[n];
      rank = new int[n];
      for (int i = 0; i < n; i++) {
        parent[i] = i;
      }
    }

    int find(int x) {
      if (parent[x] != x) {
        parent[x] = find(parent[x]);
      }
      return parent[x];
    }

    void union(int x, int y) {
      int px = find(x);
      int py = find(y);
      if (px == py) return;

      if (rank[px] < rank[py]) {
        parent[px] = py;
      } else if (rank[px] > rank[py]) {
        parent[py] = px;
      } else {
        parent[py] = px;
        rank[px]++;
      }
    }

    int countComponents() {
      Set<Integer> roots = new HashSet<>();
      for (int i = 0; i < parent.length; i++) {
        roots.add(find(i));
      }
      return roots.size();
    }
  }
}
