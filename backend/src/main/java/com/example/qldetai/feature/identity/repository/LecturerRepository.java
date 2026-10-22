package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LecturerRepository extends JpaRepository<Lecturer, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Lecturer> findByLecturerCodeIgnoreCase(String lecturerCode);

    Optional<Lecturer> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    List<Lecturer> findByUserIdIn(Collection<Long> userIds);

    @Query("""
            select l from Lecturer l
            join fetch l.user
            where l.department.faculty.id = :facultyId
              and l.user.status = com.example.qldetai.feature.identity.domain.UserStatus.ACTIVE
            order by l.user.fullName asc
            """)
    List<Lecturer> findActiveByFacultyId(@Param("facultyId") Long facultyId);

    boolean existsByLecturerCodeIgnoreCase(String lecturerCode);

    boolean existsByLecturerCodeIgnoreCaseAndIdNot(String lecturerCode, Long id);

    @EntityGraph(attributePaths = {"user", "department", "department.faculty"})
    @Query(
            """
            select l from Lecturer l
            join l.user u
            join l.department d
            join d.faculty f
            where u.status = :status
              and (:lecturerId is null or l.id = :lecturerId)
              and (:facultyId is null or f.id = :facultyId)
              and (:departmentId is null or d.id = :departmentId)
              and (:search is null
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(l.lecturerCode) like lower(concat('%', :search, '%')))
            """)
    Page<Lecturer> search(
            @Param("lecturerId") Long lecturerId,
            @Param("search") String search,
            @Param("facultyId") Long facultyId,
            @Param("departmentId") Long departmentId,
            @Param("status") UserStatus status,
            Pageable pageable);
}
