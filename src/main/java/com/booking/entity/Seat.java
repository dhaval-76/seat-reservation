package com.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "label"}))
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(nullable = false, length = 20)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "held_by", length = 100)
    private String heldBy;

    @Column(name = "held_at")
    private Instant heldAt;

    public Seat() {}

    public Seat(String label) {
        this.label = label;
    }

    public Long getId() { return id; }
    public Show getShow() { return show; }
    public String getLabel() { return label; }
    public SeatStatus getStatus() { return status; }
    public String getHeldBy() { return heldBy; }
    public Instant getHeldAt() { return heldAt; }

    public void setId(Long id) { this.id = id; }
    public void setShow(Show show) { this.show = show; }
    public void setLabel(String label) { this.label = label; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public void setHeldBy(String heldBy) { this.heldBy = heldBy; }
    public void setHeldAt(Instant heldAt) { this.heldAt = heldAt; }

    public void hold(String userId) {
        this.status = SeatStatus.HELD;
        this.heldBy = userId;
        this.heldAt = Instant.now();
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldAt = null;
    }

    public void confirm() {
        this.status = SeatStatus.CONFIRMED;
    }
}
