package org.example.backendbraiding.service;

import org.example.backendbraiding.dto.ImageResponse;
import org.example.backendbraiding.dto.ImageUpdateRequest;
import org.example.backendbraiding.dto.ImageUploadRequest;
import org.example.backendbraiding.model.*;
import org.example.backendbraiding.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GalleryImageService {
    private static final Logger log = LoggerFactory.getLogger(GalleryImageService.class);
    private final GalleryImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ServiceItemRepository serviceItemRepository;
    
    // Use Render persistent disk if available, fallback to local for development
    private static final String UPLOAD_DIR = System.getenv("UPLOAD_DIR") != null 
        ? System.getenv("UPLOAD_DIR") 
        : "public/Gallery/uploads";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/jpg");
    private static final int MAX_HERO_IMAGES = 5;

    public GalleryImageService(
            GalleryImageRepository imageRepository,
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository,
            ServiceItemRepository serviceItemRepository) {
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.serviceItemRepository = serviceItemRepository;
    }

    public List<ImageResponse> getAllImages() {
        return imageRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ImageResponse> getImagesByCategory(Long categoryId) {
        return imageRepository.findByCategoryIdOrderByDisplayOrderAsc(categoryId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ImageResponse> getImagesBySubcategory(Long subcategoryId) {
        return imageRepository.findBySubcategoryIdOrderByDisplayOrderAsc(subcategoryId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ImageResponse> getFeaturedImages() {
        return imageRepository.findByIsFeaturedTrueOrderByDisplayOrderAsc().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ImageResponse> searchImages(String query) {
        return imageRepository.searchImages(query).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<ImageResponse> getImagesByTag(String tag) {
        return imageRepository.findByTag(tag).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<String> getAllTags() {
        return imageRepository.findAllTags();
    }

    public List<ImageResponse> getHeroImages() {
        return imageRepository.findByIsHeroTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ImageResponse uploadImage(MultipartFile file, ImageUploadRequest request, String uploadedBy) throws IOException {
        // Validate file
        validateFile(file);

        // Enforce maximum hero images limit
        if (Boolean.TRUE.equals(request.getIsHero())
                && imageRepository.countByIsHeroTrue() >= MAX_HERO_IMAGES) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A maximum of 5 hero images is allowed. Remove an existing hero image before uploading another one."
            );
        }

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Create image entity
        GalleryImage image = new GalleryImage();
        image.setTitle(request.getTitle());
        image.setDescription(request.getDescription());
        image.setAltText(request.getAltText());
        
        // Store correct image route that matches the serveImage endpoint
        String imageEndpoint = "/api/gallery/image/" + filename;
        image.setImageUrl(imageEndpoint);
        image.setThumbnailUrl(imageEndpoint); // TODO: Generate thumbnail
        
        image.setFileSize(file.getSize());
        image.setMimeType(file.getContentType());
        image.setTags(request.getTags() != null ? request.getTags() : List.of());
        image.setIsFeatured(request.getIsFeatured());
        image.setIsHero(request.getIsHero());
        image.setUploadedBy(uploadedBy);

        // Set relationships
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            image.setCategory(category);
        }

        if (request.getSubcategoryId() != null) {
            Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                    .orElseThrow(() -> new RuntimeException("Subcategory not found"));
            image.setSubcategory(subcategory);
        }

        if (request.getServiceItemId() != null) {
            ServiceItem serviceItem = serviceItemRepository.findById(request.getServiceItemId())
                    .orElseThrow(() -> new RuntimeException("Service item not found"));
            image.setServiceItem(serviceItem);
        }

        // Set display order (last)
        Integer maxOrder = imageRepository.findMaxDisplayOrder();
        image.setDisplayOrder((maxOrder != null ? maxOrder : 0) + 1);

        GalleryImage saved = imageRepository.save(image);
        
        return convertToResponse(saved);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ImageResponse updateImage(Long id, ImageUpdateRequest request) {
        GalleryImage image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        // Protect against creating 6th hero image through update
        boolean currentlyHero = Boolean.TRUE.equals(image.getIsHero());
        boolean willBeHero = request.getIsHero() != null
                ? request.getIsHero()
                : currentlyHero;

        if (!currentlyHero && willBeHero
                && imageRepository.countByIsHeroTrue() >= MAX_HERO_IMAGES) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A maximum of 5 hero images is allowed."
            );
        }

        if (request.getTitle() != null) image.setTitle(request.getTitle());
        if (request.getDescription() != null) image.setDescription(request.getDescription());
        if (request.getAltText() != null) image.setAltText(request.getAltText());
        if (request.getTags() != null) image.setTags(request.getTags());
        if (request.getIsFeatured() != null) image.setIsFeatured(request.getIsFeatured());
        if (request.getIsHero() != null) image.setIsHero(request.getIsHero());
        if (request.getDisplayOrder() != null) image.setDisplayOrder(request.getDisplayOrder());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            image.setCategory(category);
        }

        if (request.getSubcategoryId() != null) {
            Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                    .orElseThrow(() -> new RuntimeException("Subcategory not found"));
            image.setSubcategory(subcategory);
        }

        GalleryImage updated = imageRepository.save(image);
        
        return convertToResponse(updated);
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void deleteImage(Long id) {
        GalleryImage image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        Long subcategoryId = image.getSubcategory() != null
                ? image.getSubcategory().getId()
                : null;

        // Resolve the physical file path BEFORE touching the DB,
        // but only delete the file AFTER the DB commit succeeds.
        Path resolvedFilePath = null;
        try {
            String filename = Paths.get(image.getImageUrl())
                    .getFileName()
                    .toString();

            Path uploadPath = Paths.get(UPLOAD_DIR)
                    .toAbsolutePath()
                    .normalize();

            Path filePath = uploadPath
                    .resolve(filename)
                    .normalize();

            if (!filePath.startsWith(uploadPath)) {
                throw new IllegalStateException("Invalid image file path");
            }

            resolvedFilePath = filePath;
        } catch (Exception e) {
            log.warn("Could not resolve file path for deletion: {}", e.getMessage());
        }

        // Commit DB deletion first — if this throws, the file is untouched
        imageRepository.delete(image);
        imageRepository.flush();

        // Now safe to delete the physical file
        if (resolvedFilePath != null) {
            try {
                Files.deleteIfExists(resolvedFilePath);
            } catch (IOException e) {
                log.error("Failed to delete file after DB commit: {}", e.getMessage());
            }
        }
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void reorderImages(List<Long> imageIds) {
        java.util.Set<Long> affectedSubcategoryIds = new java.util.HashSet<>();

        for (int i = 0; i < imageIds.size(); i++) {
            final int displayOrder = i;
            Long imageId = imageIds.get(i);
            imageRepository.findById(imageId).ifPresent(image -> {
                image.setDisplayOrder(displayOrder);
                imageRepository.save(image);

                if (image.getSubcategory() != null) {
                    affectedSubcategoryIds.add(image.getSubcategory().getId());
                }
            });
        }

        imageRepository.flush();
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public ImageResponse registerImageUrl(String imageUrl, String title, Long categoryId, Long subcategoryId) {
        // Guard: if this URL is already a gallery record for this subcategory, return it as-is
        if (subcategoryId != null) {
            List<GalleryImage> existing = imageRepository.findBySubcategoryIdOrderByDisplayOrderAsc(subcategoryId);
            for (GalleryImage g : existing) {
                if (imageUrl.equals(g.getImageUrl())) {
                    return convertToResponse(g);
                }
            }
        }

        GalleryImage image = new GalleryImage();
        image.setTitle(title != null && !title.isBlank() ? title : "Image");
        image.setImageUrl(imageUrl);
        image.setThumbnailUrl(imageUrl);

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            image.setCategory(category);
        }

        if (subcategoryId != null) {
            Subcategory subcategory = subcategoryRepository.findById(subcategoryId)
                    .orElseThrow(() -> new RuntimeException("Subcategory not found"));
            image.setSubcategory(subcategory);
        }

        Integer maxOrder = imageRepository.findMaxDisplayOrder();
        image.setDisplayOrder((maxOrder != null ? maxOrder : 0) + 1);
        image.setUploadedBy("system");

        GalleryImage saved = imageRepository.save(image);

        return convertToResponse(saved);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds maximum limit of 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new RuntimeException("Invalid file type. Only JPEG, PNG, and WEBP are allowed");
        }
    }

    private ImageResponse convertToResponse(GalleryImage image) {
        ImageResponse response = new ImageResponse();
        response.setId(image.getId());
        response.setTitle(image.getTitle());
        response.setDescription(image.getDescription());
        response.setImageUrl(image.getImageUrl());
        response.setThumbnailUrl(image.getThumbnailUrl());
        response.setAltText(image.getAltText());
        response.setFileSize(image.getFileSize());
        response.setWidth(image.getWidth());
        response.setHeight(image.getHeight());
        response.setMimeType(image.getMimeType());
        response.setDisplayOrder(image.getDisplayOrder());
        response.setIsFeatured(image.getIsFeatured());
        response.setIsHero(image.getIsHero());
        response.setTags(image.getTags());
        response.setCreatedAt(image.getCreatedAt());
        response.setUpdatedAt(image.getUpdatedAt());
        response.setUploadedBy(image.getUploadedBy());

        if (image.getCategory() != null) {
            response.setCategoryId(image.getCategory().getId());
            response.setCategoryName(image.getCategory().getName());
        }

        if (image.getSubcategory() != null) {
            response.setSubcategoryId(image.getSubcategory().getId());
            response.setSubcategoryName(image.getSubcategory().getName());
            response.setSubcategoryDisplayOrder(image.getSubcategory().getDisplayOrder());
        }

        if (image.getServiceItem() != null) {
            response.setServiceItemId(image.getServiceItem().getId());
            response.setServiceItemName(image.getServiceItem().getName());
        }

        return response;
    }
}
