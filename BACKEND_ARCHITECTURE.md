# M-AgriLink Backend Architecture — 12-Layer High-Availability Topology

This document describes the reference network and service topology for the
M-AgriLink platform. Each layer states its responsibility, its failure
handling, and the contract it exposes to the layer above. All background
processing notes use inclusive, neutral wording so they read consistently
in logs regardless of who operates them.

---

## 1. DNS Layer

- Configure `https://magrilink.com` routing with distributed multi-region
  failover rules.
- Health-checked records steer callers away from degraded regions before
  they observe errors, and TTL values stay short enough that recovery
  propagates within minutes.
- Zone changes roll out behind versioned change tickets so they can be
  reverted atomically if monitors report anomalies.

## 2. CDN Layer

- Cache static assets (agricultural dictionaries, image templates,
  localized string bundles) at East African telecom hub edge nodes.
- Cache keys include content hash and locale so callers always receive
  the bundle matching their language selection.
- Origin shielding limits thundering-herd reloads; stale-while-revalidate
  keeps edge responses fast while fresh copies propagate.

## 3. Load Balancer Layer

- Distribute inbound cluster traffic across healthy application nodes
  using weighted least-connection policies.
- Active health probes remove failing nodes from rotation automatically
  and restore them only after consecutive passing checks.
- Connection draining on scale-in lets in-flight requests finish before
  a node is withdrawn.

## 4. Reverse Proxy Layer

- Terminate SSL/TLS at an Nginx gateway at the perimeter so internal
  services communicate over a controlled trust boundary.
- Enforce request size limits, rate caps per caller, and header hygiene
  before traffic reaches application pools.
- Access logs feed anomaly detection; error responses carry correlation
  IDs so incidents can be traced across layers.

## 5. App Server Layer

- Serve traffic from asynchronous worker pools (Kotlin Ktor
  configuration) sized to absorb multi-modal image bitstream uploads
  without blocking interactive API calls.
- Isolate heavy vision workloads onto dedicated dispatchers so routine
  reads keep steady latency even during upload bursts.
- Graceful shutdown hooks drain running coroutines before the process
  exits.

## 6. Middleware Layer

- Run interceptor chains that authenticate, validate, and normalize
  data packets before they reach internal logic pipelines.
- Reject malformed or unauthorized packets at the boundary with typed
  error codes; never let unvalidated input flow downstream.
- Interceptors remain stateless so any node can evaluate any packet and
  horizontal scaling stays linear.

## 7. Business Logic Layer

- Enforce strict crop validation rules and conversational-intent guards
  so crop fields accept plant names only and advisory requests route to
  the assistant pipeline.
- Bind real-time Open-Meteo climate data maps to county selections,
  with cached fallbacks when upstream forecasts are unreachable.
- Keep advisory matrices (KALRO, CABI Plantwise reference data)
  versioned so scoring changes are reviewable and reversible.

## 8. ORM Layer

- Map abstract domain entities to persistence models so business code
  never embeds raw SQL query strings.
- Centralize schema migrations with forward-only, tested scripts;
  each migration declares its rollback companion.
- Unit-of-work boundaries keep multi-table writes atomic from the
  caller's perspective.

## 9. Database Layer

- Enforce transaction durability (write-ahead logging, fsync discipline)
  for commodity pricing registries and farmer profile records.
- Apply a master-slave isolation model: writes land on the master while
  analytical and read-heavy workloads fan out to replicas.
- Scheduled integrity checks and point-in-time backups bound the
  recovery window for any single-node failure.

## 10. File System Layer

- Isolate storage into dedicated paths for audio readouts, leaf image
  previews, and operational log directories with explicit quotas.
- Serve user-visible media through signed, expiring references rather
  than direct filesystem paths.
- Retention jobs prune expired artifacts so disk growth stays
  predictable under field usage patterns.

## 11. Operating System Layer

- Run services inside a hardened Linux environment with minimal
  privileges, read-only mounts where possible, and seccomp-style
  syscall restrictions on runtime threads.
- Pin resource controls (CPU shares, memory ceilings, file descriptor
  caps) per service so one tier cannot starve its neighbors.
- Ship structured logs to central aggregation; hosts retain only a
  bounded local buffer.

## 12. Network Layer

- Transport API traffic over local cellular nodes with retry budgets
  and idempotency keys tuned for intermittent rural connectivity.
- Segment internal traffic (control plane, data plane, replication)
  so congestion in one class does not degrade the others.
- Encrypt traffic in transit end to end and rotate credentials on a
  fixed schedule with emergency rotation runbooks.

---

## Cross-Cutting Notes

- Observability: every layer emits latency, error-rate, and saturation
  gauges under a shared naming scheme so dashboards compose cleanly.
- Deploys: changes advance one layer at a time behind feature flags
  with automated rollback on SLO breach.
- Data posture: the platform mirrors public agricultural indices for
  informational display; it processes no payments and holds no escrow.
