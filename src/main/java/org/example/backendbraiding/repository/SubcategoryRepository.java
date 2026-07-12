package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);
    List<Subcategory> findByCategoryId(Long categoryId);
    Optional<Subcategory> findBySlug(String slug);

    @Query("""
        SELECT s
        FROM Subcategory s
        JOIN FETCH s.category
        WHERE s.category.id IN :categoryIds
          AND s.image IS NOT NULL
          AND TRIM(s.image) <> ''
        ORDER BY s.category.id ASC, s.displayOrder ASC
    """)
    List<Subcategory> findGalleryCardImageSources(
            @Param("categoryIds") List<Long> categoryIds
    );

    // New lightweight summary query for admin - subcategories within a category
    @Query("SELECT s FROM Subcategory s WHERE s.category.slug = :categorySlug ORDER BY s.displayOrder ASC")
    List<Subcategory> findSubcategorySummariesByCategorySlug(@Param("categorySlug") String categorySlug);

    // Single subcategory with full details - split queries to avoid MultipleBagFetchException
    @Query("SELECT s FROM Subcategory s WHERE s.slug = :slug")
    Optional<Subcategory> findBySlugForAdmin(@Param("slug") String slug);
}
