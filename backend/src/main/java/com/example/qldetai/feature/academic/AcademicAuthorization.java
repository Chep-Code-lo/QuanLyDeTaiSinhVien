package com.example.qldetai.feature.academic;

import com.example.qldetai.feature.identity.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.PositionCode;
import com.example.qldetai.security.UserPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("academicAuthorization")
public class AcademicAuthorization {

    private final AcademicAppointmentRepository appointmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationPeriodRepository periodRepository;
    private final Clock clock;

    @Autowired
    public AcademicAuthorization(
            AcademicAppointmentRepository appointmentRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository periodRepository) {
        this(appointmentRepository, departmentRepository, periodRepository, Clock.systemUTC());
    }

    AcademicAuthorization(
            AcademicAppointmentRepository appointmentRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository periodRepository,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.departmentRepository = departmentRepository;
        this.periodRepository = periodRepository;
        this.clock = clock;
    }

    public boolean canManageFaculty(Long facultyId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        return principal != null && appointmentRepository.hasActiveFacultyAppointment(
                principal.id(), PositionCode.DEAN, facultyId, LocalDateTime.now(clock));
    }

    public boolean canManageDepartment(Long departmentId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (appointmentRepository.hasActiveDepartmentAppointment(
                principal.id(), PositionCode.DEPARTMENT_HEAD, departmentId, now)) {
            return true;
        }
        return departmentRepository.findById(departmentId)
                .map(department -> appointmentRepository.hasActiveFacultyAppointment(
                        principal.id(), PositionCode.DEAN, department.getFaculty().getId(), now))
                .orElse(false);
    }

    public boolean canManagePeriod(Long periodId, Authentication authentication) {
        return periodRepository.findById(periodId)
                .map(period -> canManageFaculty(period.getFaculty().getId(), authentication))
                .orElse(false);
    }

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal
                : null;
    }
}
