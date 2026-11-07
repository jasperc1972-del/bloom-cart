package com.dora.flowerstore.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 指定配送時段（PRD 4.3）
 */
public enum DeliveryTimeSlot {

    MORNING("09:00-12:00"),
    AFTERNOON("14:00-18:00");

    private final String label;

    DeliveryTimeSlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isValid(String label) {
        return Arrays.stream(values()).anyMatch(s -> s.label.equals(label));
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(DeliveryTimeSlot::getLabel).toList();
    }
}
