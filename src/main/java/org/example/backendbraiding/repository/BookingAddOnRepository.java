package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.BookingAddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingAddOnRepository extends JpaRepository<BookingAddOn, Long> {
    List<BookingAddOn> findAllByOrderByNameAsc();
    @Query("SELECT addOn FROM BookingAddOn addOn WHERE addOn.active = true ORDER BY LOWER(addOn.name), addOn.id")
    List<BookingAddOn> findActiveLibrary();
}
