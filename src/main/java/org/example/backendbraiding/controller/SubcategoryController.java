package org.example.backendbraiding.controller;

import jakarta.validation.Valid;
import org.example.backendbraiding.dto.AdminSubcategoryDTO;
import org.example.backendbraiding.dto.SubcategoryRequestDTO;
import org.example.backendbraiding.dto.SubcategoryUpdateDTO;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.service.CategoryService;
import org.example.backendbraiding.service.SubcategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subcategories")
public class SubcategoryController {
    private final SubcategoryService subcategoryService;
    private final CategoryService categoryService;
    private final org.example.backendbraiding.repository.SubcategoryRepository subcategoryRepository;

    public SubcategoryController(SubcategoryService subcategoryService, CategoryService categoryService, org.example.backendbraiding.repository.SubcategoryRepository subcategoryRepository) {
        this.subcategoryService = subcategoryService;
        this.categoryService = categoryService;
        this.subcategoryRepository = subcategoryRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Subcategory> getSubcategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(subcategoryService.getSubcategoryById(id));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<java.util.List<Subcategory>> getSubcategoriesByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(subcategoryRepository.findByCategoryIdOrderByDisplayOrderAsc(categoryId));
    }

    // New optimized endpoint for admin lazy loading
    @GetMapping("/admin/{slug}")
    public ResponseEntity<AdminSubcategoryDTO> getSubcategoryBySlugForAdmin(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getSubcategoryBySlugForAdmin(slug));
    }

    @PostMapping
    public ResponseEntity<Subcategory> createSubcategory(@Valid @RequestBody SubcategoryRequestDTO request) {
        return ResponseEntity.ok(subcategoryService.createSubcategory(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Subcategory> updateSubcategory(
            @PathVariable Long id,
            @Valid @RequestBody SubcategoryUpdateDTO request) {
        return ResponseEntity.ok(subcategoryService.updateSubcategory(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSubcategory(@PathVariable Long id) {
        subcategoryService.deleteSubcategory(id);
        return ResponseEntity.ok(Map.of("message", "Subcategory deleted successfully"));
    }
}
