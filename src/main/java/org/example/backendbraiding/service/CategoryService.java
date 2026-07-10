package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.AdminCategoryDTO;
import org.example.backendbraiding.dto.AdminServiceItemDTO;
import org.example.backendbraiding.dto.AdminSubcategoryDTO;
import org.example.backendbraiding.dto.CategoryGalleryDTO;
import org.example.backendbraiding.dto.LengthOptionDTO;
import org.example.backendbraiding.dto.SubcategoryGalleryDTO;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
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

    @Transactional(readOnly = true)
    public Map<String, Object> getAllCategoriesForAdmin() {
        List<Category> categories = categoryRepository.findAllWithSubcategoriesAndItems();
        List<AdminCategoryDTO> adminDtos = categories.stream().map(cat -> {
            AdminCategoryDTO dto = new AdminCategoryDTO();
            dto.setId(cat.getId());
            dto.setName(cat.getName());
            dto.setSlug(cat.getSlug());
            dto.setSummary(cat.getSummary());
            dto.setImage(cat.getImage());
            dto.setDisplayOrder(cat.getDisplayOrder());
            dto.setFlippingImages(cat.getFlippingImages() != null ? cat.getFlippingImages() : new ArrayList<>());

            // Map subcategories with null safety
            List<AdminSubcategoryDTO> subDtos = new ArrayList<>();
            if (cat.getSubcategories() != null) {
                subDtos = cat.getSubcategories().stream().map(sub -> {
                    AdminSubcategoryDTO subDto = new AdminSubcategoryDTO();
                    subDto.setId(sub.getId());
                    subDto.setName(sub.getName());
                    subDto.setSlug(sub.getSlug());
                    subDto.setSummary(sub.getSummary());
                    subDto.setImage(sub.getImage());
                    subDto.setDisplayOrder(sub.getDisplayOrder());

                    // Map service items for subcategory with null safety
                    List<AdminServiceItemDTO> itemDtos = new ArrayList<>();
                    if (sub.getItems() != null) {
                        itemDtos = sub.getItems().stream().map(item -> {
                            return mapToAdminServiceItemDTO(item);
                        }).collect(Collectors.toList());
                    }
                    subDto.setItems(itemDtos);

                    return subDto;
                }).collect(Collectors.toList());
            }
            dto.setSubcategories(subDtos);

            // Map service items for category with null safety
            List<AdminServiceItemDTO> itemDtos = new ArrayList<>();
            if (cat.getItems() != null) {
                itemDtos = cat.getItems().stream().map(item -> {
                    return mapToAdminServiceItemDTO(item);
                }).collect(Collectors.toList());
            }
            dto.setItems(itemDtos);

            return dto;
        }).collect(Collectors.toList());

        return Map.of(
            "defaultBookingUrl", "https://calendly.com/djonretglo",
            "categories", adminDtos
        );
    }

    private AdminServiceItemDTO mapToAdminServiceItemDTO(ServiceItem item) {
        AdminServiceItemDTO dto = new AdminServiceItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setPrice(item.getPrice());
        dto.setDescription(item.getDescription());
        dto.setNotes(item.getNotes());
        dto.setImage(item.getImage());
        dto.setImages(item.getImages());
        dto.setLink(item.getLink());
        dto.setObjectPosition(item.getObjectPosition());
        dto.setAvailableSizes(item.getAvailableSizes());
        dto.setHairTextures(item.getHairTextures());

        // Map length options
        List<LengthOptionDTO> lengthOptionDtos = item.getLengthOptions().stream().map(opt -> {
            LengthOptionDTO optDto = new LengthOptionDTO();
            optDto.setId(opt.getId());
            optDto.setName(opt.getName());
            optDto.setPrice(opt.getPrice());
            optDto.setDuration(opt.getDuration());
            optDto.setNotes(opt.getNotes());
            return optDto;
        }).collect(Collectors.toList());
        dto.setLengthOptions(lengthOptionDtos);

        return dto;
    }

    public List<CategoryGalleryDTO> getAllCategoriesForGallery() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return categories.stream().map(cat -> {
            CategoryGalleryDTO dto = new CategoryGalleryDTO();
            dto.setId(cat.getId());
            dto.setName(cat.getName());
            dto.setSlug(cat.getSlug());
            dto.setImage(cat.getImage());
            dto.setFlippingImages(cat.getFlippingImages());
            dto.setDisplayOrder(cat.getDisplayOrder());

            // Don't load subcategories items - just the basic subcategory data
            List<SubcategoryGalleryDTO> subDtos = cat.getSubcategories().stream().map(sub -> {
                SubcategoryGalleryDTO subDto = new SubcategoryGalleryDTO();
                subDto.setId(sub.getId());
                subDto.setName(sub.getName());
                subDto.setSlug(sub.getSlug());
                subDto.setImage(sub.getImage());
                // Subcategory doesn't have images field, leave as null
                return subDto;
            }).collect(Collectors.toList());

            dto.setSubcategories(subDtos);
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryGalleryDTO> getAllCategoriesForGalleryCards() {
        List<Category> categories =
                categoryRepository.findAllForGalleryCards();

        if (categories.isEmpty()) {
            return List.of();
        }

        List<Long> categoryIds = categories.stream()
                .map(Category::getId)
                .collect(Collectors.toList());

        Map<Long, List<String>> fallbackImagesByCategoryId =
                subcategoryRepository.findGalleryCardImageSources(categoryIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                subcategory -> subcategory.getCategory().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        Subcategory::getImage,
                                        Collectors.toList()
                                )
                        ));

        return categories.stream()
                .map(category -> {
                    CategoryGalleryDTO dto = new CategoryGalleryDTO();

                    dto.setId(category.getId());
                    dto.setName(category.getName());
                    dto.setSlug(category.getSlug());
                    dto.setImage(category.getImage());
                    dto.setDisplayOrder(category.getDisplayOrder());

                    // These are manually chosen rotating images.
                    dto.setFlippingImages(
                            category.getFlippingImages() != null
                                    ? new ArrayList<>(category.getFlippingImages())
                                    : new ArrayList<>()
                    );

                    // These are existing subcategory cover photos.
                    // They are only a fallback when flippingImages is empty.
                    List<String> fallbackImages = fallbackImagesByCategoryId
                            .getOrDefault(category.getId(), List.of())
                            .stream()
                            .limit(5)
                            .collect(Collectors.toList());

                    dto.setFallbackImages(fallbackImages);

                    // Keep this lightweight endpoint free of full subcategory data.
                    dto.setSubcategories(null);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "bookingCategory", key = "#slug")
    public Category getCategoryBySlugForBooking(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElse(null);
    }

    @Transactional
    @CacheEvict(value = "bookingCategory", allEntries = true)
    public Category createCategory(Category category) {
        if (categoryRepository.existsBySlug(category.getSlug())) {
            throw new RuntimeException("Category with slug already exists");
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = "bookingCategory", allEntries = true)
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
    @CacheEvict(value = "bookingCategory", allEntries = true)
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        categoryRepository.delete(category);
    }

    @Transactional
    @CacheEvict(value = "bookingCategory", allEntries = true)
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
