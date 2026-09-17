package com.example.qldetai.feature.identity;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.Department;
import com.example.qldetai.feature.academic.DepartmentRepository;
import com.example.qldetai.feature.academic.Faculty;
import com.example.qldetai.feature.academic.FacultyRepository;
import com.example.qldetai.feature.identity.IdentityDtos.AppointmentResponse;
import com.example.qldetai.feature.identity.IdentityDtos.CreateAppointmentRequest;
import com.example.qldetai.feature.identity.IdentityDtos.EndAppointmentRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicAppointmentService {

    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final LecturerRepository lecturerRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final IdentityMapper identityMapper;

    public AcademicAppointmentService(
            AcademicAppointmentRepository academicAppointmentRepository,
            LecturerRepository lecturerRepository,
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            IdentityMapper identityMapper) {
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.lecturerRepository = lecturerRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.identityMapper = identityMapper;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Long lecturerId) {
        return academicAppointmentRepository.findForAdministration(lecturerId).stream()
                .map(identityMapper::toAppointment)
                .toList();
    }

    @Transactional
    @BusinessAudit(action = "ACADEMIC_APPOINTMENT_CREATE", entityType = "ACADEMIC_APPOINTMENT")
    public AppointmentResponse create(CreateAppointmentRequest request, Long assignedById) {
        Lecturer lecturer = lecturerRepository.findById(request.lecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer profile was not found."));
        Faculty faculty = facultyRepository.findById(request.facultyId())
                .orElseThrow(() -> new ResourceNotFoundException("Faculty was not found."));
        Department department = request.departmentId() == null ? null : departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found."));

        validate(request, faculty, department, lecturer);

        AcademicAppointment appointment = new AcademicAppointment();
        appointment.setLecturer(lecturer);
        appointment.setFaculty(faculty);
        appointment.setDepartment(department);
        appointment.setPositionCode(request.positionCode());
        appointment.setStartsAt(request.startsAt());
        appointment.setEndsAt(request.endsAt());
        appointment.setAssignedBy(userRepository.findById(assignedById)
                .orElseThrow(() -> new ResourceNotFoundException("Assigning user was not found.")));
        return identityMapper.toAppointment(academicAppointmentRepository.save(appointment));
    }

    @Transactional
    @BusinessAudit(action = "ACADEMIC_APPOINTMENT_END", entityType = "ACADEMIC_APPOINTMENT", entityIdArgument = 0)
    public AppointmentResponse end(Long id, EndAppointmentRequest request) {
        AcademicAppointment appointment = academicAppointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Academic appointment was not found."));
        if (!request.endsAt().isAfter(appointment.getStartsAt())) {
            throw new BusinessRuleException("The appointment end must be after its start.");
        }
        appointment.setEndsAt(request.endsAt());
        return identityMapper.toAppointment(appointment);
    }

    private void validate(
            CreateAppointmentRequest request,
            Faculty faculty,
            Department department,
            Lecturer lecturer) {
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("The appointment end must be after its start.");
        }
        if (!lecturer.getDepartment().getFaculty().getId().equals(faculty.getId())) {
            throw new BusinessRuleException("The lecturer does not belong to the selected faculty.");
        }
        if (request.positionCode() == PositionCode.DEAN && department != null) {
            throw new BusinessRuleException("A dean appointment must not specify a department.");
        }
        if (request.positionCode() == PositionCode.DEPARTMENT_HEAD) {
            if (department == null) {
                throw new BusinessRuleException("A department head appointment requires a department.");
            }
            if (!department.getFaculty().getId().equals(faculty.getId())) {
                throw new BusinessRuleException("The department does not belong to the selected faculty.");
            }
            if (!lecturer.getDepartment().getId().equals(department.getId())) {
                throw new BusinessRuleException("A department head must belong to that department.");
            }
        }
    }
}
