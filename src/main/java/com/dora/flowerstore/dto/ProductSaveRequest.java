package com.dora.flowerstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理員新增 / 修改商品
 */
public record ProductSaveRequest(
        @NotBlank(message = "請輸入商品代碼")
        @Pattern(regexp = "^[A-Z0-9-]{3,50}$", message = "商品代碼只能用大寫英數字與 -（如 FLOWER-ROSE-RED）")
        String productCode,
        @NotBlank(message = "請輸入商品名稱") @Size(max = 100, message = "商品名稱過長") String name,
        @NotBlank(message = "請輸入花種") @Size(max = 50, message = "花種過長") String flowerType,
        String flowerMeaning,
        @Size(max = 255, message = "適用場景過長") String occasions,
        String careInstructions,
        @NotNull(message = "請輸入單價")
        @DecimalMin(value = "0.00", message = "單價不可為負")
        @Digits(integer = 8, fraction = 2, message = "單價格式錯誤")
        BigDecimal basePrice,
        @NotNull(message = "請輸入庫存") @Min(value = 0, message = "庫存不可為負") Integer stock,
        @NotNull(message = "請選擇上下架狀態") Integer status
) {
}
