package com.dora.flowerstore.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dora.flowerstore.entity.CartItem;
import com.dora.flowerstore.entity.Order;
import com.dora.flowerstore.entity.OrderItem;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 單元測試共用工具：
 * Service 裡用到 LambdaQueryWrapper（例如 CartItem::getUserId），
 * MyBatis-Plus 需要先知道 Entity 的欄位對應才能組條件。
 * 正式執行時由 Spring 自動初始化；單元測試不啟動 Spring、不連資料庫，所以要手動初始化一次。
 */
public final class MybatisPlusTestSupport {

    private static boolean initialized = false;

    private MybatisPlusTestSupport() {
    }

    public static synchronized void initTableInfo() {
        if (initialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : new Class<?>[]{User.class, Product.class, CartItem.class, Order.class, OrderItem.class}) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        initialized = true;
    }
}
