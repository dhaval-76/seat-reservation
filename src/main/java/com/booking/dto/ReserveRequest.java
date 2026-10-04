package com.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReserveRequest(
    @NotEmpty List<String> seats,
    @NotBlank String idempotencyKey,
    @Min(0) long amountPaise
) {}
