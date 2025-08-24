package com.v_disk.dto.order;

import java.time.Instant;
import java.util.List;

public record OrderResponseDTO(
    String id,
    String userId,
    List<String> vinylIds,
    int qt,
    String paymentId,
    String orderStatus,
    Boolean isPaymentConfirmed,
    Instant createdAt,
    Instant updatedAt
) {}
