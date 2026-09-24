package com.example.qldetai.feature.identity.service;

import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;
import com.example.qldetai.feature.identity.mapper.IdentityMapper;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads profile data before passing it to the query-free identity mapper. */
@Service
@Transactional(readOnly = true)
public class UserProfileService {
    private final StudentRepository students;
    private final LecturerRepository lecturers;
    private final AcademicAppointmentRepository appointments;
    private final IdentityMapper mapper;
    private final Clock clock;

    public UserProfileService(
            StudentRepository students,
            LecturerRepository lecturers,
            AcademicAppointmentRepository appointments,
            IdentityMapper mapper,
            Clock clock) {
        this.students = students;
        this.lecturers = lecturers;
        this.appointments = appointments;
        this.mapper = mapper;
        this.clock = clock;
    }

    public CurrentUserResponse toCurrentUser(User user) {
        return mapper.toCurrentUser(
                user,
                students.findByUserId(user.getId()).orElse(null),
                lecturers.findByUserId(user.getId()).orElse(null),
                appointments.findActiveByUserId(user.getId(), LocalDateTime.now(clock)));
    }

    public UserResponse toUser(User user) {
        return mapper.toUser(
                user,
                students.findByUserId(user.getId()).orElse(null),
                lecturers.findByUserId(user.getId()).orElse(null));
    }

    public Page<UserResponse> toUsers(Page<User> users) {
        if (users.isEmpty()) {
            return users.map(user -> mapper.toUser(user, null, null));
        }
        List<Long> ids = users.getContent().stream().map(User::getId).toList();
        Map<Long, Student> studentsByUser =
                students.findByUserIdIn(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        student -> student.getUser().getId(), Function.identity()));
        Map<Long, Lecturer> lecturersByUser =
                lecturers.findByUserIdIn(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        lecturer -> lecturer.getUser().getId(),
                                        Function.identity()));
        return users.map(
                user ->
                        mapper.toUser(
                                user,
                                studentsByUser.get(user.getId()),
                                lecturersByUser.get(user.getId())));
    }
}
