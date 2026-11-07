package com.dora.flowerstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dora.flowerstore.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 原子扣減庫存（防超賣）：只有「上架中且庫存足夠」才會扣成功。
     * InnoDB 對該列加排他鎖，多筆結帳同時進來也會依序判斷，回傳 0 代表庫存不足。
     */
    @Update("UPDATE products SET stock = stock - #{qty} "
            + "WHERE id = #{productId} AND status = 1 AND stock >= #{qty}")
    int decreaseStock(@Param("productId") Long productId, @Param("qty") int qty);

    /** 刪除 / 取消訂單時把庫存加回去 */
    @Update("UPDATE products SET stock = stock + #{qty} WHERE id = #{productId}")
    int increaseStock(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * 讀取最新已提交的庫存（鎖定讀）。
     * 交易內一般 SELECT 在 REPEATABLE READ 下只會看到交易開始時的快照，扣庫存失敗時要用這個才準。
     */
    @Select("SELECT stock FROM products WHERE id = #{productId} FOR SHARE")
    Integer selectCurrentStock(@Param("productId") Long productId);
}
