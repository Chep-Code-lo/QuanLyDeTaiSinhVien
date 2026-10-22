package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.Semester;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    List<Semester> findAllByOrderByStartsOnDesc();

    Optional<Semester> findFirstByStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByStartsOnDesc(
            LocalDate startsOn, LocalDate endsOn);

    Optional<Semester> findFirstByStartsOnLessThanEqualOrderByStartsOnDesc(LocalDate startsOn);

    boolean existsByAcademicYearIgnoreCaseAndSemesterNo(String academicYear, int semesterNo);

    Optional<Semester> findByAcademicYearIgnoreCaseAndSemesterNo(
            String academicYear, int semesterNo);
}
