package com.dora.flowerstore.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 花卉商品（products）
 */
@Data
@TableName("products")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 業務代碼，對應圖片資料夾 */
    private String productCode;
    private String name;
    /** 花種 */
    private String flowerType;
    /** 花語 */
    private String flowerMeaning;
    /** 適用場景，逗號分隔 */
    private String occasions;
    /** 照顧注意事項 */
    private String careInstructions;
    private BigDecimal basePrice;
    private Integer stock;
    private String mainImageUrl;
    /** 1 上架, 0 下架 */
    private Integer status;
    private LocalDateTime createdAt;
}
