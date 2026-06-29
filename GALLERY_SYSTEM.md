# Gallery Management System

## Overview
Complete gallery management system with Spring Boot backend and Next.js frontend.

## Features
- ✅ 96 images imported from categories.json
- ✅ Full CRUD operations (Create, Read, Update, Delete)
- ✅ Search and filter by category, subcategory, tags
- ✅ Featured images support
- ✅ Image upload with validation (10MB max, JPEG/PNG/WEBP)
- ✅ SEO-friendly (alt text, metadata)
- ✅ Secure (JWT authentication for uploads/edits)

## Backend (Spring Boot)

### Entities
- `GalleryImage` - Main image entity with metadata
- Relationships: Category, Subcategory, ServiceItem

### API Endpoints

**Public (No Auth Required):**
- `GET /api/gallery` - Get all images
- `GET /api/gallery/featured` - Get featured images
- `GET /api/gallery/category/{id}` - Get images by category
- `GET /api/gallery/subcategory/{id}` - Get images by subcategory
- `GET /api/gallery/search?q={query}` - Search images
- `GET /api/gallery/tag/{tag}` - Filter by tag
- `GET /api/gallery/tags` - Get all tags

**Authenticated (Requires JWT):**
- `POST /api/gallery/upload` - Upload new image
- `PUT /api/gallery/{id}` - Update image metadata
- `DELETE /api/gallery/{id}` - Delete image
- `POST /api/gallery/reorder` - Reorder images

### Database Tables
- `gallery_images` - Main images table
- `gallery_image_tags` - Tags collection

## Frontend (Next.js)

### Admin Gallery
**Location:** `/app/admin/components/GalleryAdminNew.tsx`

**Features:**
- Grid view of all images
- Filter by category (sidebar)
- Filter by tags
- Search functionality
- Upload modal with drag & drop
- Edit modal for metadata
- Toggle featured status
- Delete with confirmation

**Usage:**
1. Login at `http://localhost:3000/admin`
2. Click "Gallery" in sidebar
3. Use filters, search, or upload new images

### Public Gallery
**Location:** `/app/gallery/page.jsx`

**Features:**
- Displays images by category/subcategory
- Fetches from backend API
- Responsive grid layout

## File Structure

### Backend
```
src/main/java/org/example/backendbraiding/
├── model/
│   └── GalleryImage.java
├── repository/
│   └── GalleryImageRepository.java
├── service/
│   └── GalleryImageService.java
├── controller/
│   └── GalleryController.java
└── dto/
    ├── ImageUploadRequest.java
    ├── ImageUpdateRequest.java
    └── ImageResponse.java
```

### Frontend
```
app/
├── admin/
│   └── components/
│       └── GalleryAdminNew.tsx
├── gallery/
│   └── page.jsx
└── lib/
    └── api/
        └── gallery.ts
```

## Configuration

### Security (SecurityConfig.java)
```java
.requestMatchers("/api/gallery", "/api/gallery/featured", 
    "/api/gallery/category/**", "/api/gallery/subcategory/**", 
    "/api/gallery/tag/**", "/api/gallery/tags").permitAll()
.requestMatchers("/api/gallery/**").authenticated()
```

### Image Storage
- Images stored in: `public/Gallery/uploads/`
- Max file size: 10MB
- Allowed types: JPEG, PNG, WEBP

## Usage Examples

### Upload Image (Admin)
1. Click "Upload Images" button
2. Drag & drop or select file
3. Fill in metadata (title, description, alt text, category, tags)
4. Mark as featured (optional)
5. Click "Upload Image"

### Edit Image (Admin)
1. Hover over image
2. Click edit icon (pencil)
3. Update metadata
4. Click "Save Changes"

### Search Images
1. Type query in search box
2. Click "Search" or press Enter
3. Results filter automatically

### Filter by Category
1. Click category in left sidebar
2. Images filter to that category
3. Click "Clear Filters" to reset

## API Client Usage (Frontend)

```typescript
import { galleryApi } from '@/lib/api/gallery';

// Get all images
const images = await galleryApi.getAllImages();

// Search
const results = await galleryApi.searchImages('braids');

// Upload
await galleryApi.uploadImage({
  file: fileObject,
  title: 'My Image',
  tags: ['braids', 'protective'],
  categoryId: 1,
  isFeatured: true
});

// Update
await galleryApi.updateImage(imageId, {
  title: 'Updated Title',
  isFeatured: true
});

// Delete
await galleryApi.deleteImage(imageId);
```

## Statistics
- **Total Images:** 96
- **Categories:** 6 (Box Braids, Conrows, Miracle Knots, Twists, Locs, Crochets)
- **Tags:** Multiple (french-curls, bohemian, knotless, etc.)

## Maintenance

### Add New Images
Use the admin panel upload feature or API endpoint.

### Backup Images
Images are stored in database with file paths pointing to `public/Gallery/` folder.

### Clean Up
All temporary import files have been removed. System is production-ready.
