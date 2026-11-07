package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.dto.ProductView;
import com.dora.flowerstore.enums.DeliveryTimeSlot;
import com.dora.flowerstore.enums.WrappingOption;
import com.dora.flowerstore.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前台商品瀏覽（訪客可用）
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;
    private final int minDaysAhead;
    private final int maxDaysAhead;

    public ProductController(ProductService productService,
                             @Value("${app.delivery.min-days-ahead}") int minDaysAhead,
                             @Value("${app.delivery.max-days-ahead}") int maxDaysAhead) {
        this.productService = productService;
        this.minDaysAhead = minDaysAhead;
        this.maxDaysAhead = maxDaysAhead;
    }

    /** 商品列表：keyword 比對品名/花種/花語，occasion 依場合，flowerType 依花種 */
    @GetMapping("/products")
    public ApiResponse<List<ProductView>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String occasion,
                                               @RequestParam(required = false) String flowerType) {
        return ApiResponse.ok(productService.listOnSale(keyword, occasion, flowerType));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductView> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.getOnSale(id));
    }

    @GetMapping("/products/filters")
    public ApiResponse<Map<String, List<String>>> filters() {
        return ApiResponse.ok(productService.filterOptions());
    }

    /** 前端下拉選單需要的固定選項：包裝、配送時段、可預約日期範圍 */
    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wrappingOptions", Arrays.stream(WrappingOption.values())
                .map(w -> Map.of("code", w.name(), "label", w.getLabel(), "fee", w.getFee()))
                .toList());
        m.put("timeSlots", DeliveryTimeSlot.labels());
        m.put("deliveryMinDaysAhead", minDaysAhead);
        m.put("deliveryMaxDaysAhead", maxDaysAhead);
        m.put("cardMessageLimit", 100);
        return ApiResponse.ok(m);
    }
}
