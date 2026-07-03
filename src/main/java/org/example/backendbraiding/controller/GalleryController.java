package org.example.backendbraiding.controller;

import org.example.backendbraiding.dto.ImageResponse;
import org.example.backendbraiding.dto.ImageUpdateRequest;
import org.example.backendbraiding.dto.ImageUploadRequest;
import org.example.backendbraiding.service.GalleryImageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gallery")
public class GalleryController {
    private final GalleryImageService galleryImageService;

    public GalleryController(GalleryImageService galleryImageService) {
        this.galleryImageService = galleryImageService;
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getAllImages() {
        return ResponseEntity.ok(galleryImageService.getAllImages());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<ImageResponse>> getImagesByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(galleryImageService.getImagesByCategory(categoryId));
    }

    @GetMapping("/subcategory/{subcategoryId}")
    public ResponseEntity<List<ImageResponse>> getImagesBySubcategory(@PathVariable Long subcategoryId) {
        return ResponseEntity.ok(galleryImageService.getImagesBySubcategory(subcategoryId));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<ImageResponse>> getFeaturedImages() {
        return ResponseEntity.ok(galleryImageService.getFeaturedImages());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ImageResponse>> searchImages(@RequestParam String q) {
        return ResponseEntity.ok(galleryImageService.searchImages(q));
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<List<ImageResponse>> getImagesByTag(@PathVariable String tag) {
        return ResponseEntity.ok(galleryImageService.getImagesByTag(tag));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        return ResponseEntity.ok(galleryImageService.getAllTags());
    }

    @PostMapping("/upload")
    public ResponseEntity<ImageResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(required = false) Long serviceItemId,
            @RequestParam(required = false, defaultValue = "false") Boolean isFeatured,
            @RequestParam(required = false, defaultValue = "false") Boolean isHero,
            Authentication authentication) throws IOException {
        
        ImageUploadRequest request = new ImageUploadRequest();
        request.setTitle(title != null ? title : file.getOriginalFilename());
        request.setDescription(description);
        request.setAltText(altText);
        request.setTags(tags);
        request.setCategoryId(categoryId);
        request.setSubcategoryId(subcategoryId);
        request.setServiceItemId(serviceItemId);
        request.setIsFeatured(isFeatured);
        request.setIsHero(isHero);

        String uploadedBy = authentication != null ? authentication.getName() : "admin";
        ImageResponse response = galleryImageService.uploadImage(file, request, uploadedBy);
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ImageResponse> updateImage(
            @PathVariable Long id,
            @RequestBody ImageUpdateRequest request) {
        return ResponseEntity.ok(galleryImageService.updateImage(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteImage(@PathVariable Long id) {
        galleryImageService.deleteImage(id);
        return ResponseEntity.ok(Map.of("message", "Image deleted successfully"));
    }

    @PostMapping("/reorder")
    public ResponseEntity<Map<String, String>> reorderImages(@RequestBody List<Long> imageIds) {
        galleryImageService.reorderImages(imageIds);
        return ResponseEntity.ok(Map.of("message", "Images reordered successfully"));
    }

    @CrossOrigin(origins = {"https://hair-braiding-coral.vercel.app", "http://localhost:3000", "http://localhost:3001"})
    @GetMapping("/image/{filename:.+}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        try {
            String uploadDir = System.getenv("UPLOAD_DIR") != null 
                ? System.getenv("UPLOAD_DIR") 
                : "public/Gallery/uploads";
            
            Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = "application/octet-stream";
                if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (filename.endsWith(".png")) {
                    contentType = "image/png";
                } else if (filename.endsWith(".webp")) {
                    contentType = "image/webp";
                } else if (filename.endsWith(".gif")) {
                    contentType = "image/gif";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
