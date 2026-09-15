package com.example.qldetai.feature.groupregistration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    Page<StudentGroup> findByPeriodId(Long periodId, Pageable pageable);
}
