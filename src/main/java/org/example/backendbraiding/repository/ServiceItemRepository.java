package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    List<ServiceItem> findByCategoryId(Long categoryId);
    List<ServiceItem> findBySubcategoryId(Long subcategoryId);
    ServiceItem findFirstByNameContainingIgnoreCase(String name);
}
