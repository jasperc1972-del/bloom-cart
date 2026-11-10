package com.dora.flowerstore.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(
        Long id,
        String orderNo,
        String batchNo,
        String username,
        BigDecimal totalAmount,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        String payerName,
        String payerPhone,
        String payerEmail,
        LocalDate deliveryDate,
        String deliveryTimeSlot,
        String cardMessage,
        String status,
        String statusLabel,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime createdAt,
        List<OrderItemView> items
) {
}
