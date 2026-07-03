package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {
    
    @Query("SELECT DISTINCT g FROM GalleryImage g " +
           "LEFT JOIN FETCH g.category " +
           "LEFT JOIN FETCH g.subcategory " +
           "LEFT JOIN FETCH g.serviceItem " +
           "ORDER BY g.displayOrder ASC")
    List<GalleryImage> findAllWithRelations();
    
    List<GalleryImage> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);
    List<GalleryImage> findBySubcategoryIdOrderByDisplayOrderAsc(Long subcategoryId);
    List<GalleryImage> findByServiceItemIdOrderByDisplayOrderAsc(Long serviceItemId);
    List<GalleryImage> findByIsFeaturedTrueOrderByDisplayOrderAsc();
    List<GalleryImage> findByIsHeroTrue();
    
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
