package com.dora.flowerstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 訂單商品明細（order_items），全部是下單當下的快照
 */
@Data
@TableName("order_items")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    /** 下單當下的花卉基礎單價 */
    private BigDecimal price;
    /** 下單當下的包裝加價 */
    private BigDecimal wrappingFee;
    private Integer quantity;
    private String wrappingOption;
    private String cardMessage;
    private String cardSignature;
    private Boolean anonymous;
}
