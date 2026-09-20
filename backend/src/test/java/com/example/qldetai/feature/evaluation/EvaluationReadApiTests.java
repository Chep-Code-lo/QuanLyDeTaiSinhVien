package com.example.qldetai.feature.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qldetai.feature.academic.Department;
import com.example.qldetai.feature.academic.Faculty;
import com.example.qldetai.feature.academic.ProjectType;
import com.example.qldetai.feature.academic.RegistrationPeriod;
import com.example.qldetai.feature.academic.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.Semester;
import com.example.qldetai.feature.groupregistration.GroupMember;
import com.example.qldetai.feature.groupregistration.GroupMemberRole;
import com.example.qldetai.feature.groupregistration.GroupStatus;
import com.example.qldetai.feature.groupregistration.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.StudentGroup;
import com.example.qldetai.feature.groupregistration.TopicRegistration;
import com.example.qldetai.feature.identity.Lecturer;
import com.example.qldetai.feature.identity.Role;
import com.example.qldetai.feature.identity.RoleCode;
import com.example.qldetai.feature.identity.Student;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserStatus;
import com.example.qldetai.feature.project.ProjectAssignment;
import com.example.qldetai.feature.topic.ProposalSource;
import com.example.qldetai.feature.topic.Topic;
import com.example.qldetai.feature.topic.TopicStatus;
import com.example.qldetai.feature.topic.VisibilityType;
import com.example.qldetai.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
class EvaluationReadApiTests {

    private final MockMvc mockMvc;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvaluationReadApiTests(MockMvc mockMvc, EntityManager entityManager, ObjectMapper objectMapper, Clock clock) {
        this.mockMvc = mockMvc;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private LocalDateTime now;
    private Faculty faculty;
    private Department department;
    private Semester semester;
    private User admin;
    private Lecturer lecturer;
    private Student student;
    private RegistrationPeriod period;
    private RegistrationPeriod otherPeriod;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        admin = createUser("Manager", RoleCode.ADMIN);
        faculty = new Faculty();
        faculty.setCode(unique("F"));
        faculty.setName("Evaluation test faculty");
        faculty.setActive(true);
        persist(faculty);

        department = new Department();
        department.setFaculty(faculty);
        department.setCode(unique("D"));
        department.setName("Software engineering");
        department.setActive(true);
        persist(department);

        semester = new Semester();
        semester.setAcademicYear("2026-2027");
        semester.setSemesterNo(1);
        semester.setStartsOn(now.toLocalDate().minusMonths(1));
        semester.setEndsOn(now.toLocalDate().plusMonths(4));
        persist(semester);

        lecturer = createLecturer("Evaluator");
        student = createStudent("Group leader");
        period = createPeriod("Current period");
        otherPeriod = createPeriod("Other period");
    }

