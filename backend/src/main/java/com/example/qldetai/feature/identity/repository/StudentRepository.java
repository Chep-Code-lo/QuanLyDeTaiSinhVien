package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.Student;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    Optional<Student> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    List<Student> findByUserIdIn(Collection<Long> userIds);

    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
