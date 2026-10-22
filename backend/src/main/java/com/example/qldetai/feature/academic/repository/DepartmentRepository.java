package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.Department;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @EntityGraph(attributePaths = "faculty")
    List<Department> findByFacultyIdOrderByNameAsc(Long facultyId);

    @EntityGraph(attributePaths = "faculty")
    List<Department> findAllByOrderByNameAsc();

    boolean existsByFacultyIdAndCodeIgnoreCase(Long facultyId, String code);

    Optional<Department> findByFacultyIdAndCodeIgnoreCase(Long facultyId, String code);
}
