package org.example.backendbraiding.repository;

import org.example.backendbraiding.dto.CategorySummaryDTO;
import org.example.backendbraiding.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByDisplayOrderAsc();

    @Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.subcategories
        ORDER BY c.displayOrder ASC
    """)
    List<Category> findAllWithSubcategoriesAndItems();

    @Query("""
        SELECT DISTINCT c
        FROM Category c
        LEFT JOIN FETCH c.flippingImages
        ORDER BY c.displayOrder ASC
    """)
    List<Category> findAllForGalleryCards();

    Optional<Category> findBySlug(String slug);
    
    @Query("""
        SELECT DISTINCT c
        FROM Category c
        LEFT JOIN FETCH c.subcategories
        WHERE c.slug = :slug
    """)
    Optional<Category> findBySlugWithAllData(@org.springframework.data.repository.query.Param("slug") String slug);
    
    boolean existsBySlug(String slug);

    @Query("""
        SELECT new org.example.backendbraiding.dto.CategorySummaryDTO(
            c.id,
            c.name,
            c.slug,
            c.displayOrder,
            COUNT(DISTINCT subcategory.id),
            (SELECT COUNT(appointment.id)
             FROM Appointment appointment
             WHERE appointment.service.category.id = c.id),
            c.updatedAt
        )
        FROM Category c
        LEFT JOIN c.subcategories subcategory
        GROUP BY c.id, c.name, c.slug, c.displayOrder, c.updatedAt
        ORDER BY c.displayOrder ASC
    """)
    List<CategorySummaryDTO> findCategorySummaries();

    // Single category for admin: eagerly fetch subcategories only.
    // flippingImages is loaded separately to avoid MultipleBagFetchException.
    @Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.subcategories
        WHERE c.slug = :slug
    """)
    Optional<Category> findBySlugForAdmin(@org.springframework.data.repository.query.Param("slug") String slug);
    
    // Optimized query for booking data - fetch all needed relationships in one query
    @Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.subcategories
        ORDER BY c.displayOrder ASC
    """)
    List<Category> findAllForBooking();
}
