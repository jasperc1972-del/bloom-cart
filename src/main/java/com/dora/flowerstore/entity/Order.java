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
    /** 結帳批號：同一次結帳拆出的多張訂單共用 */
    private String batchNo;
    private Long userId;
    private BigDecimal totalAmount;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    /** 付款人（可與會員、收件人不同） */
    private String payerName;
    private String payerPhone;
    /** 付款通知信寄送對象 */
    private String payerEmail;
    /** 指定送達日期 */
    private LocalDate deliveryDate;
    /** 指定時段 */
    private String deliveryTimeSlot;
    /** 卡片祝福語快照 */
    private String cardMessage;
    private String status;
    private LocalDateTime createdAt;
}
