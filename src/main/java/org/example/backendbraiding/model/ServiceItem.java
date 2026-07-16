package org.example.backendbraiding.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_items")
@Data
public class ServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String price;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String notes;

    private String image;

    @ElementCollection
    @CollectionTable(name = "service_item_images", joinColumns = @JoinColumn(name = "service_item_id"))
    @Column(name = "image_url")
    @BatchSize(size = 50)
    private List<String> images = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "service_item_size_photos", joinColumns = @JoinColumn(name = "service_item_id"))
    @Column(name = "image_url")
    @BatchSize(size = 50)
    private List<String> sizePhotos = new ArrayList<>();

    private String link;

    private String objectPosition;

    @ElementCollection
    @CollectionTable(name = "service_item_sizes", joinColumns = @JoinColumn(name = "service_item_id"))
    @Column(name = "size")
    @BatchSize(size = 50)
    private List<String> availableSizes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "service_item_textures", joinColumns = @JoinColumn(name = "service_item_id"))
    @Column(name = "texture")
    @BatchSize(size = 50)
    private List<String> hairTextures = new ArrayList<>();

    @OneToMany(mappedBy = "serviceItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<LengthOption> lengthOptions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnore
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    @JsonIgnore
    private Subcategory subcategory;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
