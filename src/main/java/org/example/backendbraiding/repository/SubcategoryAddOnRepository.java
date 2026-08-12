package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.SubcategoryAddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubcategoryAddOnRepository extends JpaRepository<SubcategoryAddOn, Long> {
    @Query("SELECT a FROM SubcategoryAddOn a JOIN FETCH a.addOn WHERE a.subcategory.id = :subcategoryId ORDER BY a.displayOrder, a.id")
    List<SubcategoryAddOn> findBySubcategoryId(@Param("subcategoryId") Long subcategoryId);

    @Query("SELECT a FROM SubcategoryAddOn a JOIN FETCH a.addOn WHERE a.subcategory.id = :subcategoryId AND a.active = true AND a.addOn.active = true ORDER BY a.displayOrder, a.id")
    List<SubcategoryAddOn> findActiveBySubcategoryId(@Param("subcategoryId") Long subcategoryId);

    Optional<SubcategoryAddOn> findBySubcategoryIdAndAddOnId(Long subcategoryId, Long addOnId);
}
