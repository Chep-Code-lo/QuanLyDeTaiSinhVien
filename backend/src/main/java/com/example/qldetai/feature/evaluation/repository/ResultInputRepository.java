package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ResultInput;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResultInputRepository extends JpaRepository<ResultInput, Long> {

    List<ResultInput> findByResultIdOrderByIdAsc(Long resultId);

    @EntityGraph(attributePaths = "component")
    List<ResultInput> findByResultIdInOrderByIdAsc(Collection<Long> resultIds);

    /**
     * A source score sheet becomes immutable as soon as a result version has
     * captured it. This includes finalized, published, and superseded results.
     */
    boolean existsBySheetId(Long sheetId);
}
