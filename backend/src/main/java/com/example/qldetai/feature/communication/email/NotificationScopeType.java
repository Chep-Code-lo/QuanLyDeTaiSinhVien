package com.example.qldetai.feature.communication.email;

/**
 * SYSTEM uses scope id 0. FACULTY uses faculties.id and LECTURER uses users.id.
 * Using a non-null scope id keeps the uniqueness constraint portable to MySQL.
 */
public enum NotificationScopeType {
    SYSTEM,
    FACULTY,
    LECTURER
}
