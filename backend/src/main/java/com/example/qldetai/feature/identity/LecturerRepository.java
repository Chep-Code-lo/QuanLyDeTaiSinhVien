package com.example.qldetai.feature.identity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LecturerRepository extends JpaRepository<Lecturer, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Lecturer> findByLecturerCodeIgnoreCase(String lecturerCode);

    Optional<Lecturer> findByUserId(Long userId);

    boolean existsByLecturerCodeIgnoreCase(String lecturerCode);

    @EntityGraph(attributePaths = {"user", "department", "department.faculty"})
    @Query("""
            select l from Lecturer l
            join l.user u
            join l.department d
            join d.faculty f
            where u.status = :status
              and (:facultyId is null or f.id = :facultyId)
              and (:departmentId is null or d.id = :departmentId)
              and (:search is null
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(l.lecturerCode) like lower(concat('%', :search, '%')))
            """)
    Page<Lecturer> search(
            @Param("search") String search,
            @Param("facultyId") Long facultyId,
            @Param("departmentId") Long departmentId,
            @Param("status") UserStatus status,
            Pageable pageable);
}
