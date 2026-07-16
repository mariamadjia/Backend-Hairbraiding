package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.GalleryImage;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.GalleryImageRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.example.backendbraiding.service.CategoryService;
import org.example.backendbraiding.service.SubcategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/booking")
public class BookingController {
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);
    private final CategoryService categoryService;
    private final SubcategoryService subcategoryService;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final GalleryImageRepository galleryImageRepository;

    public BookingController(CategoryService categoryService, SubcategoryService subcategoryService,
                           CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository,
                           GalleryImageRepository galleryImageRepository) {
        this.categoryService = categoryService;
        this.subcategoryService = subcategoryService;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.galleryImageRepository = galleryImageRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBookingData() {
        List<Category> categories = categoryService.getAllCategoriesData();
        List<Map<String, Object>> bookingCategories = new ArrayList<>();

        // Batch fetch all gallery images for all subcategories to avoid N+1 queries
        List<Long> allSubcategoryIds = categories.stream()
                .flatMap(cat -> cat.getSubcategories().stream())
                .map(Subcategory::getId)
                .filter(Objects::nonNull)
                .toList();
        
        Map<Long, List<GalleryImage>> galleryImagesBySubcategory = new HashMap<>();
        if (!allSubcategoryIds.isEmpty()) {
            List<GalleryImage> allGalleryImages = galleryImageRepository
                    .findBySubcategoryIdsOrderBySubcategoryAndDisplayOrder(allSubcategoryIds);
            galleryImagesBySubcategory = allGalleryImages.stream()
                    .collect(Collectors.groupingBy(img -> img.getSubcategory().getId()));
        }

        for (Category category : categories) {
            Map<String, Object> bookingCategory = new HashMap<>();
            bookingCategory.put("name", category.getName());
            bookingCategory.put("slug", category.getSlug());
            bookingCategory.put("summary", category.getSummary());

            List<Map<String, Object>> bookingSubcategories = new ArrayList<>();

            // Process subcategories
            for (Subcategory subcategory : category.getSubcategories()) {
                Map<String, Object> bookingSubcategory = new HashMap<>();
                bookingSubcategory.put("name", subcategory.getName());
                bookingSubcategory.put("slug", subcategory.getSlug());
                bookingSubcategory.put("summary", subcategory.getSummary());

                List<GalleryImage> galleryImages = galleryImagesBySubcategory.getOrDefault(subcategory.getId(), List.of());
                List<String> subcategoryImages = galleryImages.stream()
                        .map(GalleryImage::getImageUrl)
                        .filter(Objects::nonNull)
                        .toList();
                bookingSubcategory.put("images", subcategoryImages);
                bookingSubcategory.put("galleryImages", mapGalleryImages(galleryImages));
                bookingSubcategory.put("image", !subcategoryImages.isEmpty() ? subcategoryImages.get(0) : subcategory.getImage());

                List<Map<String, Object>> bookingItems = new ArrayList<>();

                // Process service items
                for (ServiceItem item : subcategory.getItems()) {
                    Map<String, Object> bookingItem = new HashMap<>();
                    bookingItem.put("name", item.getName());
                    bookingItem.put("price", item.getPrice());
                    bookingItem.put("description", item.getDescription());
                    bookingItem.put("notes", item.getNotes());
                    bookingItem.put("image", item.getImage());
                    bookingItem.put("images", item.getImages());
                    bookingItem.put("sizePhotos", item.getSizePhotos());
                    bookingItem.put("link", item.getLink());
                    bookingItem.put("objectPosition", item.getObjectPosition());
                    bookingItem.put("availableSizes", item.getAvailableSizes());
                    bookingItem.put("hairTextures", item.getHairTextures());

                    // Convert length options
                    List<Map<String, Object>> lengthOptions = new ArrayList<>();
                    for (LengthOption option : item.getLengthOptions()) {
                        Map<String, Object> lengthOption = new HashMap<>();
                        lengthOption.put("name", option.getName());
                        lengthOption.put("price", option.getPrice());
                        lengthOption.put("duration", option.getDuration());
                        lengthOption.put("notes", option.getNotes());
                        lengthOptions.add(lengthOption);
                    }
                    bookingItem.put("lengthOptions", lengthOptions);

                    bookingItems.add(bookingItem);
                }

                bookingSubcategory.put("items", bookingItems);
                bookingSubcategories.add(bookingSubcategory);
            }

            bookingCategory.put("subcategories", bookingSubcategories);
            bookingCategories.add(bookingCategory);
        }

        return ResponseEntity.ok(bookingCategories);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Map<String, Object>> getBookingCategoryBySlug(
            @PathVariable String slug
    ) {
        log.info("Fetching booking category by slug: {}", slug);
        Category category = categoryService.getCategoryBySlugForBooking(slug);
        if (category == null) {
            log.error("Booking category not found for slug: {}", slug);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking category not found: " + slug
            );
        }

        return ResponseEntity.ok(mapBookingCategory(category));
    }

    private Map<String, Object> mapBookingCategory(Category category) {
        Map<String, Object> bookingCategory = new LinkedHashMap<>();

        bookingCategory.put("name", category.getName());
        bookingCategory.put("slug", category.getSlug());
        bookingCategory.put("summary", category.getSummary());
        bookingCategory.put("image", category.getImage());

        List<Map<String, Object>> bookingSubcategories = new ArrayList<>();

        List<Subcategory> subcategories = category.getSubcategories() != null
                ? category.getSubcategories()
                : List.of();

        // Batch fetch gallery images for all subcategories in this category
        List<Long> subcategoryIds = subcategories.stream()
                .map(Subcategory::getId)
                .filter(Objects::nonNull)
                .toList();
        
        Map<Long, List<GalleryImage>> galleryImagesBySubcategory = new HashMap<>();
        if (!subcategoryIds.isEmpty()) {
            List<GalleryImage> allGalleryImages = galleryImageRepository
                    .findBySubcategoryIdsOrderBySubcategoryAndDisplayOrder(subcategoryIds);
            galleryImagesBySubcategory = allGalleryImages.stream()
                    .collect(Collectors.groupingBy(img -> img.getSubcategory().getId()));
        }

        for (Subcategory subcategory : subcategories) {
            Map<String, Object> bookingSubcategory = new LinkedHashMap<>();

            bookingSubcategory.put("name", subcategory.getName());
            bookingSubcategory.put("slug", subcategory.getSlug());
            bookingSubcategory.put("summary", subcategory.getSummary());

            List<GalleryImage> galleryImages = galleryImagesBySubcategory.getOrDefault(subcategory.getId(), List.of());
            List<String> subcategoryImages = galleryImages.stream()
                    .map(GalleryImage::getImageUrl)
                    .filter(Objects::nonNull)
                    .toList();
            bookingSubcategory.put("images", subcategoryImages);
            bookingSubcategory.put("galleryImages", mapGalleryImages(galleryImages));
            bookingSubcategory.put("image", !subcategoryImages.isEmpty() ? subcategoryImages.get(0) : subcategory.getImage());

            List<Map<String, Object>> bookingItems = new ArrayList<>();

            List<ServiceItem> items = subcategory.getItems() != null
                    ? subcategory.getItems()
                    : List.of();

            for (ServiceItem item : items) {
                Map<String, Object> bookingItem = new LinkedHashMap<>();

                bookingItem.put("name", item.getName());
                bookingItem.put("price", item.getPrice());
                bookingItem.put("description", item.getDescription());
                bookingItem.put("notes", item.getNotes());
                bookingItem.put("image", item.getImage());
                bookingItem.put("images", item.getImages());
                bookingItem.put("sizePhotos", item.getSizePhotos());
                bookingItem.put("link", item.getLink());
                bookingItem.put("objectPosition", item.getObjectPosition());
                bookingItem.put("availableSizes", item.getAvailableSizes());
                bookingItem.put("hairTextures", item.getHairTextures());

                List<Map<String, Object>> lengthOptions = new ArrayList<>();

                List<LengthOption> options = item.getLengthOptions() != null
                        ? item.getLengthOptions()
                        : List.of();

                for (LengthOption option : options) {
                    Map<String, Object> lengthOption = new LinkedHashMap<>();

                    lengthOption.put("name", option.getName());
                    lengthOption.put("price", option.getPrice());
                    lengthOption.put("duration", option.getDuration());
                    lengthOption.put("notes", option.getNotes());

                    lengthOptions.add(lengthOption);
                }

                bookingItem.put("lengthOptions", lengthOptions);
                bookingItems.add(bookingItem);
            }

            bookingSubcategory.put("items", bookingItems);
            bookingSubcategories.add(bookingSubcategory);
        }

        bookingCategory.put("subcategories", bookingSubcategories);

        return bookingCategory;
    }

    private List<Map<String, Object>> mapGalleryImages(List<GalleryImage> images) {
        return images.stream()
                .map(image -> {
                    Map<String, Object> galleryImage = new LinkedHashMap<>();
                    galleryImage.put("id", image.getId());
                    galleryImage.put("imageUrl", image.getImageUrl());
                    galleryImage.put("thumbnailUrl", image.getThumbnailUrl());
                    galleryImage.put("title", image.getTitle());
                    galleryImage.put("altText", image.getAltText());
                    galleryImage.put("displayOrder", image.getDisplayOrder());
                    return galleryImage;
                })
                .toList();
    }

    @PostMapping("/populate-images")
    @Transactional
    public ResponseEntity<Map<String, String>> populateItemImages() {
        int itemsUpdated = 0;
        List<Category> categories = categoryRepository.findAll();

        for (Category category : categories) {
            for (Subcategory subcategory : category.getSubcategories()) {
                String subcategoryImage = subcategory.getImage();
                if (subcategoryImage == null || subcategoryImage.isEmpty()) {
                    continue;
                }

                for (ServiceItem item : subcategory.getItems()) {
                    if (item.getImage() == null || item.getImage().isEmpty()) {
                        item.setImage(subcategoryImage);
                        itemsUpdated++;
                    }
                }
                subcategoryRepository.save(subcategory);
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Item images populated successfully",
            "itemsUpdated", String.valueOf(itemsUpdated)
        ));
    }
}
