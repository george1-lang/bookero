package com.bookero.algorithms;

import org.springframework.data.domain.PageRequest;
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
  public ResponseEntity<AlgorithmRunResponse> runAlgorithm(
      @PathVariable String key,
      @RequestBody(required = false) Map<String, Object> body
  ) {
    return ResponseEntity.ok(algorithmRunService.execute(key, extractFlightIds(body)));
  }

  @GetMapping("/runs")
  public ResponseEntity<List<AlgorithmRunResponse>> listRuns(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size
  ) {
    var runs = algorithmRunRepository
        .findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200)))
        .stream()
        .map(AlgorithmRunResponse::ofHistory)
        .toList();
    return ResponseEntity.ok(runs);
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


}
