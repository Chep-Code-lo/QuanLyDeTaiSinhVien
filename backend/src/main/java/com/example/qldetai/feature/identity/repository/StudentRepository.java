package com.example.qldetai.feature.identity.repository;

import com.example.qldetai.feature.identity.domain.Student;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = {"user", "user.userRoles", "user.userRoles.role"})
    Optional<Student> findByStudentCodeIgnoreCase(String studentCode);

    Optional<Student> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    List<Student> findByUserIdIn(Collection<Long> userIds);

    @Query("""
            select s from Student s
            join fetch s.user
            where s.department.faculty.id = :facultyId
              and s.user.status = com.example.qldetai.feature.identity.domain.UserStatus.ACTIVE
            order by s.user.fullName asc
            """)
    List<Student> findActiveByFacultyId(@Param("facultyId") Long facultyId);

    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
