# Algorithm: `shortest_path`

**Family:** Shortest path  
**Purpose:** Find shortest paths from a hub to all reachable airports using Dijkstra's algorithm.

## Purpose

Route planning and flight connectivity analysis require shortest-path computation. Dijkstra finds the minimum-distance path from a hub (e.g., largest airport) to every reachable destination. Use case: identify which destinations are 1 hop, 2 hops, etc., from the hub.

## Inputs / Outputs

| Item | Type |
|------|------|
| **Input** | Route graph (distance-weighted edges) |
| **Output** | `metrics.visitedNodes` | Nodes explored |
| **Output** | `metrics.relaxedEdges` | Edges relaxed |
| **Output** | `metrics.pathLength` | Count of reachable nodes |
| **Output** | `metrics.originHub` | Starting airport code |

## Pseudocode

```
FUNCTION dijkstra_execute(context):
  graph := build_graph_from_routes()
  hub := first_airport()  // or highest-degree
  
  distances := {}
  for each node in graph:
    distances[node] := INF
  distances[hub] := 0
  
  pq := PriorityQueue()  // binary heap
  pq.push((0, hub))
  
  visited := {}
  relaxedEdges := 0
  
  while pq not empty:
    (d, u) := pq.pop()
    if u in visited: continue
    visited.add(u)
    
    for each (v, weight) in graph[u]:
      if distances[u] + weight < distances[v]:
        distances[v] := distances[u] + weight
        pq.push((distances[v], v))
        relaxedEdges += 1
  
  reachable := count { d : d[d] < INF }
  
  RETURN {
    status: "SUCCESS",
    metrics: {
      visitedNodes: len(visited),
      relaxedEdges,
      pathLength: reachable,
      originHub: hub
    }
  }
```

## Mermaid Flowchart

```mermaid
flowchart TD
  Start([Dijkstra]) --> Build["Build distance-weighted graph"]
  Build --> Init["Initialize distances to INF except source=0"]
  Init --> EnqueueHub["Enqueue hub with distance 0"]
  EnqueueHub --> Loop["While queue not empty"]
  Loop --> Pop["Pop min-distance node u"]
  Pop --> Visited{"u visited?"}
  Visited -->|Yes| Loop
  Visited -->|No| MarkVisited["Mark u visited"]
  MarkVisited --> ForEdges["For each edge (u,v,w)"]
  ForEdges --> Relax{"d[u]+w < d[v]?"}
  Relax -->|Yes| UpdateDist["Update d[v]; enqueue v"]
  UpdateDist --> IncrementRelax["relaxedEdges++"]
  Relax -->|No| ForEdges
  IncrementRelax --> ForEdges
  ForEdges --> Loop
  Loop --> Return["Return metrics"]
  Return --> End([Done])
```

## Complexity Analysis

- **Time:** O((N + E) log N) where N = nodes, E = edges. Binary heap operations dominate.
- **Space:** O(N) for distances and visited set.

## Design Rationale

Dijkstra is the gold-standard shortest-path algorithm for non-negative weights (distances are non-negative). Binary heap ensures O(log N) per operation. Alternatives (Bellman-Ford, Floyd-Warshall) are slower for single-source queries.

## Implementation

**Class:** `com.bookero.algorithms.ShortestPathAlgorithm`

## Tests

**Test class:** `AlgorithmSmokeTests`  
**Assertions:** Algorithm instantiates; key and family correct.

## Performance Results

| Metric | Benchmark (ms) | Notes |
|--------|---:|---|
| Duration (median) | 188 | Dijkstra's algorithm on full graph; O((N+E)logN) with binary heap |
| Duration range | 181-246 ms | Variance due to graph connectivity and node distances |
| Origin hub | ACC | Starting point for shortest-path computation |
| Nodes visited | 3,231 | Airports reachable from ACC (breadth of reachability) |
| Edges relaxed | 4,842 | Edge-relaxation operations in Dijkstra's main loop |
| Reachable airports | 3,231 | Same as nodes visited (full reachability from hub) |
| Mean shortest path (km) | 9,644 | Average distance to reachable destinations |
| Farthest reachable (km) | 20,428 | Max distance airport reachable via shortest path from ACC |
| Fares moved | 0 | This is an analysis algorithm; does not reprice |
| Revenue delta | N/A | Not applicable (routing analysis, not pricing) |

**Note:** `shortest_path` (Dijkstra) is an offline analysis tool. Latency (188 ms) is acceptable for periodic reachability analysis but not for real-time pricing. Algorithm enables network-aware features: e.g., inform travelers of fastest connection options or identify hub-dependent routes for capacity planning. In production, might cache results and update once daily or on schedule changes.
