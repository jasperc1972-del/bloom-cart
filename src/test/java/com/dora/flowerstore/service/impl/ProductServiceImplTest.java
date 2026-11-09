package com.dora.flowerstore.service.impl;

import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.dto.ProductSaveRequest;
import com.dora.flowerstore.dto.ProductView;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.service.ProductImageService;
import com.dora.flowerstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductServiceImpl 商品")
class ProductServiceImplTest {

    @Mock
    ProductMapper productMapper;
    @Mock
    ProductImageService imageService;
    @InjectMocks
    ProductServiceImpl service;

    @BeforeAll
    static void initMybatis() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        when(imageService.resolveMain(any())).thenReturn("/images/products/X/main.webp");
        when(imageService.resolveDetails(any())).thenReturn(List.of());
    }

    static Product product(Long id, String code, String type, String occasions, int status) {
        Product p = new Product();
        p.setId(id);
        p.setProductCode(code);
        p.setName("花束" + id);
        p.setFlowerType(type);
        p.setOccasions(occasions);
        p.setBasePrice(new BigDecimal("1000.00"));
        p.setStock(5);
        p.setStatus(status);
        return p;
    }

    static ProductSaveRequest saveReq(String code, String occasions, int status) {
        return new ProductSaveRequest(code, " 粉芍藥典雅花束 ", " 芍藥 ", "富貴", occasions, "勤換水",
                new BigDecimal("1880.00"), 12, status);
    }

    @Test
    @DisplayName("查看下架商品 → 404")
    void getOffline() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "FLOWER-A", "玫瑰", "", 0));

        assertThatThrownBy(() -> service.getOnSale(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("商品顯示資料：場合字串拆成清單")
    void toView() {
        ProductView v = service.toView(product(1L, "FLOWER-A", "玫瑰", "求婚,情人節,紀念日", 1));

        assertThat(v.occasions()).containsExactly("求婚", "情人節", "紀念日");
        assertThat(v.mainImageUrl()).isEqualTo("/images/products/X/main.webp");
    }

    @Test
    @DisplayName("篩選選項：場合與花種去重複並排序")
    void filterOptions() {
        when(productMapper.selectList(any())).thenReturn(List.of(
                product(1L, "A", "玫瑰", "生日,求婚", 1),
                product(2L, "B", "鬱金香", "生日,告白", 1),
                product(3L, "C", "玫瑰", null, 1)));

        Map<String, List<String>> opts = service.filterOptions();

        assertThat(opts.get("occasions")).containsExactlyInAnyOrder("生日", "求婚", "告白").doesNotHaveDuplicates();
        assertThat(opts.get("flowerTypes")).containsExactlyInAnyOrder("玫瑰", "鬱金香");
    }

    @Test
    @DisplayName("新增商品：場合格式統一（全形逗號、頓號、空白、重複都整理好）、名稱去空白、主圖用規則路徑")
    void createNormalizes() {
        doAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(9L);
            return 1;
        }).when(productMapper).insert(any(Product.class));
        when(productMapper.selectById(9L)).thenAnswer(inv -> product(9L, "FLOWER-PEONY-PINK", "芍藥", "婚禮", 1));

        service.create(saveReq("FLOWER-PEONY-PINK", "婚禮，母親節、 紀念日 ,婚禮", 1));

        ArgumentCaptor<Product> cap = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(cap.capture());
        Product saved = cap.getValue();
        assertThat(saved.getOccasions()).isEqualTo("婚禮,母親節,紀念日");
        assertThat(saved.getName()).isEqualTo("粉芍藥典雅花束");
        assertThat(saved.getFlowerType()).isEqualTo("芍藥");
        assertThat(saved.getMainImageUrl()).isEqualTo("/images/products/FLOWER-PEONY-PINK/main.webp");
        verify(imageService).clearCache();
    }

    @Test
    @DisplayName("上下架狀態只能是 0 或 1")
    void invalidStatus() {
        assertThatThrownBy(() -> service.create(saveReq("FLOWER-X", "", 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("狀態");
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("商品代碼重複 → 友善訊息")
    void duplicateCode() {
        doThrow(new DuplicateKeyException("dup")).when(productMapper).insert(any(Product.class));

        assertThatThrownBy(() -> service.create(saveReq("FLOWER-ROSE-RED", "", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品代碼已存在");
    }

    @Test
    @DisplayName("修改商品代碼 → 主圖路徑跟著換")
    void updateCodeChangesImagePath() {
        Product existing = product(1L, "FLOWER-OLD", "芍藥", "", 1);
        existing.setMainImageUrl("/images/products/FLOWER-OLD/main.webp");
        when(productMapper.selectById(1L)).thenReturn(existing);

        service.update(1L, saveReq("FLOWER-NEW", "", 1));

        assertThat(existing.getMainImageUrl()).isEqualTo("/images/products/FLOWER-NEW/main.webp");
        verify(productMapper).updateById(existing);
    }

    @Test
    @DisplayName("修改不存在的商品 → 404")
    void updateNotFound() {
        assertThatThrownBy(() -> service.update(404L, saveReq("FLOWER-X", "", 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
