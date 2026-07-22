package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.ServiceItemRequest;
import org.example.backendbraiding.model.Category;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.CategoryRepository;
import org.example.backendbraiding.repository.ServiceItemRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceItemServiceTests {
    private ServiceItemRepository services;
    private ServiceItemService subject;
    private ServiceItem existing;

    @BeforeEach
    void setUp() {
        services = mock(ServiceItemRepository.class);
        subject = new ServiceItemService(services, mock(CategoryRepository.class), mock(SubcategoryRepository.class));

        Category category = new Category();
        category.setId(1L);
        Subcategory subcategory = new Subcategory();
        subcategory.setId(2L);
        subcategory.setCategory(category);
        existing = new ServiceItem();
        existing.setId(3L);
        existing.setName("Small");
        existing.setSubcategory(subcategory);
        existing.setCategory(category);
        LengthOption option = new LengthOption();
        option.setId(4L);
        option.setName("Waist");
        option.setPrice("250");
        option.setServiceItem(existing);
        existing.setLengthOptions(new ArrayList<>(List.of(option)));

        when(services.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(existing));
        when(services.save(any(ServiceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatePreservesExistingLengthOptionIdentity() {
        ServiceItemRequest request = baseRequest();
        ServiceItemRequest.LengthOptionInput input = new ServiceItemRequest.LengthOptionInput();
        input.setId(4L);
        input.setName("Waist Length");
        input.setPrice("275.00");
        request.setLengthOptions(List.of(input));

        ServiceItem updated = subject.updateService(3L, request);

        assertEquals(4L, updated.getLengthOptions().get(0).getId());
        assertEquals("Waist Length", updated.getLengthOptions().get(0).getName());
    }

    @Test
    void rejectsLengthOptionOwnedByAnotherService() {
        ServiceItemRequest request = baseRequest();
        ServiceItemRequest.LengthOptionInput input = new ServiceItemRequest.LengthOptionInput();
        input.setId(999L);
        input.setName("Waist");
        input.setPrice("250");
        request.setLengthOptions(List.of(input));

        assertThrows(IllegalArgumentException.class, () -> subject.updateService(3L, request));
    }

    @Test
    void deleteArchivesInsteadOfDeletingRow() {
        subject.deleteService(3L);

        assertFalse(existing.isActive());
        verify(services).save(existing);
        verify(services, never()).delete(any());
    }

    @Test
    void restoresArchivedService() {
        existing.setActive(false);
        when(services.findById(3L)).thenReturn(Optional.of(existing));

        ServiceItem restored = subject.restoreService(3L);

        assertTrue(restored.isActive());
        verify(services).save(existing);
    }

    @Test
    void reordersServicesWithinSubcategory() {
        ServiceItem second = new ServiceItem();
        second.setId(5L);
        second.setName("Medium");
        second.setCategory(existing.getCategory());
        second.setSubcategory(existing.getSubcategory());
        when(services.findAllById(List.of(5L, 3L))).thenReturn(List.of(existing, second));

        subject.reorderServices(List.of(5L, 3L));

        assertEquals(0, second.getDisplayOrder());
        assertEquals(1, existing.getDisplayOrder());
        verify(services).saveAll(anyList());
    }

    private ServiceItemRequest baseRequest() {
        ServiceItemRequest request = new ServiceItemRequest();
        request.setName("Small");
        request.setPrice("");
        return request;
    }
}
