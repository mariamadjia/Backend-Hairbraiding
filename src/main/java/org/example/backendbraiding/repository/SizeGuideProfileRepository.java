package org.example.backendbraiding.repository;

import org.example.backendbraiding.model.SizeGuideProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SizeGuideProfileRepository extends JpaRepository<SizeGuideProfile, Long> {
    List<SizeGuideProfile> findAllByOrderByDisplayOrderAscIdAsc();
}
