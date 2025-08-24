package com.v_disk.dto.order;

import java.util.List;

public record OrderUpdateDTO (
    String userId,
    List<String> vinylIds,
    int qt,
    String paymentId,
    String orderStatus,
    Boolean isPaymentConfirmed

) {}
