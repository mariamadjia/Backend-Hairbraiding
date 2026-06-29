package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);
    List<Subcategory> findByCategoryId(Long categoryId);
    Optional<Subcategory> findBySlug(String slug);
}
