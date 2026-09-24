package com.example.qldetai.feature.identity.mapper;

import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.dto.IdentityDtos.AppointmentResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerDirectoryResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerProfileResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.StudentProfileResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IdentityMapper {

    public CurrentUserResponse toCurrentUser(
            User user, Student student, Lecturer lecturer, List<AcademicAppointment> appointments) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                roleCodes(user),
                student == null ? null : toStudent(student),
                lecturer == null ? null : toLecturer(lecturer),
                appointments.stream().map(this::toAppointment).toList());
    }

    public UserResponse toUser(User user, Student student, Lecturer lecturer) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roleCodes(user),
                student == null ? null : toStudent(student),
                lecturer == null ? null : toLecturer(lecturer),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getVersion());
    }

    public AppointmentResponse toAppointment(AcademicAppointment appointment) {
        Lecturer lecturer = appointment.getLecturer();
        return new AppointmentResponse(
                appointment.getId(),
                lecturer.getId(),
                lecturer.getLecturerCode(),
                lecturer.getUser().getFullName(),
                appointment.getFaculty().getId(),
                appointment.getDepartment() == null ? null : appointment.getDepartment().getId(),
                appointment.getPositionCode(),
                appointment.getStartsAt(),
                appointment.getEndsAt());
    }

    public LecturerDirectoryResponse toLecturerDirectory(Lecturer lecturer) {
        return new LecturerDirectoryResponse(
                lecturer.getId(),
                lecturer.getUser().getId(),
                lecturer.getLecturerCode(),
                lecturer.getUser().getFullName(),
                lecturer.getAcademicDegree(),
                lecturer.getSpecialization(),
                lecturer.getDepartment().getFaculty().getId(),
                lecturer.getDepartment().getFaculty().getName(),
                lecturer.getDepartment().getId(),
                lecturer.getDepartment().getName());
    }

    private Set<RoleCode> roleCodes(User user) {
        return user.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getCode())
                .collect(Collectors.toUnmodifiableSet());
    }

    private StudentProfileResponse toStudent(Student student) {
        return new StudentProfileResponse(
                student.getId(),
                student.getStudentCode(),
                student.getClassName(),
                student.getCohort(),
                student.getDepartment().getId());
    }

    private LecturerProfileResponse toLecturer(Lecturer lecturer) {
        return new LecturerProfileResponse(
                lecturer.getId(),
                lecturer.getLecturerCode(),
                lecturer.getAcademicDegree(),
                lecturer.getSpecialization(),
                lecturer.getDepartment().getId());
    }
}
