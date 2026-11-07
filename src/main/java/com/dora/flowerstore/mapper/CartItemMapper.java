package com.dora.flowerstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dora.flowerstore.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
    // MyBatis-Plus 已自動內建基本 CRUD 方法
}
