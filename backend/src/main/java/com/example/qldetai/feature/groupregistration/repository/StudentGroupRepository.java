package com.example.qldetai.feature.groupregistration.repository;

import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    @Override
    @EntityGraph(attributePaths = {"period", "createdBy.user"})
    Page<StudentGroup> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"period", "createdBy.user"})
    Page<StudentGroup> findByPeriodId(Long periodId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groupEntity from StudentGroup groupEntity where groupEntity.id = :id")
    Optional<StudentGroup> findByIdForUpdate(@Param("id") Long id);
}
