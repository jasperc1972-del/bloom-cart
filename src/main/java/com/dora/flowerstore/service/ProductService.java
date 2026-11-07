package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.ProductSaveRequest;
import com.dora.flowerstore.dto.ProductView;
import com.dora.flowerstore.entity.Product;

import java.util.List;
import java.util.Map;

public interface ProductService {

    /** 前台：上架商品，可依關鍵字（品名/花語）、場合、花種篩選 */
    List<ProductView> listOnSale(String keyword, String occasion, String flowerType);

    /** 前台：單一上架商品詳情 */
    ProductView getOnSale(Long id);

    /** 前台篩選選單：所有場合與花種 */
    Map<String, List<String>> filterOptions();

    /** 後台：全部商品（含下架） */
    List<ProductView> listAll();

    ProductView create(ProductSaveRequest request);

    ProductView update(Long id, ProductSaveRequest request);

    ProductView toView(Product product);
}
