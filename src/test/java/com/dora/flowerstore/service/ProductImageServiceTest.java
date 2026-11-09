package com.dora.flowerstore.service;

import com.dora.flowerstore.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductImageService 商品圖片路徑與容錯")
class ProductImageServiceTest {

    /** 假裝存在於 classpath:static 底下的圖檔 */
    private final Set<String> existing = new HashSet<>();
    private ResourceLoader loader;
    private ProductImageService service;

    @BeforeEach
    void setUp() {
        loader = mock(ResourceLoader.class);
        when(loader.getResource(anyString())).thenAnswer(inv -> {
            String location = inv.getArgument(0);
            Resource r = mock(Resource.class);
            when(r.exists()).thenReturn(existing.contains(location.replace("classpath:static", "")));
            return r;
        });
        service = new ProductImageService(loader);
    }

    private static Product product(String code, String mainUrl) {
        Product p = new Product();
        p.setProductCode(code);
        p.setMainImageUrl(mainUrl);
        return p;
    }

    @Test
    @DisplayName("主圖存在 → 回傳主圖路徑")
    void mainExists() {
        existing.add("/images/products/FLOWER-ROSE-RED/main.webp");
        assertThat(service.resolveMain(product("FLOWER-ROSE-RED", null)))
                .isEqualTo("/images/products/FLOWER-ROSE-RED/main.webp");
    }

    @Test
    @DisplayName("主圖不存在 → 回傳預設花卉圖")
    void mainMissing() {
        assertThat(service.resolveMain(product("FLOWER-NEW", null))).isEqualTo(ProductImageService.PLACEHOLDER);
    }

    @Test
    @DisplayName("資料庫存的是 placeholder，但規則路徑已有圖 → 改用新圖")
    void placeholderInDbButFileAdded() {
        existing.add("/images/products/FLOWER-PEONY-PINK/main.webp");
        assertThat(service.resolveMain(product("FLOWER-PEONY-PINK", ProductImageService.PLACEHOLDER)))
                .isEqualTo("/images/products/FLOWER-PEONY-PINK/main.webp");
    }

    @Test
    @DisplayName("外部網址或路徑穿越 → 一律不採用，給 placeholder")
    void rejectUnsafeUrl() {
        assertThat(service.resolveMain(product("X", "https://evil.example/a.webp")))
                .isEqualTo(ProductImageService.PLACEHOLDER);
        assertThat(service.resolveMain(product("X", "/images/../application.yml")))
                .isEqualTo(ProductImageService.PLACEHOLDER);
    }

    @Test
    @DisplayName("細節圖依序找，遇到缺號就停（有 1、2、4 只回傳 1、2）")
    void detailsStopAtGap() {
        String base = "/images/products/FLOWER-ROSE-RED/";
        existing.add(base + "detail_1.webp");
        existing.add(base + "detail_2.webp");
        existing.add(base + "detail_4.webp");
        assertThat(service.resolveDetails(product("FLOWER-ROSE-RED", null)))
                .containsExactly(base + "detail_1.webp", base + "detail_2.webp");
    }

    @Test
    @DisplayName("同一路徑只查一次檔案系統；clearCache 後重新查")
    void cache() {
        Product p = product("FLOWER-ROSE-RED", null);
        service.resolveMain(p);
        service.resolveMain(p);
        verify(loader, times(1)).getResource("classpath:static/images/products/FLOWER-ROSE-RED/main.webp");

        service.clearCache();
        service.resolveMain(p);
        verify(loader, times(2)).getResource("classpath:static/images/products/FLOWER-ROSE-RED/main.webp");
        verify(loader, never()).getResource("classpath:static/images/../x");
    }
}
