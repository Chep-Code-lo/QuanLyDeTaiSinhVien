package com.example.qldetai.feature.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogDeletionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @Autowired StudentRepository students;
    @Autowired UserRepository users;

    private Faculty faculty() {
        var faculty = new Faculty();
        faculty.setCode(UUID.randomUUID().toString().substring(0, 15));
        faculty.setName("Unused faculty");
        return faculties.saveAndFlush(faculty);
    }

    private Department department(Faculty faculty) {
        var department = new Department();
        department.setFaculty(faculty);
        department.setCode(UUID.randomUUID().toString().substring(0, 15));
        department.setName("Unused department");
        return departments.saveAndFlush(department);
    }

    @Test
    void deletingUnusedFacultyRemovesItAndRetryReturnsNotFound() throws Exception {
        var faculty = faculty();
        String url = "/api/v1/faculties/" + faculty.getId();
        mvc.perform(delete(url).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(faculties.existsById(faculty.getId())).isFalse();
        mvc.perform(delete(url).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingUnusedDepartmentPreservesParentFaculty() throws Exception {
        var faculty = faculty();
        var department = department(faculty);
        mvc.perform(delete("/api/v1/departments/" + department.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(departments.existsById(department.getId())).isFalse();
        assertThat(faculties.existsById(faculty.getId())).isTrue();
    }

    @Test
    void deletingFacultyWithDepartmentReturnsConflictWithoutDeletingEither() throws Exception {
        var faculty = faculty();
        var department = department(faculty);
        mvc.perform(delete("/api/v1/faculties/" + faculty.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(faculties.existsById(faculty.getId())).isTrue();
        assertThat(departments.existsById(department.getId())).isTrue();
    }

    @Test
    void deletingDepartmentUsedByStudentReturnsConflictAndPreservesStudent() throws Exception {
        var department = department(faculty());
        var account = new User();
        account.setEmail(UUID.randomUUID() + "@catalog.test");
        account.setFullName("Catalog student");
        account.setPasswordHash("unused-test-hash");
        account.setStatus(UserStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        var student = new Student();
        student.setUser(users.saveAndFlush(account));
        student.setDepartment(department);
        student.setStudentCode(UUID.randomUUID().toString().substring(0, 15));
        student.setClassName("Test class");
        student.setCohort("2026");
        student = students.saveAndFlush(student);
        mvc.perform(delete("/api/v1/departments/" + department.getId())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(departments.existsById(department.getId())).isTrue();
        assertThat(students.existsById(student.getId())).isTrue();
    }

    @Test
    void lecturerWithoutManagementScopeCannotDeleteCatalogs() throws Exception {
        var faculty = faculty();
        var department = department(faculty);
        for (String url : new String[] {"/api/v1/faculties/" + faculty.getId(),
                "/api/v1/departments/" + department.getId()}) {
            mvc.perform(delete(url).with(user("lecturer").roles("LECTURER")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(faculties.existsById(faculty.getId())).isTrue();
        assertThat(departments.existsById(department.getId())).isTrue();
    }

    @Test
    void deleteWithoutCsrfIsRejected() throws Exception {
        var faculty = faculty();
        mvc.perform(delete("/api/v1/faculties/" + faculty.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertThat(faculties.existsById(faculty.getId())).isTrue();
    }
}
