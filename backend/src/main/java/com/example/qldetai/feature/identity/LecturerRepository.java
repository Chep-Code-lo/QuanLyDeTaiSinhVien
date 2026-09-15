package com.example.qldetai.feature.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturerRepository extends JpaRepository<Lecturer, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Lecturer> findByLecturerCodeIgnoreCase(String lecturerCode);

    Optional<Lecturer> findByUserId(Long userId);

    boolean existsByLecturerCodeIgnoreCase(String lecturerCode);
}
