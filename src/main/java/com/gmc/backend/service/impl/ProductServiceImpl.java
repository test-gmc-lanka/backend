package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.ProductRequest;
import com.gmc.backend.dto.response.CategoryResponse;
import com.gmc.backend.dto.response.ProductResponse;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.Product;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.repository.ProductCategoryRepository;
import com.gmc.backend.repository.ProductRepository;
import com.gmc.backend.service.FileStorageService;
import com.gmc.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse getProductById(Long productId) {
        return toResponse(findById(productId));
    }

    @Override
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        return productRepository.findByCategory(category).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponse> getProductsByMaterial(String material) {
        return productRepository.findAll().stream()
                .filter(p -> material.equalsIgnoreCase(p.getMaterial()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        ProductCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .material(request.getMaterial())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .build();
        applyImages(product, request);
        return toResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = findById(productId);
        ProductCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(category);
        product.setMaterial(request.getMaterial());
        applyImages(product, request);
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        return toResponse(productRepository.save(product));
    }

    @Override
    public void deleteProduct(Long productId) {
        findById(productId);
        productRepository.deleteById(productId);
    }

    @Override
    public List<String> uploadImages(List<MultipartFile> files) {
        return fileStorageService.storeImages(files, "products");
    }

    /**
     * Copies the gallery from the request and picks the cover image. Falls back to the
     * legacy single imageUrl when no gallery is sent, so older clients keep working.
     */
    private void applyImages(Product product, ProductRequest request) {
        List<String> images = request.getImageUrls() == null
                ? new ArrayList<>()
                : request.getImageUrls().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(url -> !url.isEmpty())
                        .distinct()
                        .collect(Collectors.toCollection(ArrayList::new));
        String cover = request.getImageUrl();
        if (images.isEmpty() && cover != null && !cover.isBlank()) {
            images.add(cover.trim());
        }
        if (cover == null || !images.contains(cover.trim())) {
            cover = images.isEmpty() ? null : images.get(0);
        }
        if (product.getImageUrls() == null) {
            product.setImageUrls(new ArrayList<>());
        }
        product.getImageUrls().clear();
        product.getImageUrls().addAll(images);
        product.setImageUrl(cover == null ? null : cover.trim());
    }

    private Product findById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    public ProductResponse toResponse(Product product) {
        CategoryResponse categoryResponse = CategoryResponse.builder()
                .categoryId(product.getCategory().getCategoryId())
                .name(product.getCategory().getName())
                .build();
        return ProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(categoryResponse)
                .material(product.getMaterial())
                .imageUrl(product.getImageUrl())
                .imageUrls(galleryOf(product))
                .stockQuantity(product.getStockQuantity())
                .build();
    }

    // Products created before galleries existed only have the single imageUrl
    private List<String> galleryOf(Product product) {
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            return new ArrayList<>(product.getImageUrls());
        }
        return product.getImageUrl() == null ? List.of() : List.of(product.getImageUrl());
    }
}
