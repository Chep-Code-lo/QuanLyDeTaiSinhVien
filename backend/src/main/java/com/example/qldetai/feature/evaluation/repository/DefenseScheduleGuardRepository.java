package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.DefenseScheduleGuard;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DefenseScheduleGuardRepository extends JpaRepository<DefenseScheduleGuard, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from DefenseScheduleGuard guard where guard.id = 1")
    Optional<DefenseScheduleGuard> lockScheduling();
}
