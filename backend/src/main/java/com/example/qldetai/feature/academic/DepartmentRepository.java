package com.example.qldetai.feature.academic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByFacultyIdOrderByNameAsc(Long facultyId);

    List<Department> findAllByOrderByNameAsc();

    boolean existsByFacultyIdAndCodeIgnoreCase(Long facultyId, String code);

    Optional<Department> findByFacultyIdAndCodeIgnoreCase(Long facultyId, String code);
}
