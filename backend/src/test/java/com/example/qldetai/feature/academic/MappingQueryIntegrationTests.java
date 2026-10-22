package com.example.qldetai.feature.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.academic.service.AcademicCatalogService;
import com.example.qldetai.feature.academic.service.RegistrationPeriodService;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:review-mapping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "spring.jpa.properties.hibernate.session.events.log=false"
        })
@Transactional
class MappingQueryIntegrationTests {
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @Autowired SemesterRepository semesters;
    @Autowired RegistrationPeriodRepository periods;
    @Autowired UserRepository users;
    @Autowired AcademicCatalogService catalog;
    @Autowired RegistrationPeriodService service;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory factory;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        String url = System.getProperty("review.mysql.url");
        if (url != null) {
            if (!url.startsWith("jdbc:mysql://127.0.0.1:")
                    || !url.contains("/ql_de_tai_sinh_vien?"))
                throw new IllegalArgumentException("Disposable local database required");
            properties.add("spring.datasource.url", () -> url);
            properties.add(
                    "spring.datasource.username", () -> System.getProperty("review.mysql.user"));
            properties.add(
                    "spring.datasource.password",
                    () -> System.getProperty("review.mysql.password"));
            properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
            properties.add("spring.sql.init.mode", () -> "never");
        }
    }

    private Faculty faculty() {
        var faculty = new Faculty();
        faculty.setCode(UUID.randomUUID().toString().substring(0, 8));
        faculty.setName("Khoa mapping");
        faculty.setActive(true);
        return faculties.saveAndFlush(faculty);
    }

    @Test
    void departmentMappingLoadsFacultyInOneQuery() {
        var expectedIds = new ArrayList<Long>();
        for (int index = 0; index < 4; index++) {
            var department = new Department();
            department.setFaculty(faculty());
            department.setCode("BM" + index);
            department.setName("Bộ môn " + index);
            department.setActive(true);
            expectedIds.add(departments.saveAndFlush(department).getId());
        }
        entityManager.clear();
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var results = catalog.departments(null);
        assertThat(results).extracting(item -> item.id()).containsAll(expectedIds);
        assertThat(results.stream().filter(item -> expectedIds.contains(item.id())))
                .allSatisfy(item -> assertThat(item.facultyName()).isEqualTo("Khoa mapping"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void periodMappingLoadsFacultyAndSemesterWithoutQueriesPerRow() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@mapping.local");
        user.setFullName("Mapper probe");
        user.setPasswordHash("unused-test-verifier");
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        var expectedIds = new ArrayList<Long>();
        for (int index = 0; index < 3; index++) {
            var semester = new Semester();
            semester.setAcademicYear((2046 + index) + "-" + (2047 + index));
            semester.setSemesterNo(1);
            semester.setStartsOn(LocalDate.of(2046 + index, 9, 1));
            semester.setEndsOn(LocalDate.of(2047 + index, 8, 31));
            semesters.saveAndFlush(semester);
            var period = new RegistrationPeriod();
            period.setFaculty(faculty());
            period.setSemester(semester);
            period.setName("Mapping " + UUID.randomUUID());
            period.setCreatedBy(user);
            period.setProjectType(ProjectType.COURSE);
            period.setStatus(RegistrationPeriodStatus.DRAFT);
            var now = LocalDateTime.now(Clock.systemUTC());
            period.setProposalStartAt(now.minusDays(6));
            period.setProposalEndAt(now.minusDays(4));
            period.setRegistrationStartAt(now.minusDays(3));
            period.setRegistrationEndAt(now.plusDays(3));
            expectedIds.add(periods.saveAndFlush(period).getId());
        }
        entityManager.clear();
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var results = service.list(null, 0, 100).content();
        assertThat(results).extracting(item -> item.id()).containsAll(expectedIds);
        assertThat(results.stream().filter(item -> expectedIds.contains(item.id())))
                .allSatisfy(item -> assertThat(item.facultyName()).isEqualTo("Khoa mapping"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
