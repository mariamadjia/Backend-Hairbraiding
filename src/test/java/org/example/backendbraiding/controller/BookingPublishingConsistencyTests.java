package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.GalleryImageRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.example.backendbraiding.service.BookingQuoteService;
import org.example.backendbraiding.service.CategoryService;
import org.example.backendbraiding.service.SubcategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingPublishingConsistencyTests {

    @Test
    void publicBookingResponseSortsStylesAndSizesAndExcludesNestedSizesFromCategoryItems() {
        GalleryImageRepository galleryImages = mock(GalleryImageRepository.class);
        when(galleryImages.findBySubcategoryIdsOrderBySubcategoryAndDisplayOrder(anyList())).thenReturn(List.of());
        BookingController controller = new BookingController(
                mock(CategoryService.class), mock(SubcategoryService.class),
                mock(CategoryRepository.class), mock(SubcategoryRepository.class),
                galleryImages, mock(BookingQuoteService.class));

        Category category = new Category();
        category.setId(1L);
        category.setName("Box Braids");
        category.setSlug("box-braids");

        Subcategory later = subcategory(12L, "Later", 1, category);
        Subcategory first = subcategory(11L, "First", 0, category);
        first.setItems(new ArrayList<>(List.of(
                service(102L, "Medium", 1, category, first),
                service(101L, "Small", 0, category, first))));
        category.setSubcategories(new ArrayList<>(List.of(later, first)));

        ServiceItem nested = service(101L, "Small", 0, category, first);
        ServiceItem direct = service(201L, "Consultation", 0, category, null);
        category.setItems(new ArrayList<>(List.of(nested, direct)));

        Map<String, Object> response = controller.mapBookingCategory(category);
        List<Map<String, Object>> styles = castMaps(response.get("subcategories"));
        List<Map<String, Object>> directItems = castMaps(response.get("items"));
        List<Map<String, Object>> sizes = castMaps(styles.get(0).get("items"));

        assertEquals(List.of("First", "Later"), styles.stream().map(item -> item.get("name")).toList());
        assertEquals(0, styles.get(0).get("displayOrder"));
        assertEquals(List.of("Small", "Medium"), sizes.stream().map(item -> item.get("name")).toList());
        assertEquals(List.of("Consultation"), directItems.stream().map(item -> item.get("name")).toList());
    }

    @Test
    void everyStyleMutationEvictsTheSingularBookingCategoryCache() throws Exception {
        assertEvictsBookingCategory("createSubcategory", org.example.backendbraiding.dto.SubcategoryRequestDTO.class);
        assertEvictsBookingCategory("updateSubcategory", Long.class, org.example.backendbraiding.dto.SubcategoryUpdateDTO.class);
        assertEvictsBookingCategory("deleteSubcategory", Long.class);
        assertEvictsBookingCategory("reorderSubcategories", List.class);
    }

    private static void assertEvictsBookingCategory(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = SubcategoryService.class.getMethod(methodName, parameterTypes);
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);
        assertTrue(annotation != null && List.of(annotation.value()).contains("bookingCategory"),
                methodName + " must evict bookingCategory");
    }

    private static Subcategory subcategory(Long id, String name, int order, Category category) {
        Subcategory item = new Subcategory();
        item.setId(id);
        item.setName(name);
        item.setSlug(name.toLowerCase());
        item.setDisplayOrder(order);
        item.setCategory(category);
        return item;
    }

    private static ServiceItem service(Long id, String name, int order, Category category, Subcategory subcategory) {
        ServiceItem item = new ServiceItem();
        item.setId(id);
        item.setName(name);
        item.setDisplayOrder(order);
        item.setCategory(category);
        item.setSubcategory(subcategory);
        item.setPricingMode("FIXED");
        item.setPrice("100");
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
