package com.bookero.algorithms;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/algorithms")
@PreAuthorize("hasRole('ANALYST')")
public class AlgorithmController {

  private final AlgorithmRegistry algorithmRegistry;
  private final AlgorithmRunService algorithmRunService;
  private final AlgorithmRunRepository algorithmRunRepository;

  public AlgorithmController(
      AlgorithmRegistry algorithmRegistry,
      AlgorithmRunService algorithmRunService,
      AlgorithmRunRepository algorithmRunRepository
  ) {
    this.algorithmRegistry = algorithmRegistry;
    this.algorithmRunService = algorithmRunService;
    this.algorithmRunRepository = algorithmRunRepository;
  }

  @GetMapping
  public ResponseEntity<List<AlgorithmDto>> listAlgorithms() {
    var dtos = algorithmRegistry.list().stream()
        .map(algo -> {
          var latest = algorithmRunRepository.findLatestByKey(algo.key());
          return new AlgorithmDto(
              algo.key(),
              algo.displayName(),
              algo.family(),
              algo.description(),
              latest.map(AlgorithmRunEntity::getDurationMs).orElse(null),
              latest.map(AlgorithmRunEntity::getRevenueDelta).orElse(null),
              latest.map(AlgorithmRunEntity::getStatus).orElse(null),
              latest.map(AlgorithmRunEntity::getCreatedAt).orElse(null)
          );
        })
        .toList();
    return ResponseEntity.ok(dtos);
  }

  @PostMapping("/{key}/run")
  public ResponseEntity<Map<String, Object>> runAlgorithm(
      @PathVariable String key,
      @RequestBody(required = false) Map<String, Object> body
  ) {
    var flightIds = extractFlightIds(body);
    var run = algorithmRunService.execute(key, flightIds);
    return ResponseEntity.ok(buildRunResponse(run));
  }

  @GetMapping("/runs")
  public ResponseEntity<List<Map<String, Object>>> listRuns(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size
  ) {
    var pageable = PageRequest.of(page, size);
    var pageResult = algorithmRunRepository.findAllByOrderByCreatedAtDesc(pageable);
    var responses = pageResult.stream()
        .map(this::buildRunResponse)
        .toList();
    return ResponseEntity.ok(responses);
  }

  private List<UUID> extractFlightIds(Map<String, Object> body) {
    if (body == null || body.get("flightIds") == null) {
      return null;
    }
    var flightIds = body.get("flightIds");
    if (flightIds instanceof List<?> list) {
      return list.stream()
          .map(id -> {
            if (id instanceof String str) {
              return UUID.fromString(str);
            }
            return (UUID) id;
          })
          .toList();
    }
    return null;
  }

  private Map<String, Object> buildRunResponse(AlgorithmRunEntity run) {
    return Map.ofEntries(
        Map.entry("runId", run.getId()),
        Map.entry("algorithmKey", run.getAlgorithmKey()),
        Map.entry("status", run.getStatus()),
        Map.entry("durationMs", run.getDurationMs()),
        Map.entry("revenueDelta", run.getRevenueDelta()),
        Map.entry("flightsAffected", 0),
        Map.entry("priceUpdates", List.of()),
        Map.entry("metrics", Map.of()),
        Map.entry("message", null)
    );
  }
}
