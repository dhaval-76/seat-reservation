# Seat Reservation System

Concurrent seat reservation API with PostgreSQL row-level locking, advisory locks for per-user serialization, idempotent reservations, and distributed scheduling.

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 4.1.1 |
| PostgreSQL | 16 |
| Flyway | managed by Spring Boot |
| ShedLock | 5.16.0 |
| Micrometer / Prometheus | managed by Spring Boot |

## Quick Start

```bash
docker compose up -d --build
```

This starts PostgreSQL and the application on `http://localhost:8080`. Flyway runs migrations automatically.

To tear down (including data):

```bash
docker compose down -v
```

### Local Development

```bash
# start postgres
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=booking \
  -e POSTGRES_USER=booking \
  -e POSTGRES_PASSWORD=booking \
  postgres:16-alpine

# run the app
./mvnw spring-boot:run
```

## API

All request and response fields use **snake_case** (e.g. `idempotency_key`, `price_paise`, `amount_paise`).

### Authentication

Protected endpoints require a `Bearer` token in the format `Bearer user-<id>`. The user ID is extracted from the token — identity is always token-derived, never from the request body.

```
Authorization: Bearer user-alice
```

**Why only some endpoints require auth:** Show creation (`POST /shows`) and viewing (`GET /shows/{id}`) are public — anyone can browse available shows. Reserving and cancelling seats require authentication because these actions are tied to a specific user (enforcing per-user limits, ownership checks, etc.).

### Endpoints

#### Create Show

```bash
curl -s -X POST http://localhost:8080/shows \
  -H "Content-Type: application/json" \
  -d '{"name": "Hamilton", "seats": ["A1", "A2", "A3", "B1", "B2"], "price_paise": 25000}'
```

Response `201`:

```json
{
  "id": 1,
  "name": "Hamilton",
  "price_paise": 25000,
  "seats": [
    { "label": "A1", "status": "available" },
    { "label": "A2", "status": "available" }
  ],
  "summary": { "total": 5, "available": 5, "held": 0, "confirmed": 0 }
}
```

#### Get Show

```bash
curl -s http://localhost:8080/shows/1
```

Response `200`:

```json
{
  "id": 1,
  "name": "Hamilton",
  "price_paise": 25000,
  "seats": [
    { "label": "A1", "status": "confirmed" },
    { "label": "A2", "status": "confirmed" },
    { "label": "A3", "status": "available" },
    { "label": "B1", "status": "available" },
    { "label": "B2", "status": "available" }
  ],
  "summary": { "total": 5, "available": 3, "held": 0, "confirmed": 2 }
}
```

#### Reserve Seats

```bash
curl -s -X POST http://localhost:8080/shows/1/reserve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer user-alice" \
  -d '{"seats": ["A1", "A2"], "idempotency_key": "uuid-123"}'
```

Response `201`:

```json
{
  "reservation_id": 1,
  "show_id": 1,
  "user_id": "user-alice",
  "status": "confirmed",
  "seats": ["A1", "A2"],
  "amount_paise": 50000,
  "created_at": "2026-10-04T07:00:00Z",
  "expires_at": "2026-10-04T07:05:00Z"
}
```

The `amount_paise` is computed server-side: `price_paise × number_of_seats`.

| Status Code | Meaning |
|-------------|---------|
| 201 | Reservation created (or idempotent retry) |
| 401 | Missing auth token |
| 404 | Show or seat not found |
| 409 | Seat unavailable, user limit exceeded, or idempotency conflict |

#### Cancel Reservation

```bash
curl -s -X POST http://localhost:8080/reservations/1/cancel \
  -H "Authorization: Bearer user-alice"
```

Response `200`:

```json
{
  "reservation_id": 1,
  "show_id": 1,
  "user_id": "user-alice",
  "status": "cancelled",
  "seats": ["A1", "A2"],
  "amount_paise": 50000,
  "created_at": "2026-10-04T07:00:00Z",
  "expires_at": "2026-10-04T07:05:00Z"
}
```

