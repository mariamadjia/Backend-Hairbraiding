package org.example.backendbraiding.service;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SubcategoryService {
    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final ImageSyncService imageSyncService;

    public SubcategoryService(SubcategoryRepository subcategoryRepository, CategoryRepository categoryRepository, ImageSyncService imageSyncService) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.imageSyncService = imageSyncService;
    }

    public Subcategory getSubcategoryById(Long id) {
        return subcategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subcategory not found"));
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "allCategories"}, allEntries = true)
    public Subcategory createSubcategory(String name, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        
        Subcategory subcategory = new Subcategory();
        subcategory.setName(name);
        
        // Auto-generate slug from name
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        subcategory.setSlug(slug);
        subcategory.setCategory(category);
        
        return subcategoryRepository.save(subcategory);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "allCategories"}, allEntries = true)
    public Subcategory updateSubcategory(Long id, Map<String, String> updates) {
        Subcategory subcategory = getSubcategoryById(id);
        boolean imageUpdated = false;
        
        if (updates.containsKey("name")) {
            subcategory.setName(updates.get("name"));
            // Auto-generate slug from name
            String slug = updates.get("name").toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .trim();
            subcategory.setSlug(slug);
        }
        
        if (updates.containsKey("summary")) {
            subcategory.setSummary(updates.get("summary"));
        }
        
        if (updates.containsKey("image")) {
            subcategory.setImage(updates.get("image"));
            imageUpdated = true;
        }
        
        if (updates.containsKey("displayOrder")) {
            subcategory.setDisplayOrder(Integer.parseInt(updates.get("displayOrder")));
        }
        
        Subcategory saved = subcategoryRepository.save(subcategory);
        
        // Sync to gallery if image was updated
        if (imageUpdated) {
            imageSyncService.syncSubcategoryImageToGallery(saved);
        }
        
        return saved;
    }

    @Transactional
    public void deleteSubcategory(Long id) {
        Subcategory subcategory = getSubcategoryById(id);
        subcategoryRepository.delete(subcategory);
    }
}
