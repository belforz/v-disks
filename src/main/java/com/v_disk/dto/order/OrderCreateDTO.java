package com.v_disk.dto.order;


import java.util.List;

import com.v_disk.model.OrderItem;

public record OrderCreateDTO(String userId, List<OrderItem> items,
    Integer qt,
    String paymentId,
    Boolean isPaymentConfirmed,
    String orderStatus
) {}
