package com.bookero.algorithms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AlgorithmRegistryTest {

  @Autowired
  private AlgorithmRegistry registry;

  @Test
  void testAllTenAlgorithmsRegistered() {
    var algorithms = registry.list();
    assertThat(algorithms).hasSize(10);
    assertThat(algorithms.stream().map(Algorithm::key))
        .containsExactlyInAnyOrder(
            "baseline",
            "route_graph",
            "shortest_path",
            "flight_search",
            "slot_schedule",
            "greedy_protection",
            "dp_seat_protect",
            "revenue_optimize",
            "time_pressure_heuristic",
            "demand_ml"
        );
  }

  @Test
  void testGetByKey() {
    var algo = registry.get("baseline");
    assertThat(algo).isNotNull();
    assertThat(algo.key()).isEqualTo("baseline");
  }

  @Test
  void testGetByKeyNotFound() {
    assertThatThrownBy(() -> registry.get("unknown"))
        .isInstanceOf(Exception.class);
  }
}
