package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.AddToCartRequest;
import com.dora.flowerstore.dto.CartView;
import com.dora.flowerstore.dto.UpdateCartItemRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 購物車：userId 不為 null 時操作資料庫購物車，否則操作 Session 訪客購物車
 */
public interface CartService {

    /** 會員加入購物車（PRD 範例介面） */
    void addItemToCart(Long userId, AddToCartRequest request);

    /** 依登入狀態自動選擇資料庫或 Session */
    void addItem(Long userId, HttpSession session, AddToCartRequest request);

    CartView getCart(Long userId, HttpSession session);

    void updateItem(Long userId, HttpSession session, Long itemId, UpdateCartItemRequest request);

    void removeItem(Long userId, HttpSession session, Long itemId);

    /** 登入時把 Session 購物車合併到資料庫購物車 */
    void mergeGuestCart(HttpSession session, Long userId);
}
