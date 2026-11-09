package com.dora.flowerstore.enums;

import com.dora.flowerstore.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("列舉：包裝、訂單狀態、配送時段")
class EnumsTest {

    @Nested
    @DisplayName("WrappingOption 包裝選項")
    class WrappingOptionTest {

        @ParameterizedTest(name = "空值「{0}」視為預設牛皮紙包裝")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void blankIsStandard(String code) {
            assertThat(WrappingOption.from(code)).isEqualTo(WrappingOption.STANDARD);
        }

        @Test
        @DisplayName("大小寫與前後空白不影響判斷")
        void caseInsensitive() {
            assertThat(WrappingOption.from(" french ")).isEqualTo(WrappingOption.FRENCH);
        }

        @Test
        @DisplayName("加價金額：牛皮紙 0 元、法式霧面 150 元")
        void fees() {
            assertThat(WrappingOption.STANDARD.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(WrappingOption.FRENCH.getFee()).isEqualByComparingTo("150");
        }

        @Test
        @DisplayName("不支援的包裝 → 業務例外")
        void unknown() {
            assertThatThrownBy(() -> WrappingOption.from("GOLD"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支援的包裝選項");
        }
    }

    @Nested
    @DisplayName("OrderStatus 訂單狀態")
    class OrderStatusTest {

        @Test
        @DisplayName("狀態順序：待付款 → 已付款 → 配送中 → 已送達")
        void order() {
            assertThat(OrderStatus.values()).containsExactly(
                    OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.DELIVERING, OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("字串轉狀態不分大小寫")
        void from() {
            assertThat(OrderStatus.from("paid")).isEqualTo(OrderStatus.PAID);
            assertThat(OrderStatus.PAID.getLabel()).isEqualTo("已付款");
        }

        @Test
        @DisplayName("不存在的狀態 / null → 業務例外")
        void unknown() {
            assertThatThrownBy(() -> OrderStatus.from("CANCELLED")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> OrderStatus.from(null)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("DeliveryTimeSlot 配送時段")
    class DeliveryTimeSlotTest {

        @Test
        @DisplayName("只接受上午與下午兩個時段")
        void valid() {
            assertThat(DeliveryTimeSlot.isValid("09:00-12:00")).isTrue();
            assertThat(DeliveryTimeSlot.isValid("14:00-18:00")).isTrue();
            assertThat(DeliveryTimeSlot.isValid("20:00-22:00")).isFalse();
            assertThat(DeliveryTimeSlot.isValid(null)).isFalse();
            assertThat(DeliveryTimeSlot.labels()).containsExactly("09:00-12:00", "14:00-18:00");
        }
    }
}
