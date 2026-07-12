package org.example.backendbraiding.exception;

public class SlugAlreadyExistsException extends RuntimeException {
    
    public SlugAlreadyExistsException(String slug) {
        super(String.format("Subcategory with slug '%s' already exists", slug));
    }
}
