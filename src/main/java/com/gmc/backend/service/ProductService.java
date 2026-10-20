package com.gmc.backend.service;

import com.gmc.backend.dto.request.ProductRequest;
import com.gmc.backend.dto.response.ProductResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long productId);

    List<ProductResponse> getProductsByCategory(Long categoryId);

    List<ProductResponse> searchProducts(String name);

    List<ProductResponse> getProductsByMaterial(String material);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long productId, ProductRequest request);

    /** Archives the product (it stays on past orders and reviews); see setActive to restore. */
    void deleteProduct(Long productId);

    ProductResponse setActive(Long productId, boolean active);

    /** Admin view: every product, including archived ones. */
    List<ProductResponse> getAllProductsIncludingArchived();

    List<String> uploadImages(List<MultipartFile> files);
}
