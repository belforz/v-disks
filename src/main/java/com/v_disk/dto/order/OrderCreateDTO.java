package com.v_disk.dto.order;

import java.util.List;

public record OrderCreateDTO(String userId, List<String> vinylIds,
    int qt,
    String paymentId,
    Boolean isPaymentConfirmed,
    String orderStatus
) {}
