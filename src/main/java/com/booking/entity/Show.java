package com.booking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    public Show() {}

    public Show(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getPricePaise() { return pricePaise; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Seat> getSeats() { return seats; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPricePaise(long pricePaise) { this.pricePaise = pricePaise; }
    public void setSeats(List<Seat> seats) { this.seats = seats; }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setShow(this);
    }
}
