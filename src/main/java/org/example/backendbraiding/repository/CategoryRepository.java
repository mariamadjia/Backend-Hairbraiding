package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories LEFT JOIN FETCH c.items ORDER BY c.displayOrder ASC")
    List<Category> findAllWithSubcategoriesAndItems();

    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
