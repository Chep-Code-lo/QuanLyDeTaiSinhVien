package com.example.qldetai.feature.academic;

import com.example.qldetai.common.api.ApiResponse;
import com.example.qldetai.feature.academic.AcademicDtos.CreateDepartmentRequest;
import com.example.qldetai.feature.academic.AcademicDtos.CreateFacultyRequest;
import com.example.qldetai.feature.academic.AcademicDtos.CreateSemesterRequest;
import com.example.qldetai.feature.academic.AcademicDtos.DepartmentResponse;
import com.example.qldetai.feature.academic.AcademicDtos.FacultyResponse;
import com.example.qldetai.feature.academic.AcademicDtos.SemesterResponse;
import com.example.qldetai.feature.academic.AcademicDtos.UpdateCatalogRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AcademicController {

    private final AcademicCatalogService academicCatalogService;

    public AcademicController(AcademicCatalogService academicCatalogService) {
        this.academicCatalogService = academicCatalogService;
    }

    @GetMapping("/faculties")
    public ApiResponse<List<FacultyResponse>> faculties() {
        return ApiResponse.success(academicCatalogService.faculties());
    }

    @PostMapping("/faculties")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FacultyResponse>> createFaculty(
            @Valid @RequestBody CreateFacultyRequest request) {
        FacultyResponse created = academicCatalogService.createFaculty(request);
        return ResponseEntity.created(URI.create("/api/v1/faculties/" + created.id()))
                .body(ApiResponse.success("Faculty created.", created));
    }

    @PatchMapping("/faculties/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FacultyResponse> updateFaculty(
            @PathVariable Long id, @Valid @RequestBody UpdateCatalogRequest request) {
        return ApiResponse.success("Faculty updated.", academicCatalogService.updateFaculty(id, request));
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentResponse>> departments(@RequestParam(required = false) Long facultyId) {
        return ApiResponse.success(academicCatalogService.departments(facultyId));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageFaculty(#request.facultyId, authentication)")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse created = academicCatalogService.createDepartment(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id()))
                .body(ApiResponse.success("Department created.", created));
    }

    @PatchMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN') or @academicAuthorization.canManageDepartment(#id, authentication)")
    public ApiResponse<DepartmentResponse> updateDepartment(
            @PathVariable Long id, @Valid @RequestBody UpdateCatalogRequest request) {
        return ApiResponse.success("Department updated.", academicCatalogService.updateDepartment(id, request));
    }

    @GetMapping("/semesters")
    public ApiResponse<List<SemesterResponse>> semesters() {
        return ApiResponse.success(academicCatalogService.semesters());
    }

    @PostMapping("/semesters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterResponse>> createSemester(
            @Valid @RequestBody CreateSemesterRequest request) {
        SemesterResponse created = academicCatalogService.createSemester(request);
        return ResponseEntity.created(URI.create("/api/v1/semesters/" + created.id()))
                .body(ApiResponse.success("Semester created.", created));
    }
}
