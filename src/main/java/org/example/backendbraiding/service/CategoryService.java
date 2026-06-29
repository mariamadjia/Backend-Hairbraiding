package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Map<String, Object> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return Map.of(
            "defaultBookingUrl", "https://calendly.com/djonretglo",
            "categories", categories
        );
    }

    public List<Category> getAllCategoriesData() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    @Transactional
    public Category createCategory(Category category) {
        if (categoryRepository.existsBySlug(category.getSlug())) {
            throw new RuntimeException("Category with slug already exists");
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = getCategoryById(id);
        category.setName(categoryDetails.getName());
        category.setSlug(categoryDetails.getSlug());
        category.setSummary(categoryDetails.getSummary());
        category.setImage(categoryDetails.getImage());
        if (categoryDetails.getDisplayOrder() != null) {
            category.setDisplayOrder(categoryDetails.getDisplayOrder());
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        categoryRepository.delete(category);
    }

    @Transactional
    public Category updateFlippingImages(Long id, List<String> flippingImages) {
        Category category = getCategoryById(id);
        category.setFlippingImages(flippingImages);
        return categoryRepository.save(category);
    }

    @Transactional
    public void reorderCategories(List<Long> categoryIds) {
        for (int i = 0; i < categoryIds.size(); i++) {
            final int displayOrder = i;
            Long categoryId = categoryIds.get(i);
            categoryRepository.findById(categoryId).ifPresent(category -> {
                category.setDisplayOrder(displayOrder);
                categoryRepository.save(category);
            });
        }
    }
}
