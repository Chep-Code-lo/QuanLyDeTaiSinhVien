package com.example.qldetai.feature.progress.domain;

import com.example.qldetai.common.persistence.CreatedAtEntity;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "milestones") @Getter @Setter @NoArgsConstructor
public class Milestone extends CreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignment_id") private ProjectAssignment assignment;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "milestone_type", nullable = false) private MilestoneType milestoneType;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "due_at", nullable = false) private LocalDateTime dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MilestoneStatus status = MilestoneStatus.OPEN;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;
    @Version @Column(nullable = false) private int version;
}
