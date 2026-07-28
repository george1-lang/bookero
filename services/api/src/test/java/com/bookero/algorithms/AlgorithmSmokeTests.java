package com.bookero.algorithms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AlgorithmSmokeTests {

  @Autowired private RouteGraphAlgorithm routeGraph;
  @Autowired private ShortestPathAlgorithm shortestPath;
  @Autowired private FlightSearchAlgorithm flightSearch;
  @Autowired private SlotScheduleAlgorithm slotSchedule;
  @Autowired private GreedyProtectionAlgorithm greedyProtection;
  @Autowired private DpSeatProtectAlgorithm dpSeatProtect;
  @Autowired private RevenueOptimizeAlgorithm revenueOptimize;
  @Autowired private TimePressureHeuristicAlgorithm timePressure;
  @Autowired private DemandMlAlgorithm demandMl;

  @Test
  void testAllAlgorithmsInstantiated() {
    assertThat(routeGraph).isNotNull();
    assertThat(shortestPath).isNotNull();
    assertThat(flightSearch).isNotNull();
    assertThat(slotSchedule).isNotNull();
    assertThat(greedyProtection).isNotNull();
    assertThat(dpSeatProtect).isNotNull();
    assertThat(revenueOptimize).isNotNull();
    assertThat(timePressure).isNotNull();
    assertThat(demandMl).isNotNull();
  }

  @Test
  void testAlgorithmKeys() {
    assertThat(routeGraph.key()).isEqualTo("route_graph");
    assertThat(shortestPath.key()).isEqualTo("shortest_path");
    assertThat(flightSearch.key()).isEqualTo("flight_search");
    assertThat(slotSchedule.key()).isEqualTo("slot_schedule");
    assertThat(greedyProtection.key()).isEqualTo("greedy_protection");
    assertThat(dpSeatProtect.key()).isEqualTo("dp_seat_protect");
    assertThat(revenueOptimize.key()).isEqualTo("revenue_optimize");
    assertThat(timePressure.key()).isEqualTo("time_pressure_heuristic");
    assertThat(demandMl.key()).isEqualTo("demand_ml");
  }

  @Test
  void testAlgorithmFamilies() {
    assertThat(routeGraph.family()).isEqualTo("Graph");
    assertThat(shortestPath.family()).isEqualTo("Shortest path");
    assertThat(flightSearch.family()).isEqualTo("Search");
    assertThat(slotSchedule.family()).isEqualTo("Scheduling");
    assertThat(greedyProtection.family()).isEqualTo("Greedy");
    assertThat(dpSeatProtect.family()).isEqualTo("Dynamic programming");
    assertThat(revenueOptimize.family()).isEqualTo("Optimization");
    assertThat(timePressure.family()).isEqualTo("Heuristic");
    assertThat(demandMl.family()).isEqualTo("Machine learning");
  }
}
