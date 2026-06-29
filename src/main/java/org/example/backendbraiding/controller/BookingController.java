package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.example.backendbraiding.service.CategoryService;
import org.example.backendbraiding.service.SubcategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/booking")
public class BookingController {
    private final CategoryService categoryService;
    private final SubcategoryService subcategoryService;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public BookingController(CategoryService categoryService, SubcategoryService subcategoryService, 
                           CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository) {
        this.categoryService = categoryService;
        this.subcategoryService = subcategoryService;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBookingData() {
        List<Category> categories = categoryService.getAllCategoriesData();
        List<Map<String, Object>> bookingCategories = new ArrayList<>();

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
                bookingSubcategory.put("image", subcategory.getImage());

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
