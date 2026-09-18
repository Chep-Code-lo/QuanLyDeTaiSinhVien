package com.example.qldetai.feature.identity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicAppointmentRepository extends JpaRepository<AcademicAppointment, Long> {

    @Query("""
            select a from AcademicAppointment a
            join fetch a.lecturer l
            join fetch l.user
            join fetch a.faculty
            left join fetch a.department
            where (:lecturerId is null or l.id = :lecturerId)
            order by a.startsAt desc
            """)
    List<AcademicAppointment> findForAdministration(@Param("lecturerId") Long lecturerId);

    @Query("""
            select count(a) > 0 from AcademicAppointment a
            where a.lecturer.user.id = :userId
              and a.positionCode = :position
              and a.faculty.id = :facultyId
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            """)
    boolean hasActiveFacultyAppointment(
            @Param("userId") Long userId,
            @Param("position") PositionCode position,
            @Param("facultyId") Long facultyId,
            @Param("now") LocalDateTime now);

    @Query("""
            select count(a) > 0 from AcademicAppointment a
            where a.lecturer.user.id = :userId
              and a.positionCode = :position
              and a.department.id = :departmentId
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            """)
    boolean hasActiveDepartmentAppointment(
            @Param("userId") Long userId,
            @Param("position") PositionCode position,
            @Param("departmentId") Long departmentId,
            @Param("now") LocalDateTime now);

    @Query("""
            select a from AcademicAppointment a
            join fetch a.lecturer l
            join fetch l.user
            join fetch a.faculty
            left join fetch a.department
            where l.user.id = :userId
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            order by a.startsAt desc
            """)
    List<AcademicAppointment> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
