package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ResultMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMemberRepository extends JpaRepository<ResultMember, Long> {

    List<ResultMember> findByResultIdOrderByIdAsc(Long resultId);

    boolean existsByResultIdAndStudentId(Long resultId, Long studentId);
}
