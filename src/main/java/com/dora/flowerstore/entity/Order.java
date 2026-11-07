package com.dora.flowerstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 訂單主檔（orders）
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    /** 指定送達日期 */
    private LocalDate deliveryDate;
    /** 指定時段 */
    private String deliveryTimeSlot;
    /** 卡片祝福語快照 */
    private String cardMessage;
    private String status;
    private LocalDateTime createdAt;
}
