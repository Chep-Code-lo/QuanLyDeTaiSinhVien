package com.example.qldetai.feature.evaluation.repository;

import com.example.qldetai.feature.evaluation.domain.ScoreDetail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ScoreDetailRepository extends JpaRepository<ScoreDetail, Long> {

    interface SheetTotal {
        Long getSheetId();
        BigDecimal getTotalScore();
    }

    @Query("select detail.sheet.id as sheetId, sum(detail.score) as totalScore from ScoreDetail detail where detail.sheet.id in :sheetIds group by detail.sheet.id")
    List<SheetTotal> totalsBySheetIds(Collection<Long> sheetIds);

    List<ScoreDetail> findBySheetId(Long sheetId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query(
            "select detail from ScoreDetail detail where detail.sheet.id = :sheetId")
    List<ScoreDetail> findBySheetIdForUpdate(Long sheetId);

    void deleteBySheetId(Long sheetId);
}
