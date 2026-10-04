CREATE TABLE shows (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE seats (
    id          BIGSERIAL PRIMARY KEY,
    show_id     BIGINT NOT NULL REFERENCES shows(id),
    label       VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_by     VARCHAR(100),
    held_at     TIMESTAMP,
    CONSTRAINT uq_seat_show_label UNIQUE (show_id, label)
);

CREATE INDEX idx_seats_show_id ON seats(show_id);
CREATE INDEX idx_seats_show_status ON seats(show_id, status);

CREATE TABLE reservations (
    id                BIGSERIAL PRIMARY KEY,
    show_id           BIGINT NOT NULL REFERENCES shows(id),
    user_id           VARCHAR(100) NOT NULL,
    idempotency_key   VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'HELD',
    seat_labels       TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP,
    CONSTRAINT uq_reservation_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_reservations_show_user ON reservations(show_id, user_id);
CREATE INDEX idx_reservations_status_expires ON reservations(status, expires_at);
