package com.booking.service;

import com.booking.dto.CreateShowRequest;
import com.booking.dto.ShowResponse;

public interface ShowServiceInterface {
    ShowResponse createShow(CreateShowRequest request);
    ShowResponse getShow(Long id);
}
