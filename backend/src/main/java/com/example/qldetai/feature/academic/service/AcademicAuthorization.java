package com.example.qldetai.feature.academic.service;

import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.security.UserPrincipal;

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

    public AcademicAuthorization(
            AcademicAppointmentRepository academicAppointmentRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            Clock clock) {
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.departmentRepository = departmentRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.clock = clock;
    }

    public boolean canManageFaculty(Long facultyId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        return principal != null
                && academicAppointmentRepository.hasActiveFacultyAppointment(
                        principal.id(), PositionCode.DEAN, facultyId, LocalDateTime.now(clock));
    }

    public boolean canManageDepartment(Long departmentId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (academicAppointmentRepository.hasActiveDepartmentAppointment(
                principal.id(), PositionCode.DEPARTMENT_HEAD, departmentId, now)) {
            return true;
        }
        return departmentRepository
                .findById(departmentId)
                .map(
                        department ->
                                academicAppointmentRepository.hasActiveFacultyAppointment(
                                        principal.id(),
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

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null
                        && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal
                : null;
    }
}
