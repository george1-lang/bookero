package com.bookero.algorithms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BaselineAlgorithmTest {

  @Autowired
  private BaselineAlgorithm algorithm;

  @Test
  void testKey() {
    assertThat(algorithm.key()).isEqualTo("baseline");
  }

  @Test
  void testFamily() {
    assertThat(algorithm.family()).isEqualTo("Control");
  }

  @Test
  void testDescription() {
    assertThat(algorithm.description())
        .contains("base price");
  }
}
