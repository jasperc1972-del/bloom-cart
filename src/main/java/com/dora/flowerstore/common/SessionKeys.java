package com.dora.flowerstore.common;

/**
 * HttpSession 中使用的 key
 */
public final class SessionKeys {

    public static final String USER_ID = "LOGIN_USER_ID";
    public static final String USER_ROLE = "LOGIN_USER_ROLE";
    public static final String USERNAME = "LOGIN_USERNAME";
    /** 訪客購物車 */
    public static final String GUEST_CART = "GUEST_CART";
    /** 訪客購物車項目流水號 */
    public static final String GUEST_CART_SEQ = "GUEST_CART_SEQ";

    private SessionKeys() {
    }
}
