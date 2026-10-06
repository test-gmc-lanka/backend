package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ProductRequest;
import com.gmc.backend.dto.response.ProductResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.repository.ProductCategoryRepository;
import com.gmc.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl}. The repositories are mocked; the
 * real {@code toResponse} mapping inside the service is exercised.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductCategory category() {
        return ProductCategory.builder().categoryId(1L).name("CNC").build();
    }

    private Product product(Long id, String material) {
        return Product.builder()
                .productId(id)
                .name("Widget " + id)
                .description("desc")
                .price(new BigDecimal("50"))
                .category(category())
                .material(material)
                .imageUrl("img")
                .stockQuantity(5)
                .build();
    }

    @Test
    void getAllProducts_returnsMappedResponses() {
        when(productRepository.findAll()).thenReturn(List.of(product(2L, "Steel")));

        List<ProductResponse> result = productService.getAllProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Widget 2");
        assertThat(result.get(0).getCategory().getName()).isEqualTo("CNC");
    }

    @Test
    void getProductById_found_returnsResponse() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, "Steel")));

        ProductResponse result = productService.getProductById(2L);

        assertThat(result.getProductId()).isEqualTo(2L);
    }

    @Test
    void getProductById_notFound_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProductsByCategory_found_returnsResponses() {
        ProductCategory category = category();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.findByCategory(category)).thenReturn(List.of(product(2L, "Steel")));

        List<ProductResponse> result = productService.getProductsByCategory(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductsByCategory_categoryNotFound_throws() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductsByCategory(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchProducts_delegatesToRepository() {
        when(productRepository.findByNameContainingIgnoreCase("wid"))
                .thenReturn(List.of(product(2L, "Steel")));

        List<ProductResponse> result = productService.searchProducts("wid");

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductsByMaterial_filtersCaseInsensitively() {
        when(productRepository.findAll())
                .thenReturn(List.of(product(2L, "Steel"), product(3L, "Wood")));

        List<ProductResponse> result = productService.getProductsByMaterial("steel");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMaterial()).isEqualTo("Steel");
    }

    @Test
    void createProduct_success_savesAndReturns() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setName("New");
        request.setDescription("desc");
        request.setPrice(new BigDecimal("120"));
        request.setCategoryId(1L);
        request.setMaterial("Aluminium");
        request.setImageUrl("img");
        request.setStockQuantity(10);

        ProductResponse result = productService.createProduct(request);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getStockQuantity()).isEqualTo(10);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_nullStock_defaultsToZero() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setName("New");
        request.setPrice(new BigDecimal("120"));
        request.setCategoryId(1L);
        request.setStockQuantity(null);

        ProductResponse result = productService.createProduct(request);

        assertThat(result.getStockQuantity()).isEqualTo(0);
    }

    @Test
    void createProduct_categoryNotFound_throwsAndDoesNotSave() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        ProductRequest request = new ProductRequest();
        request.setCategoryId(1L);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_success_updatesFields() {
        Product existing = product(2L, "Steel");
        when(productRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setName("Updated");
        request.setPrice(new BigDecimal("200"));
        request.setCategoryId(1L);
        request.setMaterial("Brass");
        request.setStockQuantity(3);

        ProductResponse result = productService.updateProduct(2L, request);

        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(existing.getName()).isEqualTo("Updated");
        assertThat(existing.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void updateProduct_notFound_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ProductRequest request = new ProductRequest();
        request.setCategoryId(1L);

        assertThatThrownBy(() -> productService.updateProduct(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteProduct_existing_deletes() {
        when(productRepository.findById(2L)).thenReturn(Optional.of(product(2L, "Steel")));

        productService.deleteProduct(2L);

        verify(productRepository).deleteById(2L);
    }

    @Test
    void deleteProduct_notFound_throwsAndDoesNotDelete() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).deleteById(99L);
    }
}
