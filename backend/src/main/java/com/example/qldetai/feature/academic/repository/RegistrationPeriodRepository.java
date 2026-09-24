package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationPeriodRepository extends JpaRepository<RegistrationPeriod, Long> {

    Page<RegistrationPeriod> findByFacultyId(Long facultyId, Pageable pageable);

    boolean existsByFacultyIdAndNameIgnoreCase(Long facultyId, String name);
}
