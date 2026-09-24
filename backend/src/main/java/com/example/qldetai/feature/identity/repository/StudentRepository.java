package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.Student;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    Optional<Student> findByUserId(Long userId);

    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
