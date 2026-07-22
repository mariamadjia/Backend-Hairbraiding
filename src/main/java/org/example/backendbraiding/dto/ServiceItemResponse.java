package org.example.backendbraiding.dto;

import lombok.Data;
import org.example.backendbraiding.model.LengthOption;
import org.example.backendbraiding.model.ServiceItem;

import java.util.List;

@Data
public class ServiceItemResponse {
    private Long id;
    private String name;
    private String price;
    private String description;
    private String notes;
    private String image;
    private List<String> images;
    private List<String> sizePhotos;
    private String link;
    private String objectPosition;
    private Boolean foundationChoicesEnabled;
    private String knotlessPriceAdjustment;
    private Integer displayOrder;
    private List<String> availableSizes;
    private List<String> hairTextures;
    private List<LengthResponse> lengthOptions;
    private Long categoryId;
    private String categoryName;
    private Long subcategoryId;
    private String subcategoryName;

    @Data
    public static class LengthResponse {
        private Long id;
        private String name;
        private String price;
        private String notes;
        private String imageUrl;
    }

    public static ServiceItemResponse from(ServiceItem service) {
        ServiceItemResponse response = new ServiceItemResponse();
        response.setId(service.getId());
        response.setName(service.getName());
        response.setPrice(service.getPrice());
        response.setDescription(service.getDescription());
        response.setNotes(service.getNotes());
        response.setImage(service.getImage());
        response.setImages(service.getImages());
        response.setSizePhotos(service.getSizePhotos());
        response.setLink(service.getLink());
        response.setObjectPosition(service.getObjectPosition());
        response.setFoundationChoicesEnabled(service.getFoundationChoicesEnabled());
        response.setKnotlessPriceAdjustment(service.getKnotlessPriceAdjustment());
        response.setDisplayOrder(service.getDisplayOrder());
        response.setAvailableSizes(service.getAvailableSizes());
        response.setHairTextures(service.getHairTextures());
        response.setLengthOptions(service.getLengthOptions().stream().map(ServiceItemResponse::lengthFrom).toList());
        response.setCategoryId(service.getCategory() == null ? null : service.getCategory().getId());
        response.setCategoryName(service.getCategory() == null ? null : service.getCategory().getName());
        response.setSubcategoryId(service.getSubcategory() == null ? null : service.getSubcategory().getId());
        response.setSubcategoryName(service.getSubcategory() == null ? null : service.getSubcategory().getName());
        return response;
    }

    private static LengthResponse lengthFrom(LengthOption option) {
        LengthResponse response = new LengthResponse();
        response.setId(option.getId());
        response.setName(option.getName());
        response.setPrice(option.getPrice());
        response.setNotes(option.getNotes());
        response.setImageUrl(option.getImageUrl());
        return response;
    }
}
