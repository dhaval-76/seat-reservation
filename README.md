# Seat Reservation System

Concurrent seat reservation API with PostgreSQL row-level locking, idempotent reservations, and distributed scheduling.

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

### Authentication

Protected endpoints require a `Bearer` token in the format `Bearer user-<id>`. The user ID is extracted from the token.

```
Authorization: Bearer user-alice
```

### Endpoints

#### Create Show

```
POST /shows
```

```json
{
  "name": "Hamilton",
  "seats": ["A1", "A2", "A3", "B1", "B2"]
}
```

Response `201`:

```json
{
  "id": 1,
  "name": "Hamilton",
  "seats": [
    { "label": "A1", "status": "AVAILABLE" },
    { "label": "A2", "status": "AVAILABLE" }
  ],
  "summary": { "total": 5, "available": 5, "held": 0, "confirmed": 0 }
}
```

#### Get Show

```
GET /shows/{id}
```

Response `200`: same shape as above with current seat statuses.

#### Reserve Seats

```
POST /shows/{showId}/reserve
Authorization: Bearer user-alice
```

```json
{
  "seats": ["A1", "A2"],
  "idempotencyKey": "uuid-123"
}
```

Response `201`:

```json
{
  "id": 1,
  "showId": 1,
  "userId": "user-alice",
  "status": "HELD",
  "seats": ["A1", "A2"],
  "createdAt": "2026-10-04T07:00:00Z",
  "expiresAt": "2026-10-04T07:05:00Z"
}
```

| Status Code | Meaning |
|-------------|---------|
| 201 | Reservation created (or idempotent retry) |
| 401 | Missing auth token |
| 404 | Show or seat not found |
| 409 | Seat unavailable, user limit exceeded, or idempotency conflict |

#### Cancel Reservation

```
POST /reservations/{id}/cancel
Authorization: Bearer user-alice
```

Response `200`: reservation with status `CANCELLED`.

## Configuration

All properties can be overridden via environment variables.

| Property | Default | Description |
|----------|---------|-------------|
| `booking.reservation-strategy` | `all-or-nothing` | `all-or-nothing` or `best-effort` |
| `booking.hold-duration-seconds` | `300` | How long a hold lasts before auto-expiry |
| `booking.hold-cleanup-interval-ms` | `30000` | Scheduler poll interval |
| `booking.max-seats-per-user-per-show` | `10` | Per-user seat cap |

### Reservation Strategies

- **all-or-nothing** -- all requested seats must be available or the entire request fails (409).
- **best-effort** -- reserves whichever requested seats are available; fails only if none are.

## Architecture

### Concurrency

Seats are locked with `SELECT ... FOR UPDATE` (via JPA `@Lock(PESSIMISTIC_WRITE)`) ordered by label to prevent deadlocks. Idempotency keys prevent duplicate reservations on retries.

### Hold Expiry

A scheduled task polls for expired holds and releases their seats. In multi-instance deployments, ShedLock ensures only one instance runs the task at a time using a database-backed lock (`shedlock` table).

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
| `booking_reservations_declined_total` | counter | Declined reservations (tagged by `reason`) |
| `booking_reservation_duration_seconds` | timer | Time to process a reservation |

## Testing

Run the 28-test integration suite against a running instance:

```bash
# start the stack
docker compose down -v && docker compose up -d --build

# wait for startup, then run tests
./full-test.sh
```

Tests cover health checks, input validation, authentication, not-found cases, double-sell prevention, idempotency, per-user limits, cancellation, hold expiry, reconciliation invariants, burst concurrency (200 concurrent requests), and metrics verification.

## Project Structure

```
src/main/java/com/booking/
  config/          BookingProperties, ReservationConfig, SchedulingConfig, SecurityConfig
  controller/      ShowController, ReservationController
  dto/             Request/response records
  entity/          JPA entities (Show, Seat, Reservation)
  event/           Application events and listener
  exception/       Domain exceptions and global handler
  filter/          Auth and request-id filters
  metrics/         Prometheus counters and timers
  repository/      Spring Data JPA repositories
  scheduler/       Hold expiry scheduler (ShedLock-protected)
  service/         Business logic with interfaces
    strategy/      AllOrNothingStrategy, BestEffortStrategy
```
