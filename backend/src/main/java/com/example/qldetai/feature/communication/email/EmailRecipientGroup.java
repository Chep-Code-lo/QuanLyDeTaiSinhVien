package com.example.qldetai.feature.communication.email;

/** Recipient sets are resolved only from existing, authorized database relations. */
public enum EmailRecipientGroup {
    DIRECT_PARTICIPANTS,
    FACULTY_STUDENTS,
    FACULTY_LECTURERS,
    GROUP_STUDENTS,
    TOPIC_STUDENTS,
    SUPERVISORS,
    ADMINS
}