    @Test
    void catalogsFilterByParentAndIncludeOrderedCriteria() throws Exception {
        AssessmentComponent component = createComponent(period, "REVIEW", AssessmentComponentType.REVIEW);
        Rubric first = createRubric(component, 1);
        Rubric latest = createRubric(component, 2);
        RubricCriterion second = createCriterion(latest, "Presentation", 2, "4.00");
        RubricCriterion firstCriterion = createCriterion(latest, "Implementation", 1, "6.00");
        createRubric(createComponent(otherPeriod, "OTHER", AssessmentComponentType.REVIEW), 1);

        mockMvc.perform(get("/api/v1/assessment-components").with(as(student.getUser()))
                        .param("periodId", period.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(component.getId()))
                .andExpect(jsonPath("$.data[0].periodName").value(period.getName()))
                .andExpect(jsonPath("$.data[0].componentType").value("REVIEW"));

        mockMvc.perform(get("/api/v1/rubrics").with(as(lecturer.getUser()))
                        .param("componentId", component.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(latest.getId()))
                .andExpect(jsonPath("$.data[1].id").value(first.getId()))
                .andExpect(jsonPath("$.data[0].componentCode").value(component.getCode()))
                .andExpect(jsonPath("$.data[0].criteria[0].id").value(firstCriterion.getId()))
                .andExpect(jsonPath("$.data[0].criteria[0].maxScore").value(6.0))
                .andExpect(jsonPath("$.data[0].criteria[1].id").value(second.getId()));
    }

    @Test
    void managerCanCreateAndLockCatalogThroughApi() throws Exception {
        MvcResult createdComponent = mockMvc.perform(post("/api/v1/assessment-components")
                        .with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodId":%d,"code":" review ","name":"Review assessment",
                                 "componentType":"REVIEW","expectedEvaluators":1}
                                """.formatted(period.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("REVIEW"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        long componentId = responseId(createdComponent);

        mockMvc.perform(patch("/api/v1/assessment-components/{id}/lock", componentId)
                        .with(as(admin)).with(csrf()))
                .andExpect(status().isBadRequest());

        MvcResult createdRubric = mockMvc.perform(post("/api/v1/rubrics")
                        .with(as(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"componentId":%d,"name":"Review rubric","criteria":[
                                  {"name":"Code","description":"Working application","maxScore":6,"sortOrder":1},
                                  {"name":"Report","description":"Clear explanation","maxScore":4,"sortOrder":2}
                                ]}
                                """.formatted(componentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.criteria.length()").value(2))
                .andReturn();
        long rubricId = responseId(createdRubric);

        mockMvc.perform(patch("/api/v1/rubrics/{id}/lock", rubricId).with(as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
        mockMvc.perform(patch("/api/v1/assessment-components/{id}/lock", componentId)
                        .with(as(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        entityManager.flush();
        assertThat(entityManager.find(AssessmentComponent.class, componentId).getStatus()).isEqualTo("LOCKED");
        assertThat(entityManager.find(Rubric.class, rubricId).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void assignmentListingCombinesFiltersAndSerializesDefenseAndSheet() throws Exception {
        ProjectAssignment assignment = createAssignment(period, student);
        EvaluationCommittee committee = createCommittee(period, "Committee A");
        DefenseSession defense = createDefense(assignment, committee);
        Rubric rubric = createRubric(createComponent(period, "COMMITTEE", AssessmentComponentType.COMMITTEE), 1);
        EvaluationAssignment expected = createEvaluation(assignment, rubric, lecturer, defense);
        ScoreSheet sheet = createSheet(expected);

        EvaluationAssignment cancelled = createEvaluation(assignment, rubric, createLecturer("Cancelled"), defense);
        cancelled.setStatus(EvaluationStatus.CANCELLED);
        createEvaluation(assignment, rubric, createLecturer("Another evaluator"), defense);

        mockMvc.perform(get("/api/v1/evaluation-assignments").with(as(lecturer.getUser()))
                        .param("periodId", period.getId().toString())
                        .param("assignmentId", assignment.getId().toString())
                        .param("lecturerId", lecturer.getId().toString())
                        .param("status", "ACTIVE").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()))
                .andExpect(jsonPath("$.data.content[0].defenseId").value(defense.getId()))
                .andExpect(jsonPath("$.data.content[0].evaluatorRole").value("COMMITTEE_MEMBER"))
                .andExpect(jsonPath("$.data.content[0].topicCode").value(assignment.getTopic().getCode()))
                .andExpect(jsonPath("$.data.content[0].lecturerName").value(lecturer.getUser().getFullName()))
                .andExpect(jsonPath("$.data.content[0].scoreSheetId").value(sheet.getId()))
                .andExpect(jsonPath("$.data.content[0].scoreSheetStatus").value("DRAFT"));

        mockMvc.perform(get("/api/v1/evaluation-assignments").with(as(admin))
                        .param("assignmentId", assignment.getId().toString()).param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(cancelled.getId()));

        mockMvc.perform(get("/api/v1/evaluation-assignments").with(as(admin))
                        .param("periodId", otherPeriod.getId().toString())
                        .param("assignmentId", assignment.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void reviewAssignmentHasNullDefenseAndNoSheetBeforeScoring() throws Exception {
        ProjectAssignment assignment = createAssignment(period, student);
        Rubric rubric = createRubric(createComponent(period, "REVIEW", AssessmentComponentType.REVIEW), 1);
        EvaluationAssignment evaluation = createEvaluation(assignment, rubric, lecturer, null);

        mockMvc.perform(get("/api/v1/evaluation-assignments").with(as(admin))
                        .param("assignmentId", assignment.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(evaluation.getId()))
                .andExpect(jsonPath("$.data.content[0].defenseId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].scoreSheetId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].evaluatorRole").value("REVIEWER"));
    }

    @Test
    void committeeAndDefenseListsRespectFiltersAndExposeUsefulNames() throws Exception {
        EvaluationCommittee committee = createCommittee(period, "Defense committee");
        CommitteeMember secretary = createMember(committee, lecturer, 2, CommitteeRole.SECRETARY);
        CommitteeMember chair = createMember(committee, createLecturer("Chairperson"), 1, CommitteeRole.CHAIR);
        createMember(committee, createLecturer("Member"), 3, CommitteeRole.MEMBER);
        EvaluationCommittee unrelated = createCommittee(otherPeriod, "Other committee");
        DefenseSession expected = createDefense(createAssignment(period, student), committee);
        createDefense(createAssignment(otherPeriod, createStudent("Other leader")), unrelated);

        mockMvc.perform(get("/api/v1/evaluation-committees").with(as(student.getUser()))
                        .param("periodId", period.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(committee.getId()))
                .andExpect(jsonPath("$.data.content[0].facultyName").value(faculty.getName()))
                .andExpect(jsonPath("$.data.content[0].members.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].members[0].id").value(chair.getId()))
                .andExpect(jsonPath("$.data.content[0].members[1].id").value(secretary.getId()));

        mockMvc.perform(get("/api/v1/defense-sessions").with(as(student.getUser()))
                        .param("periodId", period.getId().toString())
                        .param("committeeId", committee.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()))
                .andExpect(jsonPath("$.data.content[0].committeeName").value(committee.getName()))
                .andExpect(jsonPath("$.data.content[0].groupName").value(expected.getAssignment().getGroup().getName()))
                .andExpect(jsonPath("$.data.content[0].location").value("A101"));

        mockMvc.perform(get("/api/v1/defense-sessions").with(as(admin))
                        .param("periodId", otherPeriod.getId().toString())
                        .param("committeeId", committee.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void studentResultListContainsOnlyTheirPublishedResultsEvenWithFilters() throws Exception {
        ProjectAssignment ownAssignment = createAssignment(period, student);
        ResultVersion ownPublished = createResult(ownAssignment, 1, ResultStatus.PUBLISHED);
        createResult(ownAssignment, 2, ResultStatus.FINALIZED);
        ProjectAssignment otherAssignment = createAssignment(period, createStudent("Another group"));
        createResult(otherAssignment, 1, ResultStatus.PUBLISHED);
        createResult(createAssignment(otherPeriod, createStudent("Other period student")), 1, ResultStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/results").with(as(student.getUser())).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ownPublished.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/results").with(as(student.getUser()))
                        .param("assignmentId", otherAssignment.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/results").with(as(student.getUser())).param("status", "FINALIZED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/results").with(as(student.getUser()))
                        .param("periodId", otherPeriod.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void resultDetailsRequirePublishedStatusAndCurrentGroupMembershipForStudents() throws Exception {
        ProjectAssignment ownAssignment = createAssignment(period, student);
        ResultVersion published = createResult(ownAssignment, 1, ResultStatus.PUBLISHED);
        ResultVersion unpublished = createResult(ownAssignment, 2, ResultStatus.FINALIZED);
        Student outsider = createStudent("Outsider");

        mockMvc.perform(get("/api/v1/results/{id}", published.getId()).with(as(student.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(ownAssignment.getGroup().getId()))
                .andExpect(jsonPath("$.data.topicTitle").value(ownAssignment.getTopic().getTitle()))
                .andExpect(jsonPath("$.data.finalScore").value(8.5))
                .andExpect(jsonPath("$.data.publishedByName").value(admin.getFullName()));
        mockMvc.perform(get("/api/v1/results/{id}", published.getId()).with(as(outsider.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/results/{id}", unpublished.getId()).with(as(student.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/results/{id}", unpublished.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINALIZED"));

        GroupMember membership = entityManager.createQuery(
                        "select m from GroupMember m where m.group.id = :groupId and m.student.id = :studentId",
                        GroupMember.class)
                .setParameter("groupId", ownAssignment.getGroup().getId())
                .setParameter("studentId", student.getId()).getSingleResult();
        membership.setLeftAt(now);
        entityManager.flush();
        mockMvc.perform(get("/api/v1/results/{id}", published.getId()).with(as(student.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/results").with(as(student.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void managersCanFilterUnpublishedResultsAndGradeChangeRequests() throws Exception {
        ProjectAssignment assignment = createAssignment(period, student);
        ResultVersion expected = createResult(assignment, 1, ResultStatus.FINALIZED);
        createResult(assignment, 2, ResultStatus.PUBLISHED);
        mockMvc.perform(get("/api/v1/results").with(as(admin))
                        .param("periodId", period.getId().toString())
                        .param("assignmentId", assignment.getId().toString()).param("status", "FINALIZED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(expected.getId()))
                .andExpect(jsonPath("$.data.content[0].finalizedByName").value(admin.getFullName()));

        Rubric rubric = createRubric(createComponent(period, "REVIEW", AssessmentComponentType.REVIEW), 1);
        ScoreSheet sheet = createSheet(createEvaluation(assignment, rubric, lecturer, null));
        GradeChangeRequest applied = createChange(sheet, GradeChangeStatus.APPLIED);
        GradeChangeRequest pending = createChange(sheet, GradeChangeStatus.PENDING);
        ScoreSheet otherSheet = createSheet(createEvaluation(assignment, rubric, createLecturer("Other reviewer"), null));
        createChange(otherSheet, GradeChangeStatus.APPLIED);

        mockMvc.perform(get("/api/v1/grade-change-requests").with(as(lecturer.getUser()))
                        .param("status", "APPLIED").param("sheetId", sheet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(applied.getId()))
                .andExpect(jsonPath("$.data.content[0].assignmentId").value(assignment.getId()))
                .andExpect(jsonPath("$.data.content[0].requestedByName").value(lecturer.getUser().getFullName()))
                .andExpect(jsonPath("$.data.content[0].reviewedBy").value(admin.getId()))
                .andExpect(jsonPath("$.data.content[0].appliedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/grade-change-requests").with(as(admin))
                        .param("status", "PENDING").param("sheetId", sheet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(pending.getId()))
                .andExpect(jsonPath("$.data.content[0].appliedAt").value(nullValue()));
    }

    @Test
    void endpointsEnforceAuthenticationRolesAndPageBounds() throws Exception {
        mockMvc.perform(get("/api/v1/assessment-components"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/evaluation-assignments").with(as(student.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/grade-change-requests").with(as(student.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/assessment-components").with(as(student.getUser())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"periodId":%d,"code":"REVIEW","name":"Review",
                                 "componentType":"REVIEW","expectedEvaluators":1}
                                """.formatted(period.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/evaluation-committees").with(as(admin)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/defense-sessions").with(as(admin)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/results").with(as(createUser("No student profile", RoleCode.STUDENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/results/{id}", Long.MAX_VALUE).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    // Fixtures are persisted so requests exercise repositories and the current-user filter.
    private User createUser(String name, RoleCode roleCode) {
        Role role = entityManager.createQuery("select r from Role r where r.code = :code", Role.class)
                .setParameter("code", roleCode).getSingleResult();
        User entity = new User();
        entity.setEmail(unique("read") + "@test.local");
        entity.setPasswordHash("unused-for-authenticated-mock-request");
        entity.setFullName(name);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.replaceRoles(Set.of(role), now);
        return persist(entity);
    }

    private Student createStudent(String name) {
        Student entity = new Student();
        entity.setUser(createUser(name, RoleCode.STUDENT));
        entity.setDepartment(department);
        entity.setStudentCode(unique("S"));
        entity.setClassName("SE01");
        entity.setCohort("2026");
        return persist(entity);
    }

    private Lecturer createLecturer(String name) {
        Lecturer entity = new Lecturer();
        entity.setUser(createUser(name, RoleCode.LECTURER));
        entity.setDepartment(department);
        entity.setLecturerCode(unique("L"));
        return persist(entity);
    }

    private RegistrationPeriod createPeriod(String name) {
        RegistrationPeriod entity = new RegistrationPeriod();
        entity.setFaculty(faculty);
        entity.setSemester(semester);
        entity.setName(name);
        entity.setProjectType(ProjectType.COURSE);
        entity.setStatus(RegistrationPeriodStatus.ACTIVE);
        entity.setProposalStartAt(now.minusDays(7));
        entity.setProposalEndAt(now.minusDays(3));
        entity.setRegistrationStartAt(now.minusDays(2));
        entity.setRegistrationEndAt(now.plusDays(2));
        entity.setCreatedBy(admin);
        entity.setCreatedAt(now);
        return persist(entity);
    }

    private ProjectAssignment createAssignment(RegistrationPeriod owner, Student leader) {
        StudentGroup group = new StudentGroup();
        group.setPeriod(owner);
        group.setName(unique("Group"));
        group.setCreatedBy(leader);
        group.setCreatedAt(now);
        group.setStatus(GroupStatus.LOCKED);
        persist(group);
        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setPeriod(owner);
        member.setStudent(leader);
        member.setSlotNo(1);
        member.setMemberRole(GroupMemberRole.LEADER);
        member.setJoinedAt(now.minusDays(1));
        persist(member);

        Topic topic = new Topic();
        topic.setPeriod(owner);
        topic.setFaculty(faculty);
        topic.setDepartment(department);
        topic.setCode(unique("TOPIC"));
        topic.setTitle("Student topic API");
        topic.setDescription("Application with progress and evaluation");
        topic.setObjectives("Working API");
        topic.setExpectedOutcomes("Verified endpoints");
        topic.setProposedBy(lecturer.getUser());
        topic.setProposalSource(ProposalSource.LECTURER);
        topic.setVisibilityType(VisibilityType.OPEN);
        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        topic.setPublishedAt(now);
        persist(topic);

        TopicRegistration registration = new TopicRegistration();
        registration.setPeriod(owner);
        registration.setGroup(group);
        registration.setTopic(topic);
        registration.setRegisteredBy(leader);
        registration.setRegisteredAt(now);
        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setReviewedBy(admin);
        registration.setReviewedAt(now);
        persist(registration);

        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setRegistration(registration);
        assignment.setGroup(group);
        assignment.setTopic(topic);
        assignment.setPeriod(owner);
        assignment.setAssignedBy(admin);
        assignment.setAssignedAt(now);
        return persist(assignment);
    }

    private AssessmentComponent createComponent(RegistrationPeriod owner, String code, AssessmentComponentType type) {
        AssessmentComponent component = new AssessmentComponent();
        component.setPeriod(owner);
        component.setCode(code);
        component.setName(code + " assessment");
        component.setComponentType(type);
        component.setExpectedEvaluators(type == AssessmentComponentType.COMMITTEE ? 3 : 1);
        component.setStatus("LOCKED");
        return persist(component);
    }

    private Rubric createRubric(AssessmentComponent component, int versionNo) {
        Rubric rubric = new Rubric();
        rubric.setComponent(component);
        rubric.setName("Rubric version " + versionNo);
        rubric.setVersionNo(versionNo);
        rubric.setStatus("LOCKED");
        rubric.setCreatedAt(now.plusSeconds(versionNo));
        return persist(rubric);
    }

    private RubricCriterion createCriterion(Rubric rubric, String name, int sortOrder, String score) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setRubric(rubric);
        criterion.setName(name);
        criterion.setDescription(name + " quality");
        criterion.setMaxScore(new BigDecimal(score));
        criterion.setSortOrder(sortOrder);
        return persist(criterion);
    }

    private EvaluationCommittee createCommittee(RegistrationPeriod owner, String name) {
        EvaluationCommittee committee = new EvaluationCommittee();
        committee.setPeriod(owner);
        committee.setFaculty(faculty);
        committee.setName(name);
        committee.setCreatedBy(admin);
        committee.setCreatedAt(now);
        return persist(committee);
    }

    private CommitteeMember createMember(EvaluationCommittee committee, Lecturer member, int slot, CommitteeRole role) {
        CommitteeMember entity = new CommitteeMember();
        entity.setCommittee(committee);
        entity.setLecturer(member);
        entity.setSlotNo(slot);
        entity.setCommitteeRole(role);
        return persist(entity);
    }

    private DefenseSession createDefense(ProjectAssignment assignment, EvaluationCommittee committee) {
        DefenseSession defense = new DefenseSession();
        defense.setAssignment(assignment);
        defense.setCommittee(committee);
        defense.setPeriod(assignment.getPeriod());
        defense.setLocation("A101");
        defense.setStartsAt(now.plusDays(3));
        defense.setEndsAt(now.plusDays(3).plusHours(1));
        return persist(defense);
    }

    private EvaluationAssignment createEvaluation(
            ProjectAssignment assignment, Rubric rubric, Lecturer evaluator, DefenseSession defense) {
        EvaluationAssignment evaluation = new EvaluationAssignment();
        evaluation.setAssignment(assignment);
        evaluation.setPeriod(assignment.getPeriod());
        evaluation.setComponent(rubric.getComponent());
        evaluation.setRubric(rubric);
        evaluation.setLecturer(evaluator);
        evaluation.setDefense(defense);
        evaluation.setEvaluatorRole(defense == null ? "REVIEWER" : "COMMITTEE_MEMBER");
        evaluation.setDeadlineAt(now.plusDays(4));
        return persist(evaluation);
    }

    private ScoreSheet createSheet(EvaluationAssignment evaluation) {
        ScoreSheet sheet = new ScoreSheet();
        sheet.setEvaluation(evaluation);
        sheet.setRubric(evaluation.getRubric());
        return persist(sheet);
    }

    private ResultVersion createResult(ProjectAssignment assignment, int version, ResultStatus state) {
        ResultVersion result = new ResultVersion();
        result.setAssignment(assignment);
        result.setVersionNo(version);
        result.setFinalScore(new BigDecimal("8.50"));
        result.setFinalizedBy(admin);
        result.setFinalizedAt(now.plusSeconds(version));
        result.setStatus(state);
        if (state == ResultStatus.PUBLISHED) {
            result.setPublishedBy(admin);
            result.setPublishedAt(now.plusSeconds(version));
        }
        return persist(result);
    }

    private GradeChangeRequest createChange(ScoreSheet sheet, GradeChangeStatus state) {
        GradeChangeRequest change = new GradeChangeRequest();
        change.setSheet(sheet);
        change.setRequestedBy(sheet.getEvaluation().getLecturer().getUser());
        change.setReason("Correct an incorrectly entered score");
        change.setStatus(state);
        change.setCreatedAt(now);
        if (state == GradeChangeStatus.APPLIED) {
            change.setReviewedBy(admin);
            change.setReviewedAt(now.plusMinutes(1));
            change.setAppliedAt(now.plusMinutes(2));
        }
        return persist(change);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private RequestPostProcessor as(User actor) {
        entityManager.flush();
        return user(UserPrincipal.from(actor));
    }

    private long responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }
}
