package com.bookero.algorithms;

import com.bookero.common.ApiException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AlgorithmRegistry {

  private final Map<String, Algorithm> algorithms;

  public AlgorithmRegistry(List<Algorithm> algorithmList) {
    this.algorithms = algorithmList.stream()
        .collect(Collectors.toMap(Algorithm::key, a -> a));
  }

  public Algorithm get(String key) {
    var algo = algorithms.get(key);
    if (algo == null) {
      throw ApiException.notFound("Algorithm key '" + key + "' is not registered");
    }
    return algo;
  }

  public List<Algorithm> list() {
    return algorithms.values().stream()
        .sorted((a, b) -> a.key().compareTo(b.key()))
        .toList();
  }
}
