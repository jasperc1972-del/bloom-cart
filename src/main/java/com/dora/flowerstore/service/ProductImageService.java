package com.dora.flowerstore.service;

import com.dora.flowerstore.entity.Product;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商品圖片路徑解析與容錯（PRD 4.1）
 * 主圖：/images/products/{product_code}/main.webp
 * 細節圖：/images/products/{product_code}/detail_1.webp ~ detail_N.webp
 * 圖檔不存在 → /images/products/placeholder_flower.webp
 */
@Service
public class ProductImageService {

    public static final String PLACEHOLDER = "/images/products/placeholder_flower.webp";
    private static final int MAX_DETAIL_IMAGES = 6;

    private final ResourceLoader resourceLoader;
    /** 檔案是否存在的快取，避免每次請求都去查 classpath */
    private final Map<String, Boolean> existsCache = new ConcurrentHashMap<>();

    public ProductImageService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public static String defaultMainPath(String productCode) {
        return "/images/products/" + productCode + "/main.webp";
    }

    /** 解析主圖；資料庫沒填就用規則路徑，檔案不存在就給 placeholder */
    public String resolveMain(Product product) {
        String url = product.getMainImageUrl();
        if (url == null || url.isBlank() || PLACEHOLDER.equals(url)) {
            url = defaultMainPath(product.getProductCode());
        }
        return exists(url) ? url : PLACEHOLDER;
    }

    /** 依序找 detail_1、detail_2…，遇到第一個不存在的就停 */
    public List<String> resolveDetails(Product product) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= MAX_DETAIL_IMAGES; i++) {
            String url = "/images/products/" + product.getProductCode() + "/detail_" + i + ".webp";
            if (!exists(url)) {
                break;
            }
            result.add(url);
        }
        return result;
    }

    /** 管理員更新商品後清快取，讓新放的圖檔生效 */
    public void clearCache() {
        existsCache.clear();
    }

    private boolean exists(String url) {
        // 只接受站內 /images/ 路徑，避免外部網址或路徑穿越
        if (!url.startsWith("/images/") || url.contains("..")) {
            return false;
        }
        return existsCache.computeIfAbsent(url,
                u -> resourceLoader.getResource("classpath:static" + u).exists());
    }
}
