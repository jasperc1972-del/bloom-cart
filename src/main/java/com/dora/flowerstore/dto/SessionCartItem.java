package com.dora.flowerstore.dto;

import java.io.Serializable;

/**
 * 訪客購物車項目（存在 HttpSession），結構與 cart_items 相同，方便登入時合併
 */
public record SessionCartItem(
        Long id,
        Long productId,
        int quantity,
        String wrappingOption,
        String cardMessage,
        String cardSignature,
        boolean anonymous
) implements Serializable {

    public SessionCartItem withQuantity(int newQuantity) {
        return new SessionCartItem(id, productId, newQuantity, wrappingOption, cardMessage, cardSignature, anonymous);
    }
}