## Configuration

All properties can be overridden via environment variables.

| Property | Default | Description |
|----------|---------|-------------|
| `booking.reservation-strategy` | `all-or-nothing` | `all-or-nothing` or `best-effort` |
| `booking.hold-duration-seconds` | `300` | How long a reservation lasts before auto-expiry |
| `booking.hold-cleanup-interval-ms` | `30000` | Scheduler poll interval |
| `booking.max-seats-per-user-per-show` | `4` | Per-user seat cap |

### Reservation Strategies

- **all-or-nothing** — all requested seats must be available or the entire request fails (409).
- **best-effort** — reserves whichever requested seats are available; fails only if none are.

## Architecture

### Concurrency

Two-level locking ensures correctness under contention:

1. **Advisory lock** (`pg_advisory_xact_lock`) keyed on `(show_id, user_id)` — serializes all requests from the same user for the same show, preventing the per-user limit TOCTOU race.
2. **Row-level lock** (`SELECT ... FOR UPDATE`) on seat rows, ordered by label to prevent deadlocks — ensures no double-sell.

Idempotency keys are enforced by a `UNIQUE` constraint. A duplicate key with the same seats returns the original reservation (201). A duplicate key with different seats returns 409.

### Hold Expiry

A scheduled task polls for confirmed reservations past their `expires_at` timestamp and releases their seats. In multi-instance deployments, ShedLock ensures only one instance runs the task at a time using a database-backed lock (`shedlock` table).

### Events

Side-effects (logging) are decoupled from the transaction via Spring application events:

- `ReservationCreatedEvent`
- `ReservationCancelledEvent`
- `ReservationExpiredEvent`
- `ReservationDeclinedEvent`

Events are processed asynchronously by `ReservationEventListener`.

### Metrics

Available at `/actuator/prometheus`:

| Metric | Type | Description |
|--------|------|-------------|
| `booking_reservations_confirmed_total` | counter | Successful reservations |
| `booking_reservations_declined_total` | counter | Declined reservations (tagged by `reason`: `seat_unavailable`, `user_limit`, `idempotent_replay`) |
| `booking_seats_available` | gauge | Total available seats across all shows |
| `booking_reservation_duration_seconds` | timer | Time to process a reservation |

### Health

- **Liveness:** `/actuator/health/liveness` — basic process health
- **Readiness:** `/actuator/health/readiness` — includes DB connectivity check; fails closed when DB is unreachable

## Testing

### One-command burst test

```bash
# start the stack
docker compose down -v && docker compose up -d --build

# wait for startup, then run burst test
./burst-test.sh http://localhost:8080
```

The burst test creates a show, fires 500 concurrent requests at one hot seat, validates exactly one winner with zero 5xx, tests idempotency, per-user limits, and reconciliation.

### Full test suite

```bash
./full-test.sh
```

Tests cover health checks, input validation, authentication, not-found cases, double-sell prevention, idempotency, per-user limits (max 4), cancellation, hold expiry, reconciliation invariants, burst concurrency (200 concurrent requests), metrics verification, and server-side amount computation.

## Project Structure

```
src/main/java/com/booking/
  config/          BookingProperties, ReservationConfig, SchedulingConfig, SecurityConfig
  controller/      ShowController, ReservationController
  dto/             Request/response records (snake_case via @JsonNaming)
  entity/          JPA entities (Show, Seat, Reservation)
  event/           Application events and listener
  exception/       Domain exceptions and global handler
  filter/          Auth and request-id filters
  metrics/         Prometheus counters, gauges, and timers
  repository/      Spring Data JPA repositories
  scheduler/       Hold expiry scheduler (ShedLock-protected)
  service/         Business logic with interfaces
    strategy/      AllOrNothingStrategy, BestEffortStrategy
```
