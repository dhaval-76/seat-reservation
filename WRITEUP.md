# Write-Up: Seat Reservation at Scale

## Atomic Decision

The "who gets the seat" decision is pushed into a single atomic step using **PostgreSQL `SELECT ... FOR UPDATE` with deterministic ordering**.

When a reservation request arrives, the service:
1. Acquires a transaction-scoped **advisory lock** (`pg_advisory_xact_lock`) keyed on `(show_id, user_id)` — this serializes all requests from the same user for the same show.
2. Locks the requested seat rows with `SELECT ... FOR UPDATE ORDER BY label ASC` — the deterministic order prevents deadlocks when multiple users request overlapping multi-seat sets.
3. Checks each locked seat's `status` column. If any seat is not `AVAILABLE`, the entire request is rejected (all-or-nothing). Because the rows are locked, no concurrent transaction can change them until this transaction commits or rolls back.

**Why this is race-free:** The `FOR UPDATE` lock is the atomic gate. Two concurrent requests for the same seat will serialize at the row lock — the second blocks until the first commits. When the second proceeds, it sees the seat as `CONFIRMED` and returns 409. There is no read-then-write gap because the read *is* the lock acquisition.

**Multi-seat deadlock avoidance:** Seats are always locked in alphabetical label order (`ORDER BY label ASC`). If user A requests `[A3, A1]` and user B requests `[A1, A3]`, both will lock A1 first, then A3. This consistent ordering eliminates circular wait, the necessary condition for deadlock.

## Idempotency

- **Storage:** The idempotency key is stored in the `reservations` table column `idempotency_key` with a `UNIQUE` constraint (`uq_reservation_idempotency`).
- **Exactly-once enforcement:** Before attempting the reservation, the service checks `findByIdempotencyKey()`. If a matching reservation exists with the same show, user, and seats, it returns the existing reservation (201). This is the fast-path replay.
- **Same-key-different-body:** If the existing reservation's seats differ from the request, a 409 is returned — the client is trying to reserve different seats with a previously used key.
- **Concurrent duplicate protection:** If two concurrent requests with the same idempotency key both pass the application-level check (TOCTOU window), the database unique constraint prevents the second insert. The resulting `DataIntegrityViolationException` is caught and mapped to 409.

## Holds & Expiry

The system uses **immediate confirmation with time-limited expiry**:

- A successful reservation is immediately `CONFIRMED` with an `expires_at` timestamp (default 300 seconds).
- A background scheduler (`HoldExpiryScheduler`, running every 30 seconds) finds confirmed reservations past their expiry time, releases their seats back to `AVAILABLE`, and marks the reservation as `EXPIRED`.
- In multi-instance deployments, **ShedLock** (backed by a `shedlock` table) ensures only one instance runs the expiry task at a time.
- Users can also explicitly cancel via `POST /reservations/{id}/cancel`. Only the owner (matched by `user_id` from the auth token) can cancel.
- A cancelled/expired seat becomes cleanly re-bookable. The release logic checks `held_by = userId` before releasing, so it never accidentally releases a seat that has since been reserved by someone else.

## Consistency vs. Availability Under a Partition

This system is **CP** (consistency over availability). The single PostgreSQL database is the source of truth, and all concurrency control (row locks, advisory locks, unique constraints) depends on it.

If the database becomes unreachable:
- The readiness probe (`/actuator/health/readiness`) fails, removing the instance from the load balancer.
- All reservation requests fail with 409 ("Server busy, please retry") rather than returning stale or inconsistent data.
- No seat can be double-sold because the system refuses to operate without its consistency guarantees.

This is the correct trade-off for a seat reservation system. Double-selling is worse than downtime — you can retry a failed booking, but you can't un-sell a seat.

## Observability

**Metrics** (Prometheus at `/actuator/prometheus`):
- `booking_reservations_confirmed_total` — counter of successful reservations
- `booking_reservations_declined_total{reason=seat_unavailable|user_limit|idempotent_replay}` — counter per decline reason
- `booking_seats_available` — gauge of total available seats
- `booking_reservation_duration_seconds` — timer for reservation processing time

**Logs:**
- Structured JSON format with `request_id` for correlation
- Every request gets a `X-Request-Id` header (client-provided or auto-generated UUID)
- Application events (`ReservationCreated`, `Declined`, `Cancelled`, `Expired`) are logged asynchronously

