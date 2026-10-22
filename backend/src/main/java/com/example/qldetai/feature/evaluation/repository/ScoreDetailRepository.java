package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreDetail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreDetailRepository extends JpaRepository<ScoreDetail, Long> {

    List<ScoreDetail> findBySheetId(Long sheetId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query(
            "select detail from ScoreDetail detail where detail.sheet.id = :sheetId")
    List<ScoreDetail> findBySheetIdForUpdate(Long sheetId);

    void deleteBySheetId(Long sheetId);
}
