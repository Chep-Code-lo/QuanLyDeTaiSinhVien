-- MySQL 8.4 bootstrap: foreign keys, after all tables and local indexes exist.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
USE `ql_de_tai_sinh_vien`;

-- PHẦN 2: KHÓA NGOẠI (thêm sau khi mọi bảng và UNIQUE đã tồn tại)

ALTER TABLE `departments` ADD CONSTRAINT `fk_departments_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_user_roles_02` FOREIGN KEY (`role_id`)
  REFERENCES `roles` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `students` ADD CONSTRAINT `fk_students_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `students` ADD CONSTRAINT `fk_students_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `lecturers` ADD CONSTRAINT `fk_lecturers_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `lecturers` ADD CONSTRAINT `fk_lecturers_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_01` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_02` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_03` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_04` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `academic_appointments` ADD CONSTRAINT `fk_academic_appointments_05` FOREIGN KEY (`department_id`, `faculty_id`)
  REFERENCES `departments` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_02` FOREIGN KEY (`semester_id`)
  REFERENCES `semesters` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `registration_periods` ADD CONSTRAINT `fk_registration_periods_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `student_groups` ADD CONSTRAINT `fk_student_groups_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `student_groups` ADD CONSTRAINT `fk_student_groups_02` FOREIGN KEY (`created_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_02` FOREIGN KEY (`department_id`)
  REFERENCES `departments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_03` FOREIGN KEY (`proposed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_04` FOREIGN KEY (`reserved_group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_05` FOREIGN KEY (`period_id`, `faculty_id`)
  REFERENCES `registration_periods` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_06` FOREIGN KEY (`department_id`, `faculty_id`)
  REFERENCES `departments` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topics` ADD CONSTRAINT `fk_topics_07` FOREIGN KEY (`reserved_group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_01` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_supervisors` ADD CONSTRAINT `fk_topic_supervisors_03` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_reviews` ADD CONSTRAINT `fk_topic_reviews_01` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_reviews` ADD CONSTRAINT `fk_topic_reviews_02` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_01` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_02` FOREIGN KEY (`student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_members` ADD CONSTRAINT `fk_group_members_03` FOREIGN KEY (`group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_01` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_02` FOREIGN KEY (`invited_student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `group_invitations` ADD CONSTRAINT `fk_group_invitations_03` FOREIGN KEY (`invited_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_02` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_03` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_04` FOREIGN KEY (`registered_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_05` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_06` FOREIGN KEY (`group_id`, `period_id`)
  REFERENCES `student_groups` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `topic_registrations` ADD CONSTRAINT `fk_topic_registrations_07` FOREIGN KEY (`topic_id`, `period_id`)
  REFERENCES `topics` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_01` FOREIGN KEY (`registration_id`)
  REFERENCES `topic_registrations` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_02` FOREIGN KEY (`group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_03` FOREIGN KEY (`topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_04` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_05` FOREIGN KEY (`assigned_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `project_assignments` ADD CONSTRAINT `fk_project_assignments_06` FOREIGN KEY (`registration_id`, `group_id`, `topic_id`, `period_id`)
  REFERENCES `topic_registrations` (`id`, `group_id`, `topic_id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `milestones` ADD CONSTRAINT `fk_milestones_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `milestones` ADD CONSTRAINT `fk_milestones_02` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `stored_files` ADD CONSTRAINT `fk_stored_files_01` FOREIGN KEY (`uploaded_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_01` FOREIGN KEY (`milestone_id`)
  REFERENCES `milestones` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_02` FOREIGN KEY (`file_id`)
  REFERENCES `stored_files` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_submissions` ADD CONSTRAINT `fk_report_submissions_03` FOREIGN KEY (`submitted_by`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_feedback` ADD CONSTRAINT `fk_report_feedback_01` FOREIGN KEY (`submission_id`)
  REFERENCES `report_submissions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `report_feedback` ADD CONSTRAINT `fk_report_feedback_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_02` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_04` FOREIGN KEY (`period_id`, `faculty_id`)
  REFERENCES `registration_periods` (`id`, `faculty_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_committees` ADD CONSTRAINT `fk_evaluation_committees_rubric` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `committee_members` ADD CONSTRAINT `fk_committee_members_01` FOREIGN KEY (`committee_id`)
  REFERENCES `evaluation_committees` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `committee_members` ADD CONSTRAINT `fk_committee_members_02` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_02` FOREIGN KEY (`committee_id`)
  REFERENCES `evaluation_committees` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_03` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_04` FOREIGN KEY (`assignment_id`, `period_id`)
  REFERENCES `project_assignments` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `defense_sessions` ADD CONSTRAINT `fk_defense_sessions_05` FOREIGN KEY (`committee_id`, `period_id`)
  REFERENCES `evaluation_committees` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `assessment_components` ADD CONSTRAINT `fk_assessment_components_01` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `rubrics` ADD CONSTRAINT `fk_rubrics_01` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `rubric_criteria` ADD CONSTRAINT `fk_rubric_criteria_01` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_02` FOREIGN KEY (`period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_03` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_04` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_05` FOREIGN KEY (`lecturer_id`)
  REFERENCES `lecturers` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_06` FOREIGN KEY (`defense_id`)
  REFERENCES `defense_sessions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_07` FOREIGN KEY (`assignment_id`, `period_id`)
  REFERENCES `project_assignments` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_08` FOREIGN KEY (`component_id`, `period_id`)
  REFERENCES `assessment_components` (`id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_09` FOREIGN KEY (`rubric_id`, `component_id`)
  REFERENCES `rubrics` (`id`, `component_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `evaluation_assignments` ADD CONSTRAINT `fk_evaluation_assignments_10` FOREIGN KEY (`defense_id`, `assignment_id`, `period_id`)
  REFERENCES `defense_sessions` (`id`, `assignment_id`, `period_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_01` FOREIGN KEY (`evaluation_id`)
  REFERENCES `evaluation_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_02` FOREIGN KEY (`rubric_id`)
  REFERENCES `rubrics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_sheets` ADD CONSTRAINT `fk_score_sheets_03` FOREIGN KEY (`evaluation_id`, `rubric_id`)
  REFERENCES `evaluation_assignments` (`id`, `rubric_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_01` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_02` FOREIGN KEY (`criterion_id`)
  REFERENCES `rubric_criteria` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_03` FOREIGN KEY (`sheet_id`, `rubric_id`)
  REFERENCES `score_sheets` (`id`, `rubric_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `score_details` ADD CONSTRAINT `fk_score_details_04` FOREIGN KEY (`criterion_id`, `rubric_id`, `max_score`)
  REFERENCES `rubric_criteria` (`id`, `rubric_id`, `max_score`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_01` FOREIGN KEY (`assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_02` FOREIGN KEY (`finalized_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_versions` ADD CONSTRAINT `fk_result_versions_03` FOREIGN KEY (`published_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_01` FOREIGN KEY (`result_id`)
  REFERENCES `result_versions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_02` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_inputs` ADD CONSTRAINT `fk_result_inputs_03` FOREIGN KEY (`component_id`)
  REFERENCES `assessment_components` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_members` ADD CONSTRAINT `fk_result_members_01` FOREIGN KEY (`result_id`)
  REFERENCES `result_versions` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `result_members` ADD CONSTRAINT `fk_result_members_02` FOREIGN KEY (`student_id`)
  REFERENCES `students` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_01` FOREIGN KEY (`sheet_id`)
  REFERENCES `score_sheets` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_02` FOREIGN KEY (`requested_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `grade_change_requests` ADD CONSTRAINT `fk_grade_change_requests_03` FOREIGN KEY (`reviewed_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_01` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_02` FOREIGN KEY (`file_id`)
  REFERENCES `stored_files` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `announcements` ADD CONSTRAINT `fk_announcements_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_01` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `audit_logs` ADD CONSTRAINT `fk_audit_logs_01` FOREIGN KEY (`actor_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
-- BEGIN EMAIL NOTIFICATION MIGRATION: foreign keys for an existing deployment
ALTER TABLE `email_notification_settings` ADD CONSTRAINT `fk_email_notification_settings_01` FOREIGN KEY (`updated_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_01` FOREIGN KEY (`recipient_user_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_02` FOREIGN KEY (`faculty_id`)
  REFERENCES `faculties` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_03` FOREIGN KEY (`created_by`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_04` FOREIGN KEY (`related_period_id`)
  REFERENCES `registration_periods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_05` FOREIGN KEY (`related_group_id`)
  REFERENCES `student_groups` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_06` FOREIGN KEY (`related_topic_id`)
  REFERENCES `topics` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_07` FOREIGN KEY (`related_assignment_id`)
  REFERENCES `project_assignments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_outbox` ADD CONSTRAINT `fk_email_outbox_08` FOREIGN KEY (`related_milestone_id`)
  REFERENCES `milestones` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE `email_delivery_logs` ADD CONSTRAINT `fk_email_delivery_logs_01` FOREIGN KEY (`outbox_id`)
  REFERENCES `email_outbox` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
-- END EMAIL NOTIFICATION MIGRATION: foreign keys

-- Foreign key declared separately so every referenced table already exists.
ALTER TABLE `email_templates` ADD CONSTRAINT `fk_email_templates_owner` FOREIGN KEY (`owner_id`)
  REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
