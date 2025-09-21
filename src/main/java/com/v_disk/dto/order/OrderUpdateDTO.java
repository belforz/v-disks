package com.v_disk.dto.order;

import java.util.List;

import com.v_disk.model.OrderItem;

public record OrderUpdateDTO (
    String userId,
    List<OrderItem> items,
    Integer qt,
    String paymentId,
    String orderStatus,
    Boolean isPaymentConfirmed

) {}
