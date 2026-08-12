package org.example.backendbraiding.service;

import lombok.RequiredArgsConstructor;
import org.example.backendbraiding.dto.AddOnAssignmentRequest;
import org.example.backendbraiding.dto.AddOnRequest;
import org.example.backendbraiding.dto.AddOnResponse;
import org.example.backendbraiding.dto.QuotedAddOnDTO;
import org.example.backendbraiding.exception.ResourceNotFoundException;
import org.example.backendbraiding.model.BookingAddOn;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.model.SubcategoryAddOn;
import org.example.backendbraiding.repository.BookingAddOnRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.example.backendbraiding.repository.SubcategoryAddOnRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AddOnService {
    private final BookingAddOnRepository addOnRepository;
    private final SubcategoryAddOnRepository assignmentRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ServiceItemRepository serviceItemRepository;

    @Transactional(readOnly = true)
    public List<AddOnResponse> listForSubcategory(Long subcategoryId) {
        requireSubcategory(subcategoryId);
        return assignmentRepository.findBySubcategoryId(subcategoryId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AddOnResponse> listLibrary() {
        return addOnRepository.findActiveLibrary().stream().map(addOn ->
                AddOnResponse.builder().id(addOn.getId()).name(addOn.getName())
                        .description(addOn.getDescription()).pricingMode(addOn.getPricingMode())
                        .priceCents(addOn.getPriceCents()).depositBehavior(addOn.getDepositBehavior())
                        .depositAdjustmentCents(addOn.getDepositAdjustmentCents()).active(addOn.getActive())
                        .confirmationRequired(!"FIXED".equals(addOn.getPricingMode()))
                        .serviceItemIds(List.of()).lengthOptionIds(List.of()).build()).toList();
    }

    @Transactional(readOnly = true)
    public List<AddOnResponse> listAvailable(Long serviceId, Long lengthOptionId) {
        ServiceItem service = serviceItemRepository.findByIdAndActiveTrue(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        if (service.getSubcategory() == null) return List.of();
        LengthOption option = resolveLength(service, lengthOptionId);
        return availableAssignments(service, option).stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<AddOnResponse> create(AddOnRequest request) {
        validateDefinition(request);
        BookingAddOn addOn = new BookingAddOn();
        applyDefinition(addOn, request);
        addOn = addOnRepository.save(addOn);
        List<Long> styleIds = request.getSubcategoryIds() == null ? List.of() : request.getSubcategoryIds().stream().distinct().toList();
        if (styleIds.isEmpty()) throw new IllegalArgumentException("Choose at least one style for this add-on");
        List<AddOnResponse> responses = new ArrayList<>();
        for (Long styleId : styleIds) {
            AddOnAssignmentRequest assignmentRequest = new AddOnAssignmentRequest();
            assignmentRequest.setActive(request.getActive());
            assignmentRequest.setAllSizes(request.getAllSizes());
            assignmentRequest.setAllLengths(request.getAllLengths());
            assignmentRequest.setServiceItemIds(request.getServiceItemIds());
            assignmentRequest.setLengthOptionIds(request.getLengthOptionIds());
            SubcategoryAddOn assignment = createAssignment(addOn, requireSubcategory(styleId), assignmentRequest);
            responses.add(toResponse(assignmentRepository.save(assignment)));
        }
        return responses;
    }

    @Transactional
    public BookingAddOn updateDefinition(Long addOnId, AddOnRequest request) {
        validateDefinition(request);
        BookingAddOn addOn = requireAddOn(addOnId);
        applyDefinition(addOn, request);
        return addOnRepository.save(addOn);
    }

    @Transactional
    public AddOnResponse assign(Long subcategoryId, Long addOnId, AddOnAssignmentRequest request) {
        if (assignmentRepository.findBySubcategoryIdAndAddOnId(subcategoryId, addOnId).isPresent()) {
            throw new IllegalArgumentException("This add-on is already assigned to the style");
        }
        SubcategoryAddOn assignment = createAssignment(requireAddOn(addOnId), requireSubcategory(subcategoryId), request);
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public AddOnResponse updateAssignment(Long assignmentId, AddOnAssignmentRequest request) {
        SubcategoryAddOn assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Add-on assignment not found"));
        applyAssignment(assignment, request);
        validateAssignmentTargets(assignment);
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void archive(Long addOnId) {
        BookingAddOn addOn = requireAddOn(addOnId);
        addOn.setActive(false);
        addOnRepository.save(addOn);
    }

    @Transactional
    public void removeAssignment(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) throw new ResourceNotFoundException("Add-on assignment not found");
        assignmentRepository.deleteById(assignmentId);
    }

    @Transactional
    public void reorder(Long subcategoryId, List<Long> assignmentIds) {
        List<SubcategoryAddOn> current = assignmentRepository.findBySubcategoryId(subcategoryId);
        Set<Long> expected = new HashSet<>(current.stream().map(SubcategoryAddOn::getId).toList());
        if (assignmentIds == null || assignmentIds.size() != expected.size() || !expected.equals(new HashSet<>(assignmentIds))) {
            throw new IllegalArgumentException("Submit every add-on assignment exactly once");
        }
        Map<Long, SubcategoryAddOn> byId = new HashMap<>();
        current.forEach(item -> byId.put(item.getId(), item));
        for (int index = 0; index < assignmentIds.size(); index++) byId.get(assignmentIds.get(index)).setDisplayOrder(index);
        assignmentRepository.saveAll(current);
    }

    @Transactional(readOnly = true)
    public List<ResolvedAddOn> resolveSelections(ServiceItem service, LengthOption option, Collection<Long> addOnIds) {
        if (addOnIds == null || addOnIds.isEmpty()) return List.of();
        if (service.getSubcategory() == null) throw new IllegalArgumentException("This service does not offer add-ons");
        List<Long> distinct = addOnIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() != addOnIds.size()) throw new IllegalArgumentException("An add-on may only be selected once");
        Map<Long, SubcategoryAddOn> available = new LinkedHashMap<>();
        availableAssignments(service, option).forEach(a -> available.put(a.getAddOn().getId(), a));
        List<ResolvedAddOn> resolved = new ArrayList<>();
        for (Long id : distinct) {
            SubcategoryAddOn assignment = available.get(id);
            if (assignment == null) throw new IllegalArgumentException("A selected add-on is unavailable for this size or length");
            BookingAddOn addOn = assignment.getAddOn();
            long advertised = assignment.getPriceOverrideCents() == null ? addOn.getPriceCents() : assignment.getPriceOverrideCents();
            long charged = "FIXED".equals(addOn.getPricingMode()) ? advertised : 0L;
            long depositAdjustment = "ADD_FIXED".equals(addOn.getDepositBehavior()) && charged > 0
                    ? addOn.getDepositAdjustmentCents() : 0L;
            resolved.add(new ResolvedAddOn(assignment, advertised, charged, depositAdjustment));
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<ResolvedAddOn> validateClaims(ServiceItem service, LengthOption option,
                                               List<BookingQuoteTokenService.AddOnClaim> claims) {
        if (claims == null || claims.isEmpty()) return List.of();
        List<ResolvedAddOn> resolved = resolveSelections(service, option,
                claims.stream().map(BookingQuoteTokenService.AddOnClaim::addOnId).toList());
        Map<Long, ResolvedAddOn> byAssignment = new HashMap<>();
        resolved.forEach(item -> byAssignment.put(item.assignment().getId(), item));
        for (BookingQuoteTokenService.AddOnClaim claim : claims) {
            ResolvedAddOn item = byAssignment.get(claim.assignmentId());
            if (item == null || !item.assignment().getAddOn().getId().equals(claim.addOnId())
                    || version(item.assignment().getAddOn().getVersion()) != claim.addOnVersion()
                    || version(item.assignment().getVersion()) != claim.assignmentVersion()
                    || item.advertisedPriceCents() != claim.advertisedPriceCents()
                    || item.chargedPriceCents() != claim.chargedPriceCents()) {
                throw new IllegalStateException("Add-on pricing changed while you were booking. Please review your selections.");
            }
        }
        return resolved;
    }

    private List<SubcategoryAddOn> availableAssignments(ServiceItem service, LengthOption option) {
        return assignmentRepository.findActiveBySubcategoryId(service.getSubcategory().getId()).stream()
                .filter(a -> Boolean.TRUE.equals(a.getAllSizes()) || a.getServiceItemIds().contains(service.getId()))
                .filter(a -> Boolean.TRUE.equals(a.getAllLengths()) || option != null && a.getLengthOptionIds().contains(option.getId()))
                .toList();
    }

    private LengthOption resolveLength(ServiceItem service, Long lengthOptionId) {
        if (lengthOptionId == null) return null;
        return service.getLengthOptions().stream().filter(o -> lengthOptionId.equals(o.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected length is unavailable"));
    }

    private SubcategoryAddOn createAssignment(BookingAddOn addOn, Subcategory subcategory, AddOnAssignmentRequest request) {
        SubcategoryAddOn assignment = new SubcategoryAddOn();
        assignment.setAddOn(addOn);
        assignment.setSubcategory(subcategory);
        assignment.setDisplayOrder(assignmentRepository.findBySubcategoryId(subcategory.getId()).size());
        applyAssignment(assignment, request);
        validateAssignmentTargets(assignment);
        return assignment;
    }

    private void applyAssignment(SubcategoryAddOn assignment, AddOnAssignmentRequest request) {
        assignment.setActive(!Boolean.FALSE.equals(request.getActive()));
        assignment.setPriceOverrideCents(request.getPriceOverrideCents());
        assignment.setAllSizes(!Boolean.FALSE.equals(request.getAllSizes()));
        assignment.setAllLengths(!Boolean.FALSE.equals(request.getAllLengths()));
        assignment.setServiceItemIds(new LinkedHashSet<>(request.getServiceItemIds() == null ? List.of() : request.getServiceItemIds()));
        assignment.setLengthOptionIds(new LinkedHashSet<>(request.getLengthOptionIds() == null ? List.of() : request.getLengthOptionIds()));
    }

    private void validateAssignmentTargets(SubcategoryAddOn assignment) {
        List<ServiceItem> services = serviceItemRepository.findBySubcategoryId(assignment.getSubcategory().getId());
        Set<Long> validServices = new HashSet<>(services.stream().map(ServiceItem::getId).toList());
        Set<Long> validLengths = new HashSet<>(services.stream().flatMap(s -> s.getLengthOptions().stream()).map(LengthOption::getId).toList());
        if (!Boolean.TRUE.equals(assignment.getAllSizes()) && assignment.getServiceItemIds().isEmpty()) throw new IllegalArgumentException("Choose at least one size");
        if (!Boolean.TRUE.equals(assignment.getAllLengths()) && assignment.getLengthOptionIds().isEmpty()) throw new IllegalArgumentException("Choose at least one length");
        if (!validServices.containsAll(assignment.getServiceItemIds())) throw new IllegalArgumentException("A selected size does not belong to this style");
        if (!validLengths.containsAll(assignment.getLengthOptionIds())) throw new IllegalArgumentException("A selected length does not belong to this style");
    }

    private void validateDefinition(AddOnRequest request) {
        String pricing = normalizePricing(request.getPricingMode());
        if ("FIXED".equals(pricing) && (request.getPriceCents() == null || request.getPriceCents() <= 0)) {
            throw new IllegalArgumentException("Fixed-price add-ons require a price greater than zero");
        }
        normalizeDeposit(request.getDepositBehavior());
    }

    private void applyDefinition(BookingAddOn addOn, AddOnRequest request) {
        addOn.setName(request.getName().trim());
        addOn.setDescription(clean(request.getDescription()));
        addOn.setPricingMode(normalizePricing(request.getPricingMode()));
        addOn.setPriceCents(request.getPriceCents() == null ? 0L : request.getPriceCents());
        addOn.setDepositBehavior(normalizeDeposit(request.getDepositBehavior()));
        addOn.setDepositAdjustmentCents(request.getDepositAdjustmentCents() == null ? 0L : request.getDepositAdjustmentCents());
        addOn.setActive(!Boolean.FALSE.equals(request.getActive()));
    }

    private AddOnResponse toResponse(SubcategoryAddOn assignment) {
        BookingAddOn addOn = assignment.getAddOn();
        long price = assignment.getPriceOverrideCents() == null ? addOn.getPriceCents() : assignment.getPriceOverrideCents();
        return AddOnResponse.builder().id(addOn.getId()).assignmentId(assignment.getId()).name(addOn.getName())
                .description(addOn.getDescription()).pricingMode(addOn.getPricingMode()).priceCents(price)
                .depositBehavior(addOn.getDepositBehavior()).depositAdjustmentCents(addOn.getDepositAdjustmentCents())
                .active(Boolean.TRUE.equals(addOn.getActive()) && Boolean.TRUE.equals(assignment.getActive()))
                .displayOrder(assignment.getDisplayOrder()).subcategoryId(assignment.getSubcategory().getId())
                .subcategoryName(assignment.getSubcategory().getName()).allSizes(assignment.getAllSizes())
                .allLengths(assignment.getAllLengths()).serviceItemIds(List.copyOf(assignment.getServiceItemIds()))
                .lengthOptionIds(List.copyOf(assignment.getLengthOptionIds()))
                .confirmationRequired(!"FIXED".equals(addOn.getPricingMode())).build();
    }

    public QuotedAddOnDTO toQuoted(ResolvedAddOn resolved) {
        BookingAddOn addOn = resolved.assignment().getAddOn();
        return new QuotedAddOnDTO(addOn.getId(), addOn.getName(), addOn.getPricingMode(),
                resolved.advertisedPriceCents(), resolved.chargedPriceCents(), !"FIXED".equals(addOn.getPricingMode()));
    }

    private Subcategory requireSubcategory(Long id) {
        return subcategoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Style not found"));
    }
    private BookingAddOn requireAddOn(Long id) {
        return addOnRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Add-on not found"));
    }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String normalizePricing(String value) { return "STARTING_AT".equalsIgnoreCase(value) ? "STARTING_AT" : "FIXED"; }
    private String normalizeDeposit(String value) { return "ADD_FIXED".equalsIgnoreCase(value) ? "ADD_FIXED" : "NO_CHANGE"; }
    private long version(Long value) { return value == null ? 0L : value; }

    public record ResolvedAddOn(SubcategoryAddOn assignment, long advertisedPriceCents,
                                long chargedPriceCents, long depositAdjustmentCents) {}
}
