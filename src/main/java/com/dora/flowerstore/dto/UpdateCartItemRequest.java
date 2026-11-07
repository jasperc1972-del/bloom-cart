package com.dora.flowerstore.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改購物車項目（數量、包裝、卡片）
 */
public record UpdateCartItemRequest(
        @NotNull(message = "請填寫數量") @Min(value = 1, message = "數量至少 1") @Max(value = 99, message = "單次數量最多 99") Integer quantity,
        String wrappingOption,
        @Size(max = 100, message = "卡片附言上限 100 字") String cardMessage,
        @Size(max = 50, message = "署名上限 50 字") String cardSignature,
        Boolean anonymous
) {
}
