package com.example.qldetai.feature.academic.repository;

import com.example.qldetai.feature.academic.domain.Semester;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    List<Semester> findAllByOrderByStartsOnDesc();

    boolean existsByAcademicYearIgnoreCaseAndSemesterNo(String academicYear, int semesterNo);

    Optional<Semester> findByAcademicYearIgnoreCaseAndSemesterNo(String academicYear, int semesterNo);
}
