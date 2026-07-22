package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    @Query("SELECT s FROM ServiceItem s WHERE s.category.id = :categoryId AND s.active = true ORDER BY s.displayOrder ASC, s.id ASC")
    List<ServiceItem> findByCategoryId(@Param("categoryId") Long categoryId);
    
    @Query("SELECT s FROM ServiceItem s WHERE s.subcategory.id = :subcategoryId AND s.active = true ORDER BY s.displayOrder ASC, s.id ASC")
    List<ServiceItem> findBySubcategoryId(@Param("subcategoryId") Long subcategoryId);
    
    ServiceItem findFirstByNameContainingIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(String name);

    List<ServiceItem> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    java.util.Optional<ServiceItem> findByIdAndActiveTrue(Long id);
}
