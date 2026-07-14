package org.example.backendbraiding.service;

import org.example.backendbraiding.model.GalleryImage;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.GalleryImageRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service to keep subcategory images and gallery images in sync
 */
@Service
public class ImageSyncService {
    private static final Logger log = LoggerFactory.getLogger(ImageSyncService.class);
    private final GalleryImageRepository galleryImageRepository;
    private final SubcategoryRepository subcategoryRepository;

    public ImageSyncService(
            GalleryImageRepository galleryImageRepository,
            SubcategoryRepository subcategoryRepository) {
        this.galleryImageRepository = galleryImageRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    /**
     * When a subcategory image is uploaded/updated, sync it to gallery
     * Creates or updates the first gallery image for this subcategory
     */
    @Transactional
    public void syncSubcategoryImageToGallery(Subcategory subcategory) {
        if (subcategory.getImage() == null || subcategory.getImage().isEmpty()) {
            return;
        }

        // Find existing gallery images for this subcategory
        List<GalleryImage> existingImages = galleryImageRepository
                .findBySubcategoryIdOrderByDisplayOrderAsc(subcategory.getId());

        if (existingImages.isEmpty()) {
            // Create new gallery image
            GalleryImage galleryImage = new GalleryImage();
            galleryImage.setTitle(subcategory.getName());
            galleryImage.setDescription("Image for " + subcategory.getName());
            galleryImage.setImageUrl(subcategory.getImage());
            galleryImage.setThumbnailUrl(subcategory.getImage());
            galleryImage.setAltText(subcategory.getName());
            galleryImage.setSubcategory(subcategory);
            galleryImage.setCategory(subcategory.getCategory());
            galleryImage.setDisplayOrder(0);
            galleryImage.setIsFeatured(false);
            galleryImage.setIsHero(false);
            
            galleryImageRepository.save(galleryImage);
            log.info("Created gallery image for subcategory: {}", subcategory.getName());
        } else {
            // Update first gallery image
            GalleryImage firstImage = existingImages.get(0);
            firstImage.setImageUrl(subcategory.getImage());
            firstImage.setThumbnailUrl(subcategory.getImage());
            firstImage.setTitle(subcategory.getName());
            
            galleryImageRepository.save(firstImage);
            log.info("Updated gallery image for subcategory: {}", subcategory.getName());
        }
    }

    /**
     * When gallery images are uploaded/updated for a subcategory, 
     * sync the first one to subcategory.image
     */
    @Transactional
    public void syncGalleryToSubcategoryImage(Long subcategoryId) {
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new RuntimeException("Subcategory not found"));

        // Get all gallery images for this subcategory, ordered by display order
        List<GalleryImage> galleryImages = galleryImageRepository
                .findBySubcategoryIdOrderByDisplayOrderAsc(subcategoryId);

        if (galleryImages.isEmpty()) {
            // No gallery images, clear subcategory image
            subcategory.setImage(null);
        } else {
            // Set subcategory image to first gallery image
            subcategory.setImage(galleryImages.get(0).getImageUrl());
        }

        subcategoryRepository.save(subcategory);
        log.info("Synced gallery to subcategory image: {}", subcategory.getName());
    }

}
