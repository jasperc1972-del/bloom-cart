package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.dto.ProductSaveRequest;
import com.dora.flowerstore.dto.ProductView;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.service.ProductImageService;
import com.dora.flowerstore.service.ProductService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageService imageService;

    public ProductServiceImpl(ProductMapper productMapper, ProductImageService imageService) {
        this.productMapper = productMapper;
        this.imageService = imageService;
    }

    @Override
    public List<ProductView> listOnSale(String keyword, String occasion, String flowerType) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(notBlank(flowerType), Product::getFlowerType, flowerType)
                // 場合以逗號分隔儲存，用 LIKE 比對
                .like(notBlank(occasion), Product::getOccasions, occasion)
                // 關鍵字同時比對品名、花種、花語
                .and(notBlank(keyword), w -> w.like(Product::getName, keyword)
                        .or().like(Product::getFlowerType, keyword)
                        .or().like(Product::getFlowerMeaning, keyword))
                .orderByAsc(Product::getId);
        return productMapper.selectList(q).stream().map(this::toView).toList();
    }

    @Override
    public ProductView getOnSale(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到這款花卉，可能已下架");
        }
        return toView(p);
    }

    @Override
    public Map<String, List<String>> filterOptions() {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1));
        TreeSet<String> occasions = new TreeSet<>();
        TreeSet<String> types = new TreeSet<>();
        for (Product p : list) {
            occasions.addAll(splitOccasions(p.getOccasions()));
            types.add(p.getFlowerType());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("occasions", List.copyOf(occasions));
        result.put("flowerTypes", List.copyOf(types));
        return result;
    }

    @Override
    public List<ProductView> listAll() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>().orderByAsc(Product::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    public ProductView create(ProductSaveRequest req) {
        Product p = new Product();
        apply(p, req);
        p.setMainImageUrl(ProductImageService.defaultMainPath(p.getProductCode()));
        try {
            productMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("商品代碼已存在：" + req.productCode());
        }
        imageService.clearCache();
        return toView(productMapper.selectById(p.getId()));
    }

    @Override
    public ProductView update(Long id, ProductSaveRequest req) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        String oldCode = p.getProductCode();
        apply(p, req);
        // 代碼變更時，主圖路徑跟著換
        if (!oldCode.equals(p.getProductCode())) {
            p.setMainImageUrl(ProductImageService.defaultMainPath(p.getProductCode()));
        }
        try {
            productMapper.updateById(p);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("商品代碼已存在：" + req.productCode());
        }
        imageService.clearCache();
        return toView(productMapper.selectById(id));
    }

    @Override
    public ProductView toView(Product p) {
        return new ProductView(
                p.getId(),
                p.getProductCode(),
                p.getName(),
                p.getFlowerType(),
                p.getFlowerMeaning(),
                splitOccasions(p.getOccasions()),
                p.getCareInstructions(),
                p.getBasePrice(),
                p.getStock() == null ? 0 : p.getStock(),
                imageService.resolveMain(p),
                imageService.resolveDetails(p),
                p.getStatus() == null ? 0 : p.getStatus());
    }

    private void apply(Product p, ProductSaveRequest req) {
        if (req.status() != 0 && req.status() != 1) {
            throw new BusinessException("狀態只能是 1（上架）或 0（下架）");
        }
        p.setProductCode(req.productCode().trim());
        p.setName(req.name().trim());
        p.setFlowerType(req.flowerType().trim());
        p.setFlowerMeaning(req.flowerMeaning());
        // 統一場合格式：去空白、支援全形逗號
        p.setOccasions(String.join(",", splitOccasions(req.occasions())));
        p.setCareInstructions(req.careInstructions());
        p.setBasePrice(req.basePrice());
        p.setStock(req.stock());
        p.setStatus(req.status());
    }

    private static List<String> splitOccasions(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split("[,，、]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .distinct()
                .toList();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
