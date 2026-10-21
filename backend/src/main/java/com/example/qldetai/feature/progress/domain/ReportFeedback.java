package com.example.qldetai.feature.progress.domain;

import com.example.qldetai.common.persistence.CreatedAtEntity;
import com.example.qldetai.feature.identity.domain.Lecturer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "report_feedback") @Getter @Setter @NoArgsConstructor
public class ReportFeedback extends CreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "submission_id") private ReportSubmission submission;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lecturer_id") private Lecturer lecturer;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
}
