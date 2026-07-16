package org.example.backendbraiding.util;

public class SlugUtil {
    /**
     * Generate a URL-friendly slug from a name string.
     * Converts to lowercase, removes special characters, replaces spaces with hyphens.
     */
    public static String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }
}
