package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.dto.OrderView;
import com.dora.flowerstore.dto.ProductSaveRequest;
import com.dora.flowerstore.dto.ProductView;
import com.dora.flowerstore.dto.UpdateOrderStatusRequest;
import com.dora.flowerstore.service.OrderService;
import com.dora.flowerstore.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系統管理員：商品上下架、價格庫存、訂單配送狀態（攔截器已確保 ADMIN 角色）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;

    public AdminController(ProductService productService, OrderService orderService) {
        this.productService = productService;
        this.orderService = orderService;
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductView>> products() {
        return ApiResponse.ok(productService.listAll());
    }

    @PostMapping("/products")
    public ApiResponse<ProductView> create(@Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.ok("已新增商品", productService.create(request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductView> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.ok("已更新商品", productService.update(id, request));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderView>> orders(@RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.listAll(status));
    }

    @PutMapping("/orders/{id}/status")
    public ApiResponse<OrderView> updateStatus(@PathVariable Long id,
                                               @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok("已更新配送狀態", orderService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/orders/{id}")
    public ApiResponse<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ApiResponse.ok("已刪除訂單", null);
    }
}
