package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.dto.AddToCartRequest;
import com.dora.flowerstore.dto.CartView;
import com.dora.flowerstore.dto.UpdateCartItemRequest;
import com.dora.flowerstore.service.CartService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 購物車 API：訪客（userId = null）用 Session 購物車，會員用資料庫購物車
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartView> getCart(@RequestAttribute(value = "userId", required = false) Long userId,
                                         HttpSession session) {
        return ApiResponse.ok(cartService.getCart(userId, session));
    }

    @PostMapping("/add")
    public ApiResponse<CartView> addToCart(@RequestAttribute(value = "userId", required = false) Long userId,
                                           @Valid @RequestBody AddToCartRequest request,
                                           HttpSession session) {
        cartService.addItem(userId, session, request);
        return ApiResponse.ok("成功將花卉加入朵拉花店購物車", cartService.getCart(userId, session));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartView> update(@RequestAttribute(value = "userId", required = false) Long userId,
                                        @PathVariable Long itemId,
                                        @Valid @RequestBody UpdateCartItemRequest request,
                                        HttpSession session) {
        cartService.updateItem(userId, session, itemId, request);
        return ApiResponse.ok("已更新", cartService.getCart(userId, session));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartView> remove(@RequestAttribute(value = "userId", required = false) Long userId,
                                        @PathVariable Long itemId,
                                        HttpSession session) {
        cartService.removeItem(userId, session, itemId);
        return ApiResponse.ok("已移除", cartService.getCart(userId, session));
    }
}
