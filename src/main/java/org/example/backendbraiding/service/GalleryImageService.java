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
import java.io.BufferedInputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        return imageRepository.findAllByOrderByDisplayOrderAsc().stream()
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
    public synchronized ImageResponse uploadImage(MultipartFile file, ImageUploadRequest request, String uploadedBy) throws IOException {
        // Validate file
        validateFile(file);
        int[] dimensions = readDimensions(file);

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
        String extension = extensionFor(file.getContentType());
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
        if (dimensions != null) {
            image.setWidth(dimensions[0]);
            image.setHeight(dimensions[1]);
        }
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

        try {
            GalleryImage saved = imageRepository.saveAndFlush(image);
            return convertToResponse(saved);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(filePath);
            throw exception;
        }
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

        removeImageReferences(image.getImageUrl());

        // Resolve the physical file path BEFORE touching the DB,
        // but only delete the file AFTER the DB commit succeeds.
        Path resolvedFilePath = null;
        if (image.getImageUrl() != null && image.getImageUrl().startsWith("/api/gallery/image/")) try {
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

        if (resolvedFilePath != null) {
            Path fileToDelete = resolvedFilePath;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        Files.deleteIfExists(fileToDelete);
                    } catch (IOException e) {
                        log.error("Failed to delete file after DB commit: {}", e.getMessage());
                    }
                }
            });
        }
    }

    @Transactional
    @CacheEvict(value = {"bookingCategories", "publicCategories", "allCategories", "galleryCards"}, allEntries = true)
    public void reorderImages(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()
                || new java.util.HashSet<>(imageIds).size() != imageIds.size()) {
            throw new IllegalArgumentException("Image order must contain unique image IDs");
        }
        List<GalleryImage> images = imageRepository.findAllById(imageIds);
        if (images.size() != imageIds.size()) {
            throw new IllegalArgumentException("One or more gallery images were not found");
        }
        java.util.Map<Long, GalleryImage> byId = images.stream()
                .collect(Collectors.toMap(GalleryImage::getId, item -> item));
        java.util.Set<Long> affectedSubcategoryIds = new java.util.HashSet<>();

        for (int i = 0; i < imageIds.size(); i++) {
            GalleryImage image = byId.get(imageIds.get(i));
            image.setDisplayOrder(i);
            if (image.getSubcategory() != null) affectedSubcategoryIds.add(image.getSubcategory().getId());
        }

        imageRepository.saveAll(images);
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

        try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
            byte[] header = input.readNBytes(12);
            if (!matchesDeclaredImageType(header, contentType)) {
                throw new RuntimeException("The file contents do not match the selected image type");
            }
        } catch (IOException exception) {
            throw new RuntimeException("Could not validate the uploaded image", exception);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private boolean matchesDeclaredImageType(byte[] bytes, String contentType) {
        if (bytes.length < 4) return false;
        return switch (contentType) {
            case "image/jpeg", "image/jpg" ->
                    (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" ->
                    bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                            && bytes[2] == 0x4e && bytes[3] == 0x47;
            case "image/webp" ->
                    bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                            && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                            && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private int[] readDimensions(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) return null; // WebP may rely on the browser/runtime codec.
            long pixels = (long) image.getWidth() * image.getHeight();
            if (pixels > 40_000_000L) {
                throw new RuntimeException("Image dimensions are too large");
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException exception) {
            throw new RuntimeException("Could not read image dimensions", exception);
        }
    }

    private void removeImageReferences(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;

        List<Category> categories = categoryRepository.findAll();
        categories.forEach(category -> {
            if (imageUrl.equals(category.getImage())) category.setImage(null);
            category.getFlippingImages().removeIf(imageUrl::equals);
        });
        categoryRepository.saveAll(categories);

        List<Subcategory> subcategories = subcategoryRepository.findAll();
        subcategories.forEach(subcategory -> {
            if (imageUrl.equals(subcategory.getImage())) subcategory.setImage(null);
        });
        subcategoryRepository.saveAll(subcategories);

        List<ServiceItem> serviceItems = serviceItemRepository.findAll();
        serviceItems.forEach(item -> {
            if (imageUrl.equals(item.getImage())) item.setImage(null);
            item.getImages().removeIf(imageUrl::equals);
            item.getSizePhotos().removeIf(imageUrl::equals);
            item.getLengthOptions().forEach(option -> {
                if (imageUrl.equals(option.getImageUrl())) option.setImageUrl(null);
            });
        });
        serviceItemRepository.saveAll(serviceItems);
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
