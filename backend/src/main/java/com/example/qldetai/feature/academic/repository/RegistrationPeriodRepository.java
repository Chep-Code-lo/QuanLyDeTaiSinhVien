package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationPeriodRepository extends JpaRepository<RegistrationPeriod, Long> {

    Page<RegistrationPeriod> findByFacultyId(Long facultyId, Pageable pageable);

    List<RegistrationPeriod> findByStatusOrderByRegistrationEndAtAsc(
            RegistrationPeriodStatus status);

    boolean existsByFacultyIdAndNameIgnoreCase(Long facultyId, String name);
}
