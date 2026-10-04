package com.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateShowRequest(
    @NotBlank String name,
    @NotEmpty List<String> seats
) {}
