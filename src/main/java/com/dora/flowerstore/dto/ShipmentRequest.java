package com.dora.flowerstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 一位收件人的配送資訊 + 付款人 + 要送給他的購物車項目
 * 付款人可以每位收件人不同（前端勾「同一人付款」時會把同一組付款人帶到每位收件人）
 */
public record ShipmentRequest(
        @NotBlank(message = "請輸入收件人姓名") @Size(max = 50, message = "收件人姓名過長") String recipientName,
        @NotBlank(message = "請輸入收件人電話")
        @Pattern(regexp = "^[0-9+\\-() ]{8,20}$", message = "收件人電話格式不正確")
        String recipientPhone,
        @NotBlank(message = "請輸入送貨地址") @Size(max = 255, message = "地址過長") String shippingAddress,
        @NotNull(message = "請選擇指定送達日期") LocalDate deliveryDate,
        @NotBlank(message = "請選擇配送時段") String deliveryTimeSlot,
        @NotBlank(message = "請輸入付款人姓名") @Size(max = 50, message = "付款人姓名過長") String payerName,
        @NotBlank(message = "請輸入付款人電話")
        @Pattern(regexp = "^[0-9+\\-() ]{8,20}$", message = "付款人電話格式不正確")
        String payerPhone,
        @NotBlank(message = "請輸入付款人 Email，付款通知會寄到這裡")
        @Email(message = "付款人 Email 格式不正確")
        @Size(max = 100, message = "付款人 Email 過長")
        String payerEmail,
        @NotEmpty(message = "每位收件人至少要分配一項商品") List<@NotNull Long> cartItemIds
) {
}
