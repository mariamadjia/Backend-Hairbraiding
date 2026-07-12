package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.AdminCategoryDTO;
import org.example.backendbraiding.dto.CategoryGalleryDTO;
import org.example.backendbraiding.dto.CategorySummaryDTO;
import org.example.backendbraiding.dto.SubcategorySummaryDTO;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/admin")
    public ResponseEntity<Map<String, Object>> getAllCategoriesForAdmin() {
        return ResponseEntity.ok(categoryService.getAllCategoriesForAdmin());
    }

    // New optimized endpoints for admin lazy loading
    @GetMapping("/admin/summaries")
    public ResponseEntity<List<CategorySummaryDTO>> getCategorySummariesForAdmin() {
        return ResponseEntity.ok(categoryService.getCategorySummariesForAdmin());
    }

    @GetMapping("/admin/{slug}")
    public ResponseEntity<AdminCategoryDTO> getCategoryBySlugForAdmin(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getCategoryBySlugForAdmin(slug));
    }

    // New optimized endpoints for subcategory lazy loading
    @GetMapping("/admin/{categorySlug}/subcategories")
    public ResponseEntity<List<SubcategorySummaryDTO>> getSubcategorySummariesForAdmin(@PathVariable String categorySlug) {
        return ResponseEntity.ok(categoryService.getSubcategorySummariesForAdmin(categorySlug));
    }

    @GetMapping("/gallery")
    public ResponseEntity<List<CategoryGalleryDTO>> getCategoriesForGallery() {
        return ResponseEntity.ok(categoryService.getAllCategoriesForGallery());
    }

    @GetMapping("/gallery-cards")
    public ResponseEntity<List<CategoryGalleryDTO>> getCategoriesForGalleryCards() {
        return ResponseEntity.ok(categoryService.getAllCategoriesForGalleryCards());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<Category> getCategoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getCategoryBySlug(slug));
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        return ResponseEntity.ok(categoryService.createCategory(category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Category category = categoryService.getCategoryById(id);
        
        if (updates.containsKey("displayOrder") && updates.get("displayOrder") != null) {
            category.setDisplayOrder(Integer.parseInt(updates.get("displayOrder").toString()));
            return ResponseEntity.ok(categoryService.updateCategory(id, category));
        }
        
        // Full update - preserve existing values if not provided
        Category categoryDetails = categoryService.getCategoryById(id);
        if (updates.containsKey("name")) categoryDetails.setName(updates.get("name").toString());
        if (updates.containsKey("slug")) categoryDetails.setSlug(updates.get("slug").toString());
        if (updates.containsKey("summary")) categoryDetails.setSummary(updates.get("summary").toString());
        if (updates.containsKey("image")) categoryDetails.setImage(updates.get("image").toString());
        if (updates.containsKey("flippingImages")) {
            @SuppressWarnings("unchecked")
            List<String> flippingImages = (List<String>) updates.get("flippingImages");
            categoryDetails.setFlippingImages(flippingImages);
        }
        
        return ResponseEntity.ok(categoryService.updateCategory(id, categoryDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("message", "Category deleted successfully"));
    }

    @PutMapping("/{id}/flipping-images")
    public ResponseEntity<Category> updateFlippingImages(
            @PathVariable Long id,
            @RequestBody List<String> flippingImages) {
        return ResponseEntity.ok(categoryService.updateFlippingImages(id, flippingImages));
    }

    @PostMapping("/reorder")
    public ResponseEntity<Map<String, String>> reorderCategories(@RequestBody List<Long> categoryIds) {
        categoryService.reorderCategories(categoryIds);
        return ResponseEntity.ok(Map.of("message", "Categories reordered successfully"));
    }
}
