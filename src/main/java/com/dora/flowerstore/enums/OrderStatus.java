package com.dora.flowerstore.enums;

import com.dora.flowerstore.common.BusinessException;

/**
 * 訂單狀態，只能依序往前推進
 */
public enum OrderStatus {

    PENDING("待付款"),
    PAID("已付款"),
    DELIVERING("配送中"),
    COMPLETED("已送達");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static OrderStatus from(String code) {
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("不支援的訂單狀態：" + code);
        }
    }
}
