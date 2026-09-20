package com.example.qldetai.feature.progress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ReportSubmissionRepository extends JpaRepository<ReportSubmission, Long> {
 List<ReportSubmission> findByMilestoneIdOrderByVersionNoDesc(Long milestoneId);
 long countByMilestoneId(Long milestoneId);

 @Query("""
         select count(report) > 0 from ReportSubmission report
         where report.file.id = :fileId and (
             exists (select member.id from GroupMember member
                     where member.group = report.milestone.assignment.group
                       and member.student.user.id = :userId and member.leftAt is null)
             or exists (select supervisor.id from TopicSupervisor supervisor
                        where supervisor.topic = report.milestone.assignment.topic
                          and supervisor.lecturer.user.id = :userId)
             or exists (select evaluation.id from EvaluationAssignment evaluation
                        where evaluation.assignment = report.milestone.assignment
                          and evaluation.lecturer.user.id = :userId
                          and evaluation.status = com.example.qldetai.feature.evaluation.EvaluationStatus.ACTIVE)
         )
         """)
 boolean canReadFile(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
