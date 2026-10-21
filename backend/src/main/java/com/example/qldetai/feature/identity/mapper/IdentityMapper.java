package com.example.qldetai.feature.identity.mapper;

import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserRole;
import com.example.qldetai.feature.identity.dto.IdentityDtos.AppointmentResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CurrentUserResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerDirectoryResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.LecturerProfileResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.StudentProfileResponse;
import com.example.qldetai.feature.identity.dto.IdentityDtos.UserResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IdentityMapper {

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "username", source = "user.email")
    @Mapping(target = "roles", source = "user.userRoles", qualifiedByName = "roleCodes")
    @Mapping(target = "student", source = "student")
    @Mapping(target = "lecturer", source = "lecturer")
    @Mapping(target = "activeAppointments", source = "appointments")
    CurrentUserResponse toCurrentUser(
            User user, Student student, Lecturer lecturer, List<AcademicAppointment> appointments);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "roles", source = "user.userRoles", qualifiedByName = "roleCodes")
    @Mapping(target = "student", source = "student")
    @Mapping(target = "lecturer", source = "lecturer")
    @Mapping(target = "lastLoginAt", source = "user.lastLoginAt")
    @Mapping(target = "lastLoginIp", source = "user.lastLoginIp")
    @Mapping(target = "createdAt", source = "user.createdAt")
    @Mapping(target = "version", source = "user.version")
    UserResponse toUser(User user, Student student, Lecturer lecturer);

    @Mapping(target = "lecturerId", source = "lecturer.id")
    @Mapping(target = "lecturerCode", source = "lecturer.lecturerCode")
    @Mapping(target = "lecturerName", source = "lecturer.user.fullName")
    @Mapping(target = "facultyId", source = "faculty.id")
    @Mapping(target = "departmentId", source = "department.id")
    AppointmentResponse toAppointment(AcademicAppointment appointment);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "facultyId", source = "department.faculty.id")
    @Mapping(target = "facultyName", source = "department.faculty.name")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    LecturerDirectoryResponse toLecturerDirectory(Lecturer lecturer);

    @Mapping(target = "departmentId", source = "department.id")
    StudentProfileResponse toStudent(Student student);

    @Mapping(target = "departmentId", source = "department.id")
    LecturerProfileResponse toLecturer(Lecturer lecturer);

    @Named("roleCodes")
    default Set<RoleCode> toRoleCodes(Set<UserRole> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return Set.of();
        }

        Set<RoleCode> roleCodes = new LinkedHashSet<>();
        for (UserRole userRole : userRoles) {
            if (userRole != null && userRole.getRole() != null) {
                roleCodes.add(userRole.getRole().getCode());
            }
        }
        return Set.copyOf(roleCodes);
    }
}
