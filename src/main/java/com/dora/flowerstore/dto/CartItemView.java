package com.dora.flowerstore.dto;

import java.math.BigDecimal;

/**
 * 購物車項目顯示資料
 * subtotal = (basePrice + wrappingFee) × quantity
 */
public record CartItemView(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String imageUrl,
        BigDecimal basePrice,
        String wrappingOption,
        String wrappingLabel,
        BigDecimal wrappingFee,
        int quantity,
        String cardMessage,
        String cardSignature,
        boolean anonymous,
        BigDecimal subtotal,
        int stock,
        boolean available
) {
}
