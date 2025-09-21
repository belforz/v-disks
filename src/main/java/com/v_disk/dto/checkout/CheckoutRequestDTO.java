package com.v_disk.dto.checkout;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequestDTO(
    @NotBlank(message = "userId is required") String userId,
    @NotBlank(message = "paymentId is required") String paymentId
) {}