package org.example.backendbraiding.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "homepage_settings")
public class HomepageSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_video_src")
    private String heroVideoSrc;

    @Column(name = "use_hero_video")
    private Boolean useHeroVideo;

    @Column(name = "hero_images", columnDefinition = "TEXT")
    private String heroImages; // JSON string array of image paths

    @Column(name = "welcome_items", columnDefinition = "TEXT")
    private String welcomeItems; // JSON string

    @Column(name = "gallery_collections", columnDefinition = "TEXT")
    private String galleryCollections; // JSON string

    @Column(name = "braid_book_styles", columnDefinition = "TEXT")
    private String braidBookStyles; // JSON string

    @Column(name = "footer_video_src")
    private String footerVideoSrc;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    private Long version;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHeroVideoSrc() {
        return heroVideoSrc;
    }

    public void setHeroVideoSrc(String heroVideoSrc) {
        this.heroVideoSrc = heroVideoSrc;
    }

    public Boolean getUseHeroVideo() {
        return useHeroVideo;
    }

    public void setUseHeroVideo(Boolean useHeroVideo) {
        this.useHeroVideo = useHeroVideo;
    }

    public String getHeroImages() {
        return heroImages;
    }

    public void setHeroImages(String heroImages) {
        this.heroImages = heroImages;
    }

    public String getWelcomeItems() {
        return welcomeItems;
    }

    public void setWelcomeItems(String welcomeItems) {
        this.welcomeItems = welcomeItems;
    }

    public String getGalleryCollections() {
        return galleryCollections;
    }

    public void setGalleryCollections(String galleryCollections) {
        this.galleryCollections = galleryCollections;
    }

    public String getBraidBookStyles() {
        return braidBookStyles;
    }

    public void setBraidBookStyles(String braidBookStyles) {
        this.braidBookStyles = braidBookStyles;
    }

    public String getFooterVideoSrc() {
        return footerVideoSrc;
    }

    public void setFooterVideoSrc(String footerVideoSrc) {
        this.footerVideoSrc = footerVideoSrc;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
