package com.v_disk.dto.order;

import java.time.Instant;
import java.util.List;

import com.v_disk.model.OrderItem;

public record OrderResponseDTO(
    String id,
    String userId,
    List<OrderItem> items,
    Integer qt,
    String paymentId,
    String orderStatus,
    Boolean isPaymentConfirmed,
    Instant createdAt,
    Instant updatedAt
) {}