**What I'd get paged for at 2am:**
1. `booking_seats_available` dropping to 0 unexpectedly (oversold or stuck holds)
2. `booking_reservations_declined_total{reason=seat_unavailable}` spike without a corresponding show going on-sale (possible bug in release logic)
3. `booking_reservation_duration_seconds` p99 exceeding 1s (lock contention or DB issues)
4. Readiness probe failing (DB connectivity lost)
5. 5xx rate > 0 (should never happen — all business declines are 4xx)

## AI Usage

AI tools (Claude) were used as an implementation accelerator. The split was deliberate: I owned all design and architectural decisions; AI handled mechanical output.

**What I did (design, architecture, trade-off analysis):**
- Chose pessimistic locking (`SELECT FOR UPDATE`) over optimistic locking — evaluated both under the "500 users, one hot seat" scenario; pessimistic gives deterministic serialization with no retry storms, which matters more here than throughput
- Identified the TOCTOU race in the per-user limit check (count query outside the lock scope) and designed the fix: `pg_advisory_xact_lock(showId, hashtext(userId))` to serialize same-user requests without blocking other users
- Decided on deterministic lock ordering (seats sorted by label ASC) to eliminate deadlock in multi-seat reservations — understood that circular wait is the breakable condition
- Chose the consistency model: immediate CONFIRMED with time-limited auto-expiry, rather than a HELD → CONFIRMED two-phase flow — simpler, matches the spec's response format, and the confirm step can be added later with a payment integration
- Designed the idempotency approach: application-level check for the fast path, database unique constraint as the concurrency safety net, and the same-key-different-body detection logic
- Decided server-authoritative pricing (price on Show, amount computed server-side) — never trust client-supplied money values
- Error classification decision: all contention outcomes (lock timeout, pool exhaustion) map to 4xx, never 5xx — the spec's "zero 5xx" bar drove this, but it's also the right domain model (a failed booking attempt is a business outcome, not a server error)
- Chose CP over AP for the consistency/availability trade-off — double-selling is worse than downtime for a seat reservation system
- Selected the observability signals: what to meter, what to gauge, what to alert on at 2am

**What AI did (implementation, boilerplate, repetitive work):**
- Generated the Spring Boot project scaffolding — POM dependencies, application.yml, Dockerfile, docker-compose
- Wrote entity classes, DTOs, repository interfaces, and controller wiring from my specifications
- Implemented the strategy pattern classes (`AllOrNothingStrategy`, `BestEffortStrategy`) from my interface design
- Generated Flyway migration SQL from my schema design
- Built the Prometheus metrics registration boilerplate (`BookingMetrics` class)
- Wrote the exception handler mappings (`GlobalExceptionHandler`) — I specified which exceptions map to which HTTP status codes
- Created the bash test scripts (`burst-test.sh`, `full-test.sh`) — I defined the test cases and assertions; AI generated the curl/bash implementation
- Generated the Spring Security filter chain configuration from my auth design (Bearer token → userId extraction)
- Wrote the event classes and async listener — boilerplate Spring event plumbing
- Produced the ShedLock configuration and scheduler class from my requirements
- Handled the Jackson `@JsonNaming` annotations and Spring Boot 4.x package migration (`tools.jackson.*`)

## What I'd Do Next

1. **Confirm/pay endpoint** — Add `POST /reservations/{id}/confirm` with a payment integration. Currently reservations are immediately confirmed; a real system would hold → confirm on payment.
2. **Optimistic concurrency for reads** — The `GET /shows/{id}` endpoint does a full seat scan. Add a version column and ETag headers for cache-friendly polling.
3. **Rate limiting** — Per-IP and per-user rate limits to protect against abusive burst traffic.
4. **Connection pool tuning** — Profile under 20K concurrent load and tune HikariCP pool size, PostgreSQL `max_connections`, and consider PgBouncer for connection pooling at scale.
5. **Horizontal scaling** — The advisory lock approach is tied to a single PostgreSQL instance. For multi-region, consider Redis-based distributed locks or a queue-based reservation system.
6. **Automated integration tests** — Replace bash scripts with JUnit + Testcontainers for CI-friendly testing.
7. **Waitlist** — When a seat is taken, offer to waitlist the user and notify them if the seat is released.
