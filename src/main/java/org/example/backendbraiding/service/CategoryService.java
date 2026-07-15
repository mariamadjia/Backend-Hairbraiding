package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.AdminCategoryDTO;
import org.example.backendbraiding.dto.AdminServiceItemDTO;
import org.example.backendbraiding.dto.AdminSubcategoryDTO;
import org.example.backendbraiding.dto.CategoryGalleryDTO;
import org.example.backendbraiding.dto.CategorySummaryDTO;
import org.example.backendbraiding.dto.CompleteCategoryRequest;
import org.example.backendbraiding.dto.ImageResponse;
import org.example.backendbraiding.dto.LengthOptionDTO;
import org.example.backendbraiding.dto.SubcategoryGalleryDTO;
import org.example.backendbraiding.dto.SubcategorySummaryDTO;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.GalleryImage;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.GalleryImageRepository;
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
    private final GalleryImageRepository galleryImageRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository,
            GalleryImageRepository galleryImageRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.galleryImageRepository = galleryImageRepository;
    }

    @org.springframework.cache.annotation.Cacheable(value = "publicCategories")
    public Map<String, Object> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return Map.of(
            "defaultBookingUrl", "https://calendly.com/djonretglo",
            "categories", categories
        );
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "bookingCategories")
    public List<Category> getAllCategoriesData() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        // Force-load all lazy collections within the transaction
        for (Category category : categories) {
            category.getSubcategories().forEach(sub -> {
                sub.getItems().forEach(item -> item.getLengthOptions().size());
            });
            category.getItems().forEach(item -> item.getLengthOptions().size());
        }
        return categories;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAllCategoriesForAdmin() {
        List<Category> categories = categoryRepository.findAllWithSubcategoriesAndItems();
        // Force-load lengthOptions within the transaction
        categories.forEach(cat -> {
            cat.getSubcategories().forEach(sub -> sub.getItems().forEach(item -> item.getLengthOptions().size()));
            cat.getItems().forEach(item -> item.getLengthOptions().size());
        });
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
            optDto.setImageUrl(opt.getImageUrl());
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

            List<SubcategoryGalleryDTO> subDtos = cat.getSubcategories().stream().map(sub -> {
                SubcategoryGalleryDTO subDto = new SubcategoryGalleryDTO();
                subDto.setId(sub.getId());
                subDto.setName(sub.getName());
                subDto.setSlug(sub.getSlug());
                subDto.setImage(sub.getImage());
                List<String> galleryUrls = galleryImageRepository
                        .findBySubcategoryIdOrderByDisplayOrderAsc(sub.getId())
                        .stream()
                        .map(GalleryImage::getImageUrl)
                        .collect(Collectors.toList());
                subDto.setImages(galleryUrls.isEmpty() && sub.getImage() != null
                        ? List.of(sub.getImage())
                        : galleryUrls);
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

    @org.springframework.cache.annotation.Cacheable(value = "bookingCategory", key = "#slug")
    public Category getCategoryBySlugForBooking(String slug) {
        Category category = categoryRepository.findBySlug(slug).orElse(null);
        if (category != null) {
            // Eagerly load all nested relationships for caching
            category.getSubcategories().forEach(sub -> {
                sub.getItems().forEach(item -> {
                    item.getLengthOptions().size(); // Force load
                });
            });
            category.getItems().forEach(item -> {
                item.getLengthOptions().size(); // Force load
            });
        }
        return category;
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "bookingCategories", "publicCategories", "allCategories"}, allEntries = true)
    public Category createCategory(Category category) {
        if (categoryRepository.existsBySlug(category.getSlug())) {
            throw new RuntimeException("Category with slug already exists");
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "bookingCategories", "publicCategories", "allCategories"}, allEntries = true)
    public Category createCompleteCategory(CompleteCategoryRequest request) {
        Category existing = categoryRepository.findBySlug(request.getSlug()).orElse(null);
        if (existing != null) {
            return existing;
        }

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setSlug(request.getSlug().trim());
        category.setDisplayOrder(categoryRepository.findAll().size());

        List<GalleryImage> categoryImages = getGalleryImages(request.getCategoryImageIds());
        category.setFlippingImages(categoryImages.stream().map(GalleryImage::getImageUrl).collect(Collectors.toList()));

        for (int subIndex = 0; subIndex < request.getSubcategories().size(); subIndex++) {
            CompleteCategoryRequest.SubcategoryInput subInput = request.getSubcategories().get(subIndex);
            String subSlug = generateSlug(subInput.getName());
            if (subcategoryRepository.findBySlug(subSlug).isPresent()) {
                throw new IllegalArgumentException("Subcategory slug already exists: " + subSlug);
            }

            Subcategory subcategory = new Subcategory();
            subcategory.setName(subInput.getName().trim());
            subcategory.setSlug(subSlug);
            subcategory.setDisplayOrder(subIndex);
            subcategory.setCategory(category);
            category.getSubcategories().add(subcategory);

            for (CompleteCategoryRequest.ServiceInput serviceInput : subInput.getSizes()) {
                ServiceItem serviceItem = new ServiceItem();
                serviceItem.setName(serviceInput.getName().trim());
                serviceItem.setPrice("");
                serviceItem.setDescription("");
                serviceItem.setSubcategory(subcategory);
                subcategory.getItems().add(serviceItem);

                for (CompleteCategoryRequest.LengthInput lengthInput : serviceInput.getLengths()) {
                    LengthOption option = new LengthOption();
                    option.setName(lengthInput.getName().trim());
                    option.setPrice(lengthInput.getPrice().trim());
                    option.setNotes(lengthInput.getNotes());
                    option.setDuration(lengthInput.getDuration());
                    option.setServiceItem(serviceItem);
                    serviceItem.getLengthOptions().add(option);
                }
            }
        }

        Category saved = categoryRepository.saveAndFlush(category);
        attachCompleteCategoryImages(saved, request, categoryImages);
        return saved;
    }

    private List<GalleryImage> getGalleryImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        List<GalleryImage> images = galleryImageRepository.findAllById(ids);
        if (images.size() != ids.size()) {
            throw new IllegalArgumentException("One or more uploaded images were not found");
        }
        return images;
    }

    private void attachCompleteCategoryImages(Category category, CompleteCategoryRequest request, List<GalleryImage> categoryImages) {
        categoryImages.forEach(image -> image.setCategory(category));
        List<GalleryImage> changedImages = new ArrayList<>(categoryImages);

        for (int subIndex = 0; subIndex < request.getSubcategories().size(); subIndex++) {
            CompleteCategoryRequest.SubcategoryInput subInput = request.getSubcategories().get(subIndex);
            Subcategory subcategory = category.getSubcategories().get(subIndex);
            List<GalleryImage> subImages = getGalleryImages(subInput.getImageIds());
            subImages.forEach(image -> {
                image.setCategory(category);
                image.setSubcategory(subcategory);
            });
            if (!subImages.isEmpty()) subcategory.setImage(subImages.get(0).getImageUrl());
            changedImages.addAll(subImages);

            for (int sizeIndex = 0; sizeIndex < subInput.getSizes().size(); sizeIndex++) {
                CompleteCategoryRequest.ServiceInput serviceInput = subInput.getSizes().get(sizeIndex);
                ServiceItem serviceItem = subcategory.getItems().get(sizeIndex);
                for (int lengthIndex = 0; lengthIndex < serviceInput.getLengths().size(); lengthIndex++) {
                    Long imageId = serviceInput.getLengths().get(lengthIndex).getImageId();
                    if (imageId == null) continue;
                    GalleryImage image = galleryImageRepository.findById(imageId)
                            .orElseThrow(() -> new IllegalArgumentException("Uploaded length image was not found: " + imageId));
                    image.setCategory(category);
                    image.setSubcategory(subcategory);
                    image.setServiceItem(serviceItem);
                    serviceItem.getLengthOptions().get(lengthIndex).setImageUrl(image.getImageUrl());
                    changedImages.add(image);
                }
            }
        }

        galleryImageRepository.saveAll(changedImages);
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "bookingCategories", "publicCategories", "allCategories"}, allEntries = true)
    public Category updateCategory(Long id, Category categoryDetails) {
        Category category = getCategoryById(id);
        category.setName(categoryDetails.getName());
        category.setSlug(categoryDetails.getSlug());
        category.setSummary(categoryDetails.getSummary());
        category.setImage(categoryDetails.getImage());
        if (categoryDetails.getDisplayOrder() != null) {
            category.setDisplayOrder(categoryDetails.getDisplayOrder());
        }
        if (categoryDetails.getFlippingImages() != null) {
            category.setFlippingImages(new ArrayList<>(categoryDetails.getFlippingImages()));
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "bookingCategories", "publicCategories", "allCategories"}, allEntries = true)
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        // Remove gallery images first to avoid FK constraint violations
        List<GalleryImage> galleryImages = galleryImageRepository.findByCategoryIdOrderByDisplayOrderAsc(id);
        galleryImageRepository.deleteAll(galleryImages);
        categoryRepository.delete(category);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategory", "bookingCategories", "publicCategories", "allCategories"}, allEntries = true)
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

    // New optimized methods for admin lazy loading

    @Transactional(readOnly = true)
    public List<CategorySummaryDTO> getCategorySummariesForAdmin() {
        List<Category> categories = categoryRepository.findCategorySummaries();
        return categories.stream().map(cat -> {
            CategorySummaryDTO dto = new CategorySummaryDTO();
            dto.setId(cat.getId());
            dto.setName(cat.getName());
            dto.setSlug(cat.getSlug());
            dto.setDisplayOrder(cat.getDisplayOrder());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminCategoryDTO getCategoryBySlugForAdmin(String slug) {
        try {
            Category category = categoryRepository.findBySlugForAdmin(slug)
                    .orElseThrow(() -> new RuntimeException("Category not found"));

            return mapToAdminCategoryShellDTO(category);
        } catch (Exception e) {
            System.err.println("Error fetching category by slug for admin: " + slug);
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch category: " + e.getMessage(), e);
        }
    }

    private AdminCategoryDTO mapToAdminCategoryShellDTO(Category category) {
        AdminCategoryDTO dto = new AdminCategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        dto.setSummary(category.getSummary());
        dto.setImage(category.getImage());
        dto.setDisplayOrder(category.getDisplayOrder());
        dto.setFlippingImages(category.getFlippingImages() != null ? category.getFlippingImages() : new ArrayList<>());

        List<AdminSubcategoryDTO> subDtos = new ArrayList<>();
        if (category.getSubcategories() != null) {
            subDtos = category.getSubcategories().stream().map(sub -> {
                AdminSubcategoryDTO subDto = new AdminSubcategoryDTO();
                subDto.setId(sub.getId());
                subDto.setName(sub.getName());
                subDto.setSlug(sub.getSlug());
                subDto.setSummary(sub.getSummary());
                subDto.setImage(sub.getImage());
                subDto.setDisplayOrder(sub.getDisplayOrder());
                subDto.setItems(new ArrayList<>());
                
                // Include gallery images for this subcategory
                try {
                    if (sub.getId() != null) {
                        List<GalleryImage> galleryImages = galleryImageRepository.findBySubcategoryIdOrderByDisplayOrderAsc(sub.getId());
                        List<ImageResponse> galleryDtos = galleryImages.stream().map(img -> {
                            ImageResponse r = new ImageResponse();
                            r.setId(img.getId());
                            r.setImageUrl(img.getImageUrl());
                            r.setThumbnailUrl(img.getThumbnailUrl());
                            r.setTitle(img.getTitle());
                            r.setAltText(img.getAltText());
                            r.setDisplayOrder(img.getDisplayOrder());
                            r.setSubcategoryId(sub.getId());
                            r.setSubcategoryName(sub.getName());
                            return r;
                        }).collect(Collectors.toList());
                        subDto.setGalleryImages(galleryDtos);
                    } else {
                        subDto.setGalleryImages(new ArrayList<>());
                    }
                } catch (Exception e) {
                    System.err.println("Error fetching gallery images for subcategory: " + sub.getId());
                    e.printStackTrace();
                    subDto.setGalleryImages(new ArrayList<>());
                }
                
                return subDto;
            }).collect(Collectors.toList());
        }
        dto.setSubcategories(subDtos);
        dto.setItems(new ArrayList<>());

        return dto;
    }

    private AdminCategoryDTO mapToAdminCategoryDTO(Category category) {
        AdminCategoryDTO dto = new AdminCategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        dto.setSummary(category.getSummary());
        dto.setImage(category.getImage());
        dto.setDisplayOrder(category.getDisplayOrder());
        dto.setFlippingImages(category.getFlippingImages() != null ? category.getFlippingImages() : new ArrayList<>());

        // Map subcategories
        List<AdminSubcategoryDTO> subDtos = new ArrayList<>();
        if (category.getSubcategories() != null) {
            subDtos = category.getSubcategories().stream().map(sub -> {
                AdminSubcategoryDTO subDto = new AdminSubcategoryDTO();
                subDto.setId(sub.getId());
                subDto.setName(sub.getName());
                subDto.setSlug(sub.getSlug());
                subDto.setSummary(sub.getSummary());
                subDto.setImage(sub.getImage());
                subDto.setDisplayOrder(sub.getDisplayOrder());

                List<AdminServiceItemDTO> itemDtos = new ArrayList<>();
                if (sub.getItems() != null) {
                    itemDtos = sub.getItems().stream().map(this::mapToAdminServiceItemDTO).collect(Collectors.toList());
                }
                subDto.setItems(itemDtos);

                return subDto;
            }).collect(Collectors.toList());
        }
        dto.setSubcategories(subDtos);

        // Map direct category items
        List<AdminServiceItemDTO> itemDtos = new ArrayList<>();
        if (category.getItems() != null) {
            itemDtos = category.getItems().stream().map(this::mapToAdminServiceItemDTO).collect(Collectors.toList());
        }
        dto.setItems(itemDtos);

        return dto;
    }

    // New optimized methods for subcategory lazy loading

    @Transactional(readOnly = true)
    public List<SubcategorySummaryDTO> getSubcategorySummariesForAdmin(String categorySlug) {
        List<Subcategory> subcategories = subcategoryRepository.findSubcategorySummariesByCategorySlug(categorySlug);
        return subcategories.stream().map(sub -> {
            SubcategorySummaryDTO dto = new SubcategorySummaryDTO();
            dto.setId(sub.getId());
            dto.setName(sub.getName());
            dto.setSlug(sub.getSlug());
            dto.setDisplayOrder(sub.getDisplayOrder());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminSubcategoryDTO getSubcategoryBySlugForAdmin(String slug) {
        Subcategory subcategory = subcategoryRepository.findBySlugForAdmin(slug)
                .orElseThrow(() -> new RuntimeException("Subcategory not found"));

        // Items are JOIN FETCHed. Load lengthOptions + element collections via
        // separate queries (avoids MultipleBagFetchException).
        subcategory.getItems().forEach(item -> {
            item.getLengthOptions().size();
            item.getImages().size();
            item.getAvailableSizes().size();
            item.getHairTextures().size();
        });

        return mapToAdminSubcategoryDTO(subcategory);
    }

    private AdminSubcategoryDTO mapToAdminSubcategoryDTO(Subcategory subcategory) {
        AdminSubcategoryDTO dto = new AdminSubcategoryDTO();
        dto.setId(subcategory.getId());
        dto.setName(subcategory.getName());
        dto.setSlug(subcategory.getSlug());
        dto.setSummary(subcategory.getSummary());
        dto.setImage(subcategory.getImage());
        dto.setDisplayOrder(subcategory.getDisplayOrder());

        List<AdminServiceItemDTO> itemDtos = new ArrayList<>();
        if (subcategory.getItems() != null) {
            itemDtos = subcategory.getItems().stream().map(this::mapToAdminServiceItemDTO).collect(Collectors.toList());
        }
        dto.setItems(itemDtos);

        // Include gallery images so frontend doesn't need a second request
        List<GalleryImage> galleryImages = galleryImageRepository.findBySubcategoryIdOrderByDisplayOrderAsc(subcategory.getId());
        List<ImageResponse> galleryDtos = galleryImages.stream().map(img -> {
            ImageResponse r = new ImageResponse();
            r.setId(img.getId());
            r.setImageUrl(img.getImageUrl());
            r.setThumbnailUrl(img.getThumbnailUrl());
            r.setTitle(img.getTitle());
            r.setAltText(img.getAltText());
            r.setDisplayOrder(img.getDisplayOrder());
            r.setSubcategoryId(subcategory.getId());
            r.setSubcategoryName(subcategory.getName());
            return r;
        }).collect(Collectors.toList());
        dto.setGalleryImages(galleryDtos);

        return dto;
    }
}
