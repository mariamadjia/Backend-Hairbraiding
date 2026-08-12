package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.BookingAddOn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAddOnRepository extends JpaRepository<BookingAddOn, Long> {
    List<BookingAddOn> findAllByOrderByNameAsc();
}
