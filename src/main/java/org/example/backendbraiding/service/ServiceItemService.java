package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.ServiceItemRequest;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ServiceItemService {
    private final ServiceItemRepository serviceItemRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public ServiceItemService(ServiceItemRepository serviceItemRepository,
                              CategoryRepository categoryRepository,
                              SubcategoryRepository subcategoryRepository) {
        this.serviceItemRepository = serviceItemRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    public List<ServiceItem> getAllServices() {
        return serviceItemRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    public ServiceItem getServiceById(Long id) {
        return serviceItemRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    public List<ServiceItem> getServicesByCategory(Long categoryId) {
        return serviceItemRepository.findByCategoryId(categoryId);
    }

    public List<ServiceItem> getServicesBySubcategory(Long subcategoryId) {
        return serviceItemRepository.findBySubcategoryId(subcategoryId);
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> getArchivedServices(Long subcategoryId) {
        return serviceItemRepository.findArchived(subcategoryId);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem createService(ServiceItemRequest request) {
        ServiceItem service = new ServiceItem();
        applyRequest(service, request, true);
        return serviceItemRepository.save(service);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem updateService(Long id, ServiceItemRequest request) {
        ServiceItem service = getServiceById(id);
        applyRequest(service, request, false);
        return serviceItemRepository.save(service);
    }

    private void applyRequest(ServiceItem service, ServiceItemRequest request, boolean creating) {
        service.setName(request.getName().trim());
        service.setPrice(clean(request.getPrice()));
        service.setDescription(clean(request.getDescription()));
        service.setNotes(clean(request.getNotes()));
        service.setImage(clean(request.getImage()));
        service.setLink(clean(request.getLink()));
        service.setObjectPosition(clean(request.getObjectPosition()));
        service.setImages(cleanList(request.getImages()));
        service.setSizePhotos(cleanList(request.getSizePhotos()));
        service.setAvailableSizes(uniqueList(request.getAvailableSizes(), "Available sizes"));
        service.setHairTextures(uniqueList(request.getHairTextures(), "Hair textures"));
        if (request.getDisplayOrder() != null) service.setDisplayOrder(Math.max(0, request.getDisplayOrder()));

        Relationship relationship = resolveRelationship(request, service, creating);
        service.setCategory(relationship.category());
        service.setSubcategory(relationship.subcategory());
        mergeLengthOptions(service, request.getLengthOptions());
        if (service.getPrice().isBlank() && service.getLengthOptions().isEmpty()) {
            throw new IllegalArgumentException("A service requires a price or at least one length option");
        }
    }

    private Relationship resolveRelationship(ServiceItemRequest request, ServiceItem service, boolean creating) {
        Long requestedSubcategoryId = request.getSubcategory() == null ? null : request.getSubcategory().getId();
        Long requestedCategoryId = request.getCategory() == null ? null : request.getCategory().getId();
        Subcategory subcategory = requestedSubcategoryId == null ? service.getSubcategory()
                : subcategoryRepository.findById(requestedSubcategoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory not found"));
        if (subcategory == null && creating) throw new IllegalArgumentException("Subcategory is required");

        Category category = requestedCategoryId == null
                ? (subcategory == null ? service.getCategory() : subcategory.getCategory())
                : categoryRepository.findById(requestedCategoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (subcategory != null && category != null && !subcategory.getCategory().getId().equals(category.getId())) {
            throw new IllegalArgumentException("Subcategory does not belong to the selected category");
        }
        return new Relationship(category, subcategory);
    }

    private void mergeLengthOptions(ServiceItem service, List<ServiceItemRequest.LengthOptionInput> inputs) {
        List<ServiceItemRequest.LengthOptionInput> safeInputs = inputs == null ? List.of() : inputs;
        Set<String> names = new HashSet<>();
        Map<Long, LengthOption> existingById = new HashMap<>();
        service.getLengthOptions().forEach(option -> existingById.put(option.getId(), option));
        List<LengthOption> merged = new ArrayList<>();

        for (ServiceItemRequest.LengthOptionInput input : safeInputs) {
            String normalizedName = input.getName().trim();
            if (!names.add(normalizedName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Length option names must be unique within a service");
            }
            LengthOption option;
            if (input.getId() == null) {
                option = new LengthOption();
            } else {
                option = existingById.remove(input.getId());
                if (option == null) throw new IllegalArgumentException("Length option does not belong to this service");
            }
            option.setName(normalizedName);
            option.setPrice(clean(input.getPrice()));
            option.setNotes(clean(input.getNotes()));
            option.setImageUrl(clean(input.getImageUrl()));
            option.setServiceItem(service);
            merged.add(option);
        }
        service.getLengthOptions().clear();
        service.getLengthOptions().addAll(merged);
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) return new ArrayList<>();
        return values.stream().map(this::clean).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<String> uniqueList(List<String> values, String label) {
        List<String> cleaned = cleanList(values);
        Set<String> unique = new HashSet<>();
        for (String value : cleaned) {
            if (!unique.add(value.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(label + " must not contain duplicates");
            }
        }
        return new ArrayList<>(cleaned);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void deleteService(Long id) {
        ServiceItem service = getServiceById(id);
        service.setActive(false);
        serviceItemRepository.save(service);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ServiceItem restoreService(Long id) {
        ServiceItem service = serviceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archived service not found"));
        if (service.isActive()) throw new IllegalStateException("Service is already active");
        service.setActive(true);
        return serviceItemRepository.save(service);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "bookingCategory", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void reorderServices(List<Long> serviceIds) {
        List<ServiceItem> services = serviceItemRepository.findAllById(serviceIds);
        if (services.size() != serviceIds.size() || services.stream().anyMatch(service -> !service.isActive())) {
            throw new IllegalArgumentException("One or more services cannot be reordered");
        }
        Long subcategoryId = services.get(0).getSubcategory() == null ? null : services.get(0).getSubcategory().getId();
        if (subcategoryId == null || services.stream().anyMatch(service -> service.getSubcategory() == null || !subcategoryId.equals(service.getSubcategory().getId()))) {
            throw new IllegalArgumentException("Services must belong to the same subcategory");
        }
        Map<Long, ServiceItem> byId = services.stream().collect(java.util.stream.Collectors.toMap(ServiceItem::getId, service -> service));
        for (int index = 0; index < serviceIds.size(); index++) byId.get(serviceIds.get(index)).setDisplayOrder(index);
        serviceItemRepository.saveAll(services);
    }

    private record Relationship(Category category, Subcategory subcategory) {}
}
