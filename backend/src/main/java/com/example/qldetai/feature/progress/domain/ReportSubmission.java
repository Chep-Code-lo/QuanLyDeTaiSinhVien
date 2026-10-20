package com.example.qldetai.feature.progress.domain;

import com.example.qldetai.feature.identity.domain.Student;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "report_submissions")
@Getter
@Setter
@NoArgsConstructor
public class ReportSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by")
    private Student submittedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
