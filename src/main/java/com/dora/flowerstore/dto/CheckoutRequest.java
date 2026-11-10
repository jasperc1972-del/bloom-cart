package com.dora.flowerstore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 結帳請求：一次結帳可以有多位收件人（每位收件人拆成一張訂單）
 * 購物車每一項都必須指定給剛好一位收件人
 */
public record CheckoutRequest(
        @NotEmpty(message = "請至少填寫一位收件人")
        @Size(max = 10, message = "一次結帳最多 10 位收件人")
        List<@Valid ShipmentRequest> shipments
) {
}
