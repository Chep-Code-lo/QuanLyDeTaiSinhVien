package com.example.qldetai.feature.identity;

import com.example.qldetai.feature.identity.IdentityDtos.AppointmentResponse;
import com.example.qldetai.feature.identity.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.IdentityDtos.LecturerDirectoryResponse;
import com.example.qldetai.feature.identity.IdentityDtos.LecturerProfileResponse;
import com.example.qldetai.feature.identity.IdentityDtos.StudentProfileResponse;
import com.example.qldetai.feature.identity.IdentityDtos.UserResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class IdentityMapper {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final Clock clock;

    public IdentityMapper(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            AcademicAppointmentRepository academicAppointmentRepository,
            Clock clock) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.clock = clock;
    }

    public CurrentUserResponse toCurrentUser(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                roleCodes(user),
                studentRepository.findByUserId(user.getId()).map(this::toStudent).orElse(null),
                lecturerRepository.findByUserId(user.getId()).map(this::toLecturer).orElse(null),
                academicAppointmentRepository.findActiveByUserId(user.getId(), LocalDateTime.now(clock)).stream()
                        .map(this::toAppointment)
                        .toList());
    }

    public UserResponse toUser(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roleCodes(user),
                studentRepository.findByUserId(user.getId()).map(this::toStudent).orElse(null),
                lecturerRepository.findByUserId(user.getId()).map(this::toLecturer).orElse(null),
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
