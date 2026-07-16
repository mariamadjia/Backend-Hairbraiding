package org.example.backendbraiding.service;

import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ServiceItemService {
    private final ServiceItemRepository serviceItemRepository;

    public ServiceItemService(ServiceItemRepository serviceItemRepository) {
        this.serviceItemRepository = serviceItemRepository;
    }

    public List<ServiceItem> getAllServices() {
        return serviceItemRepository.findAll();
    }

    public ServiceItem getServiceById(Long id) {
        return serviceItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
    }

    public List<ServiceItem> getServicesByCategory(Long categoryId) {
        return serviceItemRepository.findByCategoryId(categoryId);
    }

    public List<ServiceItem> getServicesBySubcategory(Long subcategoryId) {
        return serviceItemRepository.findBySubcategoryId(subcategoryId);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem createService(ServiceItem service) {
        // Set bidirectional relationship for length options
        if (service.getLengthOptions() != null) {
            for (LengthOption option : service.getLengthOptions()) {
                option.setServiceItem(service);
            }
        }
        return serviceItemRepository.save(service);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem updateService(Long id, Map<String, Object> updates) {
        ServiceItem service = getServiceById(id);

        if (updates.containsKey("name") && updates.get("name") != null) {
            service.setName(updates.get("name").toString());
        }
        if (updates.containsKey("price") && updates.get("price") != null) {
            service.setPrice(updates.get("price").toString());
        }
        if (updates.containsKey("description") && updates.get("description") != null) {
            service.setDescription(updates.get("description").toString());
        }
        if (updates.containsKey("notes") && updates.get("notes") != null) {
            service.setNotes(updates.get("notes").toString());
        }
        if (updates.containsKey("image") && updates.get("image") != null) {
            service.setImage(updates.get("image").toString());
        }
        if (updates.containsKey("link") && updates.get("link") != null) {
            service.setLink(updates.get("link").toString());
        }
        if (updates.containsKey("objectPosition") && updates.get("objectPosition") != null) {
            service.setObjectPosition(updates.get("objectPosition").toString());
        }
        if (updates.containsKey("images")) {
            service.setImages((List<String>) updates.get("images"));
        }
        if (updates.containsKey("sizePhotos")) {
            service.setSizePhotos((List<String>) updates.get("sizePhotos"));
        }
        if (updates.containsKey("availableSizes")) {
            service.setAvailableSizes((List<String>) updates.get("availableSizes"));
        }
        if (updates.containsKey("hairTextures")) {
            service.setHairTextures((List<String>) updates.get("hairTextures"));
        }
        if (updates.containsKey("lengthOptions")) {
            // Clear existing length options
            if (service.getLengthOptions() == null) {
                service.setLengthOptions(new ArrayList<>());
            }
            service.getLengthOptions().clear();
            
            // Add new length options
            List<Map<String, Object>> optionsData = (List<Map<String, Object>>) updates.get("lengthOptions");
            if (optionsData != null) {
                for (Map<String, Object> optionData : optionsData) {
                    LengthOption option = new LengthOption();
                    if (optionData.containsKey("name") && optionData.get("name") != null) {
                        option.setName(optionData.get("name").toString());
                    }
                    if (optionData.containsKey("price") && optionData.get("price") != null) {
                        option.setPrice(optionData.get("price").toString());
                    }
                    if (optionData.containsKey("duration") && optionData.get("duration") != null) {
                        option.setDuration(optionData.get("duration").toString());
                    }
                    if (optionData.containsKey("notes") && optionData.get("notes") != null) {
                        option.setNotes(optionData.get("notes").toString());
                    }
                    if (optionData.containsKey("imageUrl") && optionData.get("imageUrl") != null) {
                        option.setImageUrl(optionData.get("imageUrl").toString());
                    }
                    option.setServiceItem(service);
                    service.getLengthOptions().add(option);
                }
            }
        }

        return serviceItemRepository.save(service);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void deleteService(Long id) {
        ServiceItem service = getServiceById(id);
        serviceItemRepository.delete(service);
    }
}
