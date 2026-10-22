package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreDetail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreDetailRepository extends JpaRepository<ScoreDetail, Long> {

    List<ScoreDetail> findBySheetId(Long sheetId);

    void deleteBySheetId(Long sheetId);
}
