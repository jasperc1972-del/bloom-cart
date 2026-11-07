package com.dora.flowerstore.dto;

import java.math.BigDecimal;

public record OrderItemView(
        Long productId,
        String productName,
        BigDecimal price,
        BigDecimal wrappingFee,
        String wrappingOption,
        String wrappingLabel,
        int quantity,
        String cardMessage,
        String cardSignature,
        boolean anonymous,
        BigDecimal subtotal
) {
}
