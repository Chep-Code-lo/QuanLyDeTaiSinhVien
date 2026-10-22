package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.Faculty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    List<Faculty> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    Optional<Faculty> findByCodeIgnoreCase(String code);
}
