package com.example.qldetai.feature.academic.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateDepartmentRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateFacultyRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateSemesterRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.DepartmentResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.FacultyResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.SemesterResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.UpdateCatalogRequest;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicCatalogService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;

    public AcademicCatalogService(
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            SemesterRepository semesterRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
    }

    @Transactional(readOnly = true)
    public List<FacultyResponse> faculties() {
        return facultyRepository.findAllByOrderByNameAsc().stream().map(this::toFaculty).toList();
    }

    @Transactional
    @BusinessAudit(action = "FACULTY_CREATE", entityType = "FACULTY")
    public FacultyResponse createFaculty(CreateFacultyRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (facultyRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Faculty code is already in use.");
        }
        Faculty faculty = new Faculty();
        faculty.setCode(code);
        faculty.setName(request.name().trim());
        faculty.setActive(true);
        return toFaculty(facultyRepository.save(faculty));
    }

    @Transactional
    @BusinessAudit(action = "FACULTY_UPDATE", entityType = "FACULTY", entityIdArgument = 0)
    public FacultyResponse updateFaculty(Long id, UpdateCatalogRequest request) {
        Faculty faculty = requireFaculty(id);
        faculty.setName(request.name().trim());
        faculty.setActive(request.active());
        return toFaculty(faculty);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments(Long facultyId) {
        List<Department> departments = facultyId == null
                ? departmentRepository.findAllByOrderByNameAsc()
                : departmentRepository.findByFacultyIdOrderByNameAsc(facultyId);
        return departments.stream().map(this::toDepartment).toList();
    }

    @Transactional
    @BusinessAudit(action = "DEPARTMENT_CREATE", entityType = "DEPARTMENT")
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        Faculty faculty = requireFaculty(request.facultyId());
        if (!faculty.isActive()) {
            throw new BusinessRuleException("Cannot add a department to an inactive faculty.");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (departmentRepository.existsByFacultyIdAndCodeIgnoreCase(faculty.getId(), code)) {
            throw new ConflictException("Department code is already in use in this faculty.");
        }
        Department department = new Department();
        department.setFaculty(faculty);
        department.setCode(code);
        department.setName(request.name().trim());
        department.setActive(true);
        return toDepartment(departmentRepository.save(department));
    }

    @Transactional
    @BusinessAudit(action = "DEPARTMENT_UPDATE", entityType = "DEPARTMENT", entityIdArgument = 0)
    public DepartmentResponse updateDepartment(Long id, UpdateCatalogRequest request) {
        Department department = requireDepartment(id);
        department.setName(request.name().trim());
        department.setActive(request.active());
        return toDepartment(department);
    }

    @Transactional(readOnly = true)
    public List<SemesterResponse> semesters() {
        return semesterRepository.findAllByOrderByStartsOnDesc().stream().map(this::toSemester).toList();
    }

    @Transactional
    @BusinessAudit(action = "SEMESTER_CREATE", entityType = "SEMESTER")
    public SemesterResponse createSemester(CreateSemesterRequest request) {
        if (!request.startsOn().isBefore(request.endsOn())) {
            throw new BusinessRuleException("Semester start date must be before its end date.");
        }
        String academicYear = request.academicYear().trim();
        if (semesterRepository.existsByAcademicYearIgnoreCaseAndSemesterNo(academicYear, request.semesterNo())) {
            throw new ConflictException("This semester already exists for the academic year.");
        }
        Semester semester = new Semester();
        semester.setAcademicYear(academicYear);
        semester.setSemesterNo(request.semesterNo());
        semester.setStartsOn(request.startsOn());
        semester.setEndsOn(request.endsOn());
        return toSemester(semesterRepository.save(semester));
    }

    private Faculty requireFaculty(Long id) {
        return facultyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty was not found."));
    }

    private Department requireDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department was not found."));
    }

    private FacultyResponse toFaculty(Faculty faculty) {
        return new FacultyResponse(faculty.getId(), faculty.getCode(), faculty.getName(), faculty.isActive());
    }

    private DepartmentResponse toDepartment(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getFaculty().getId(),
                department.getFaculty().getName(),
                department.getCode(),
                department.getName(),
                department.isActive());
    }

    private SemesterResponse toSemester(Semester semester) {
        return new SemesterResponse(
                semester.getId(),
                semester.getAcademicYear(),
                semester.getSemesterNo(),
                semester.getStartsOn(),
                semester.getEndsOn());
    }
}
