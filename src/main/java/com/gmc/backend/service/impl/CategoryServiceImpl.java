package com.gmc.backend.service.impl;

import com.gmc.backend.dto.request.CategoryRequest;
import com.gmc.backend.dto.response.CategoryResponse;
import com.gmc.backend.exception.BusinessRuleException;
import com.gmc.backend.exception.ResourceNotFoundException;
import com.gmc.backend.model.ProductCategory;
import com.gmc.backend.repository.ProductCategoryRepository;
import com.gmc.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final ProductCategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategoryById(Long categoryId) {
        return toResponse(findById(categoryId));
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new BusinessRuleException("Category '" + request.getName() + "' already exists");
        }
        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        ProductCategory category = findById(categoryId);
        category.setName(request.getName());
        return toResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long categoryId) {
        findById(categoryId);
        categoryRepository.deleteById(categoryId);
    }

    private ProductCategory findById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    public CategoryResponse toResponse(ProductCategory category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .build();
    }
}
