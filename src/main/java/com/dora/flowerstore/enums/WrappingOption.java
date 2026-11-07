package com.dora.flowerstore.enums;

import com.dora.flowerstore.common.BusinessException;

import java.math.BigDecimal;

/**
 * 包裝風格與加價（PRD 4.2）
 */
public enum WrappingOption {

    STANDARD("牛皮紙經典包裝", new BigDecimal("0.00")),
    FRENCH("法式霧面歐雅紙包裝", new BigDecimal("150.00"));

    private final String label;
    private final BigDecimal fee;

    WrappingOption(String label, BigDecimal fee) {
        this.label = label;
        this.fee = fee;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getFee() {
        return fee;
    }

    /** 字串轉列舉；空值視為預設牛皮紙包裝 */
    public static WrappingOption from(String code) {
        if (code == null || code.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支援的包裝選項：" + code);
        }
    }
}
