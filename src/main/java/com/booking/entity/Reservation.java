package com.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "seat_labels", nullable = false)
    private String seatLabels;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public Reservation() {}

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public String getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public ReservationStatus getStatus() { return status; }
    public String getSeatLabels() { return seatLabels; }
    public long getAmountPaise() { return amountPaise; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setId(Long id) { this.id = id; }
    public void setShowId(Long showId) { this.showId = showId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public void setSeatLabels(String seatLabels) { this.seatLabels = seatLabels; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
