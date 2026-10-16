package com.gmc.backend.service.impl;

import com.gmc.backend.repository.AppointmentRepository;
import com.gmc.backend.repository.ProductRepository;
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
    private final ProductRepository productRepository;
    private final AppointmentRepository appointmentRepository;
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
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Category '" + name + "' already exists");
        }
        ProductCategory category = ProductCategory.builder()
                .name(name)
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        ProductCategory category = findById(categoryId);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndCategoryIdNot(name, categoryId)) {
            throw new BusinessRuleException("Category '" + name + "' already exists");
        }
        category.setName(name);
        return toResponse(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long categoryId) {
        ProductCategory category = findById(categoryId);
        if (productRepository.existsByCategory(category) || appointmentRepository.existsByCategory(category)) {
            throw new BusinessRuleException("'" + category.getName() + "' is still used by products or "
                    + "appointments. Move them to another category first.");
        }
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
