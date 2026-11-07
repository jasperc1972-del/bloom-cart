package com.dora.flowerstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 加入購物車請求
 */
public record AddToCartRequest(
        @NotNull(message = "請選擇商品") Long productId,
        @NotNull(message = "請填寫數量") @Min(value = 1, message = "數量至少 1") @Max(value = 99, message = "單次數量最多 99") Integer quantity,
        String wrappingOption,
        @Size(max = 100, message = "卡片附言上限 100 字") String cardMessage,
        @Size(max = 50, message = "署名上限 50 字") String cardSignature,
        Boolean anonymous
) {
}
