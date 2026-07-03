package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.service.SubcategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subcategories")
@CrossOrigin(origins = {"https://hair-braiding-coral.vercel.app", "http://localhost:3000", "http://localhost:3001"})
public class SubcategoryController {
    private final SubcategoryService subcategoryService;
    private final org.example.backendbraiding.repository.SubcategoryRepository subcategoryRepository;

    public SubcategoryController(SubcategoryService subcategoryService, org.example.backendbraiding.repository.SubcategoryRepository subcategoryRepository) {
        this.subcategoryService = subcategoryService;
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

    @PostMapping
    public ResponseEntity<Subcategory> createSubcategory(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        Long categoryId = ((Number) request.get("categoryId")).longValue();
        return ResponseEntity.ok(subcategoryService.createSubcategory(name, categoryId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Subcategory> updateSubcategory(
            @PathVariable Long id,
            @RequestBody Map<String, String> updates) {
        return ResponseEntity.ok(subcategoryService.updateSubcategory(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSubcategory(@PathVariable Long id) {
        subcategoryService.deleteSubcategory(id);
        return ResponseEntity.ok(Map.of("message", "Subcategory deleted successfully"));
    }
}
