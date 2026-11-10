package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.dto.CheckoutRequest;
import com.dora.flowerstore.dto.OrderView;
import com.dora.flowerstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 會員結帳與訂單查詢（攔截器已確保登入）
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ApiResponse<List<OrderView>> checkout(@RequestAttribute("userId") Long userId,
                                                 @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.ok("訂單成立！朵拉花店會準時為您送達", orderService.checkout(userId, request));
    }

    @GetMapping
    public ApiResponse<List<OrderView>> myOrders(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok(orderService.listMyOrders(userId));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderView> myOrder(@RequestAttribute("userId") Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getMyOrder(userId, orderNo));
    }

    /** 會員取消自己「待付款」的訂單 */
    @DeleteMapping("/{orderNo}")
    public ApiResponse<Void> cancel(@RequestAttribute("userId") Long userId, @PathVariable String orderNo) {
        orderService.cancelMyOrder(userId, orderNo);
        return ApiResponse.ok("已取消訂單", null);
    }
}
