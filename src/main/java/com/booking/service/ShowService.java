package com.booking.service;

import com.booking.dto.CreateShowRequest;
import com.booking.dto.ShowResponse;
import com.booking.entity.Seat;
import com.booking.entity.SeatStatus;
import com.booking.entity.Show;
import com.booking.exception.ShowNotFoundException;
import com.booking.repository.ShowRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ShowService implements ShowServiceInterface {

    private final ShowRepository showRepository;

    private final Cache<Long, ShowResponse> showCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofSeconds(2))
            .build();

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        Show show = new Show(request.name());
        show.setPricePaise(request.pricePaise());
        for (String label : request.seats()) {
            show.addSeat(new Seat(label));
        }
        show = showRepository.save(show);
        ShowResponse response = toResponse(show);
        showCache.put(show.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        ShowResponse cached = showCache.getIfPresent(id);
        if (cached != null) {
            return cached;
        }
        Show show = showRepository.findByIdWithSeats(id)
                .orElseThrow(() -> new ShowNotFoundException(id));
        ShowResponse response = toResponse(show);
        showCache.put(id, response);
        return response;
    }

    public void evictShow(Long id) {
        showCache.invalidate(id);
    }

    private ShowResponse toResponse(Show show) {
        var seatInfos = show.getSeats().stream()
                .map(s -> new ShowResponse.SeatInfo(s.getLabel(), s.getStatus().name().toLowerCase()))
                .toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) show.getSeats().size());
        summary.put("available", show.getSeats().stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count());
        summary.put("held", show.getSeats().stream().filter(s -> s.getStatus() == SeatStatus.HELD).count());
        summary.put("confirmed", show.getSeats().stream().filter(s -> s.getStatus() == SeatStatus.CONFIRMED).count());

        return new ShowResponse(show.getId(), show.getName(), show.getPricePaise(), seatInfos, summary);
    }
}
