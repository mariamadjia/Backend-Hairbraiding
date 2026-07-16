package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {
    @Query("SELECT g FROM GalleryImage g LEFT JOIN FETCH g.category LEFT JOIN FETCH g.subcategory WHERE g.category.id = :categoryId ORDER BY g.displayOrder ASC")
    List<GalleryImage> findByCategoryIdOrderByDisplayOrderAsc(@Param("categoryId") Long categoryId);

    @Query("SELECT g FROM GalleryImage g LEFT JOIN FETCH g.category LEFT JOIN FETCH g.subcategory WHERE g.subcategory.id = :subcategoryId ORDER BY g.displayOrder ASC")
    List<GalleryImage> findBySubcategoryIdOrderByDisplayOrderAsc(@Param("subcategoryId") Long subcategoryId);

    @Query("SELECT g FROM GalleryImage g LEFT JOIN FETCH g.category LEFT JOIN FETCH g.subcategory WHERE g.subcategory.id IN :subcategoryIds ORDER BY g.subcategory.id, g.displayOrder ASC")
    List<GalleryImage> findBySubcategoryIdsOrderBySubcategoryAndDisplayOrder(@Param("subcategoryIds") List<Long> subcategoryIds);

    List<GalleryImage> findByServiceItemIdOrderByDisplayOrderAsc(Long serviceItemId);
    List<GalleryImage> findByIsFeaturedTrueOrderByDisplayOrderAsc();
    List<GalleryImage> findByIsHeroTrueOrderByDisplayOrderAsc();
    long countByIsHeroTrue();

    @Query("SELECT COALESCE(MAX(g.displayOrder), 0) FROM GalleryImage g")
    Integer findMaxDisplayOrder();
    
    @Query("SELECT g FROM GalleryImage g WHERE " +
           "LOWER(g.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(g.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           ":search MEMBER OF g.tags " +
           "ORDER BY g.displayOrder ASC")
    List<GalleryImage> searchImages(@Param("search") String search);
    
    @Query("SELECT g FROM GalleryImage g WHERE :tag MEMBER OF g.tags ORDER BY g.displayOrder ASC")
    List<GalleryImage> findByTag(@Param("tag") String tag);
    
    @Query("SELECT DISTINCT tag FROM GalleryImage g JOIN g.tags tag")
    List<String> findAllTags();
}
