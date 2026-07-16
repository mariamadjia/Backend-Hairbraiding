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
        LEFT JOIN FETCH c.subcategories sub
        LEFT JOIN FETCH sub.items si
        LEFT JOIN FETCH si.lengthOptions
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
        LEFT JOIN FETCH c.subcategories sub
        LEFT JOIN FETCH sub.items si
        LEFT JOIN FETCH si.lengthOptions
        WHERE c.slug = :slug
    """)
    Optional<Category> findBySlugWithAllData(@org.springframework.data.repository.query.Param("slug") String slug);
    
    boolean existsBySlug(String slug);

    // New lightweight summary query for admin - fetch entities and map in service
    @Query("SELECT c FROM Category c ORDER BY c.displayOrder ASC")
    List<Category> findCategorySummaries();

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
        LEFT JOIN FETCH c.subcategories sub
        LEFT JOIN FETCH sub.items si
        LEFT JOIN FETCH si.lengthOptions
        ORDER BY c.displayOrder ASC
    """)
    List<Category> findAllForBooking();
}
