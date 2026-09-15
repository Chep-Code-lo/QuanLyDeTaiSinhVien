package com.example.qldetai.feature.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    Optional<Student> findByUserId(Long userId);

    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
