package com.dora.flowerstore.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(@NotBlank(message = "請選擇狀態") String status) {
}
