package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.SubcategoryRequestDTO;
import org.example.backendbraiding.dto.SubcategoryUpdateDTO;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.exception.SlugAlreadyExistsException;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", id));
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "allCategories"}, allEntries = true)
    public Subcategory createSubcategory(SubcategoryRequestDTO request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        
        // Generate slug from name
        String slug = generateSlug(request.getName());
        
        // Check if slug already exists
        if (subcategoryRepository.findBySlug(slug).isPresent()) {
            throw new SlugAlreadyExistsException(slug);
        }
        
        Subcategory subcategory = new Subcategory();
        subcategory.setName(request.getName());
        subcategory.setSlug(slug);
        subcategory.setCategory(category);
        subcategory.setSummary(request.getSummary());
        subcategory.setImage(request.getImage());
        subcategory.setDisplayOrder(request.getDisplayOrder());
        
        return subcategoryRepository.save(subcategory);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "allCategories"}, allEntries = true)
    public Subcategory updateSubcategory(Long id, SubcategoryUpdateDTO request) {
        Subcategory subcategory = getSubcategoryById(id);
        boolean imageUpdated = false;
        
        if (request.getName() != null && !request.getName().isBlank()) {
            String newSlug = generateSlug(request.getName());
            
            // Check if new slug already exists (and belongs to different subcategory)
            subcategoryRepository.findBySlug(newSlug).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new SlugAlreadyExistsException(newSlug);
                }
            });
            
            subcategory.setName(request.getName());
            subcategory.setSlug(newSlug);
        }
        
        if (request.getSummary() != null) {
            subcategory.setSummary(request.getSummary());
        }
        
        if (request.getImage() != null) {
            subcategory.setImage(request.getImage());
            imageUpdated = true;
        }
        
        if (request.getDisplayOrder() != null) {
            subcategory.setDisplayOrder(Integer.parseInt(request.getDisplayOrder()));
        }
        
        Subcategory saved = subcategoryRepository.save(subcategory);
        
        // Sync to gallery if image was updated
        if (imageUpdated) {
            imageSyncService.syncSubcategoryImageToGallery(saved);
        }
        
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "allCategories"}, allEntries = true)
    public void deleteSubcategory(Long id) {
        Subcategory subcategory = getSubcategoryById(id);
        subcategoryRepository.delete(subcategory);
    }
    
    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
