package com.booking.service;

import com.booking.dto.CreateShowRequest;
import com.booking.dto.ShowResponse;
import com.booking.entity.Seat;
import com.booking.entity.Show;
import com.booking.exception.ShowNotFoundException;
import com.booking.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ShowService {

    private final ShowRepository showRepository;

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        Show show = new Show(request.name());
        for (String label : request.seats()) {
            show.addSeat(new Seat(label));
        }
        show = showRepository.save(show);
        return toResponse(show);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ShowNotFoundException(id));
        return toResponse(show);
    }

    private ShowResponse toResponse(Show show) {
        var seatInfos = show.getSeats().stream()
                .map(s -> new ShowResponse.SeatInfo(s.getLabel(), s.getStatus().name()))
                .toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) show.getSeats().size());
        summary.put("available", show.getSeats().stream().filter(s -> s.getStatus() == com.booking.entity.SeatStatus.AVAILABLE).count());
        summary.put("held", show.getSeats().stream().filter(s -> s.getStatus() == com.booking.entity.SeatStatus.HELD).count());
        summary.put("confirmed", show.getSeats().stream().filter(s -> s.getStatus() == com.booking.entity.SeatStatus.CONFIRMED).count());

        return new ShowResponse(show.getId(), show.getName(), seatInfos, summary);
    }
}
