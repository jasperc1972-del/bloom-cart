package com.dora.flowerstore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品顯示資料；圖片路徑已經過容錯處理，不存在時會是 placeholder
 */
public record ProductView(
        Long id,
        String productCode,
        String name,
        String flowerType,
        String flowerMeaning,
        List<String> occasions,
        String careInstructions,
        BigDecimal basePrice,
        int stock,
        String mainImageUrl,
        List<String> detailImageUrls,
        int status
) {
}
