package com.example.qldetai.feature.academic.service;

import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.security.UserPrincipal;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component("academicAuthorization")
@Transactional(readOnly = true)
public class AcademicAuthorization {

    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final Clock clock;
    private final UserRepository users;

    public AcademicAuthorization(
            AcademicAppointmentRepository academicAppointmentRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            Clock clock, UserRepository users) {
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.departmentRepository = departmentRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.clock = clock;
        this.users = users;
    }

    public boolean canManageFaculty(Long facultyId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        return principal != null && canManageFaculty(facultyId, principal.id());
    }

    public boolean canManageFaculty(Long facultyId, Long userId) {
        return facultyId != null
                && userId != null
                && lecturer(userId)
                && academicAppointmentRepository.hasActiveFacultyAppointment(
                        userId, PositionCode.DEAN, facultyId, LocalDateTime.now(clock));
    }

    public boolean canManageDepartment(Long departmentId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        return principal != null && canManageDepartment(departmentId, principal.id());
    }

    public boolean canManageDepartment(Long departmentId, Long userId) {
        if (departmentId == null || userId == null || !lecturer(userId)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (academicAppointmentRepository.hasActiveDepartmentAppointment(
                userId, PositionCode.DEPARTMENT_HEAD, departmentId, now)) {
            return true;
        }
        return departmentRepository
                .findById(departmentId)
                .map(
                        department ->
                                academicAppointmentRepository.hasActiveFacultyAppointment(
                                        userId,
                                        PositionCode.DEAN,
                                        department.getFaculty().getId(),
                                        now))
                .orElse(false);
    }

    public boolean canManagePeriod(Long periodId, Authentication authentication) {
        return registrationPeriodRepository
                .findById(periodId)
                .map(period -> canManageFaculty(period.getFaculty().getId(), authentication))
                .orElse(false);
    }

    public boolean canManagePeriod(Long periodId, Long userId) {
        return registrationPeriodRepository.findById(periodId)
                .map(period -> canManageFaculty(period.getFaculty().getId(), userId)).orElse(false);
    }

    public boolean lecturer(Long userId) {
        return users.findWithRolesById(userId).map(u -> u.getUserRoles().stream()
                .anyMatch(r -> r.getRole().getCode() == RoleCode.LECTURER)).orElse(false);
    }

    public void requireDepartmentManagement(Long departmentId, Long userId) {
        if (!canManageDepartment(departmentId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("An active academic management appointment in this department is required.");
        }
    }

    public void requirePeriodManagement(Long periodId, Long userId) {
        if (!canManagePeriod(periodId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("An active dean appointment in this faculty is required.");
        }
    }

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null
                        && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal
                : null;
    }
}
