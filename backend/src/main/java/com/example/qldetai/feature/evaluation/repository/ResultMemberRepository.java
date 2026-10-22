package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ResultMember;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResultMemberRepository extends JpaRepository<ResultMember, Long> {

    List<ResultMember> findByResultIdOrderByIdAsc(Long resultId);

    List<ResultMember> findByResultIdInOrderByIdAsc(Collection<Long> resultIds);

    boolean existsByResultIdAndStudentId(Long resultId, Long studentId);
}
