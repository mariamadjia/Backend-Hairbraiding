package org.example.backendbraiding.service;

import org.example.backendbraiding.model.GalleryImage;
import org.example.backendbraiding.model.Subcategory;
import org.example.backendbraiding.repository.GalleryImageRepository;
import org.example.backendbraiding.repository.SubcategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service to keep subcategory images and gallery images in sync
 */
@Service
public class ImageSyncService {
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
            System.out.println("Created gallery image for subcategory: " + subcategory.getName());
        } else {
            // Update first gallery image
            GalleryImage firstImage = existingImages.get(0);
            firstImage.setImageUrl(subcategory.getImage());
            firstImage.setThumbnailUrl(subcategory.getImage());
            firstImage.setTitle(subcategory.getName());
            
            galleryImageRepository.save(firstImage);
            System.out.println("Updated gallery image for subcategory: " + subcategory.getName());
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
        System.out.println("Synced gallery to subcategory image: " + subcategory.getName());
    }

    /**
     * When a gallery image is deleted, update subcategory.image if needed
     */
    @Transactional
    public void handleGalleryImageDeletion(GalleryImage deletedImage) {
        if (deletedImage.getSubcategory() == null) {
            return;
        }

        Long subcategoryId = deletedImage.getSubcategory().getId();
        Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                .orElse(null);

        if (subcategory == null) {
            return;
        }

        // Check if the deleted image was the subcategory's main image
        if (subcategory.getImage() != null && 
            subcategory.getImage().equals(deletedImage.getImageUrl())) {
            
            // Find remaining gallery images for this subcategory
            List<GalleryImage> remainingImages = galleryImageRepository
                    .findBySubcategoryIdOrderByDisplayOrderAsc(subcategoryId);

            if (remainingImages.isEmpty()) {
                // No more images, clear subcategory image
                subcategory.setImage(null);
                System.out.println("Cleared subcategory image (no gallery images left): " + subcategory.getName());
            } else {
                // Set to next available image
                subcategory.setImage(remainingImages.get(0).getImageUrl());
                System.out.println("Updated subcategory image to next gallery image: " + subcategory.getName());
            }

            subcategoryRepository.save(subcategory);
        }
    }
}
