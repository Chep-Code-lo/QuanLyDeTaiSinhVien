package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreDetailRepository extends JpaRepository<ScoreDetail, Long> {

    List<ScoreDetail> findBySheetId(Long sheetId);

    void deleteBySheetId(Long sheetId);
}
