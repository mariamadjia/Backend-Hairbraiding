package org.example.backendbraiding.controller;

import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.service.CategoryService;
import org.example.backendbraiding.service.ServiceItemService;
import org.example.backendbraiding.service.SubcategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServiceItemController {
    private final ServiceItemService serviceItemService;
    private final CategoryService categoryService;
    private final SubcategoryService subcategoryService;

    public ServiceItemController(ServiceItemService serviceItemService, CategoryService categoryService, SubcategoryService subcategoryService) {
        this.serviceItemService = serviceItemService;
        this.categoryService = categoryService;
        this.subcategoryService = subcategoryService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceItem>> getAllServices() {
        return ResponseEntity.ok(serviceItemService.getAllServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceItem> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceItemService.getServiceById(id));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ServiceItem>> getServicesByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(serviceItemService.getServicesByCategory(categoryId));
    }

    @GetMapping("/subcategory/{subcategoryId}")
    public ResponseEntity<List<ServiceItem>> getServicesBySubcategory(@PathVariable Long subcategoryId) {
        return ResponseEntity.ok(serviceItemService.getServicesBySubcategory(subcategoryId));
    }

    @PostMapping
    public ResponseEntity<ServiceItem> createService(@RequestBody Map<String, Object> serviceData) {
        System.out.println("========== CREATE SERVICE CALLED ==========");
        System.out.println("Service data: " + serviceData);
        
        ServiceItem service = new ServiceItem();
        service.setName(serviceData.get("name").toString());
        service.setPrice(serviceData.getOrDefault("price", "").toString());
        service.setDescription(serviceData.getOrDefault("description", "").toString());
        
        // Handle category
        if (serviceData.containsKey("category")) {
            Map<String, Object> categoryData = (Map<String, Object>) serviceData.get("category");
            if (categoryData.containsKey("id")) {
                Long categoryId = ((Number) categoryData.get("id")).longValue();
                Category category = categoryService.getCategoryById(categoryId);
                service.setCategory(category);
                System.out.println("Set category: " + category.getName());
            }
        }
        
        // Handle subcategory
        if (serviceData.containsKey("subcategory")) {
            Map<String, Object> subcategoryData = (Map<String, Object>) serviceData.get("subcategory");
            if (subcategoryData.containsKey("id")) {
                Long subcategoryId = ((Number) subcategoryData.get("id")).longValue();
                Subcategory subcategory = subcategoryService.getSubcategoryById(subcategoryId);
                service.setSubcategory(subcategory);
                System.out.println("Set subcategory: " + subcategory.getName());
            }
        }
        
        // Handle length options
        if (serviceData.containsKey("lengthOptions")) {
            List<Map<String, Object>> lengthOptionsData = (List<Map<String, Object>>) serviceData.get("lengthOptions");
            List<LengthOption> lengthOptions = new ArrayList<>();
            for (Map<String, Object> optionData : lengthOptionsData) {
                LengthOption option = new LengthOption();
                if (optionData.containsKey("name")) {
                    option.setName(optionData.get("name").toString());
                }
                if (optionData.containsKey("price")) {
                    option.setPrice(optionData.get("price").toString());
                }
                if (optionData.containsKey("duration")) {
                    option.setDuration(optionData.get("duration").toString());
                }
                if (optionData.containsKey("notes")) {
                    option.setNotes(optionData.get("notes").toString());
                }
                option.setServiceItem(service);
                lengthOptions.add(option);
            }
            service.setLengthOptions(lengthOptions);
        }
        
        System.out.println("Length options: " + (service.getLengthOptions() != null ? service.getLengthOptions().size() : 0));
        ServiceItem created = serviceItemService.createService(service);
        System.out.println("Service created with ID: " + created.getId());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceItem> updateService(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(serviceItemService.updateService(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteService(@PathVariable Long id) {
        serviceItemService.deleteService(id);
        return ResponseEntity.ok(Map.of("message", "Service deleted successfully"));
    }
}
