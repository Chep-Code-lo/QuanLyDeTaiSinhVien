package com.example.qldetai.feature.topic.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.Student;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.domain.ProposalSource;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicReview;
import com.example.qldetai.feature.topic.domain.TopicReviewDecision;
import com.example.qldetai.feature.topic.domain.TopicStatus;
import com.example.qldetai.feature.topic.domain.TopicSupervisor;
import com.example.qldetai.feature.topic.domain.VisibilityType;
import com.example.qldetai.feature.topic.dto.TopicDtos.AssignSupervisorsRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.PublishTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.ReviewTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicResponse;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicReviewResponse;
import com.example.qldetai.feature.topic.dto.TopicDtos.TopicSupervisorResponse;
import com.example.qldetai.feature.topic.dto.TopicDtos.UpdateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.WithdrawTopicRequest;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.feature.topic.repository.TopicReviewRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TopicService {

    private static final EnumSet<TopicStatus> EDITABLE_STATUSES =
            EnumSet.of(TopicStatus.DRAFT, TopicStatus.PENDING, TopicStatus.REVISION_REQUIRED);

    private final TopicRepository topicRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final TopicReviewRepository topicReviewRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailNotificationEventService emailNotificationEventService;
    private final Clock clock;
    private final EntityManager entityManager;


    @Transactional(readOnly = true)
    public PageResponse<TopicResponse> list(
            Long periodId, TopicStatus status, int page, int size, Long actorId) {
        return list(periodId, status, null, page, size, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<TopicResponse> list(
            Long periodId, TopicStatus status, String search, int page, int size, Long actorId) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        User actor = requireUser(actorId);
        boolean staff = isAcademicStaff(actor);
        if (!staff && !hasRole(actor, RoleCode.STUDENT)) {
            throw new AccessDeniedException("A student, lecturer, or administrator account is required.");
        }
        org.springframework.data.jpa.domain.Specification<Topic> specification = (root, query, cb) -> {
            var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (periodId != null) conditions.add(cb.equal(root.get("period").get("id"), periodId));
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (!staff) {
                var membership = query.subquery(Long.class);
                var member = membership.from(com.example.qldetai.feature.groupregistration.domain.GroupMember.class);
                membership.select(member.get("id")).where(
                        cb.equal(member.get("group"), root.get("reservedGroup")),
                        cb.equal(member.get("student").get("user").get("id"), actorId),
                        cb.isNull(member.get("leftAt")));
                conditions.add(cb.or(cb.equal(root.get("proposedBy").get("id"), actorId),
                        cb.and(cb.equal(root.get("status"), TopicStatus.PUBLISHED),
                                cb.or(cb.equal(root.get("visibilityType"), VisibilityType.OPEN), cb.exists(membership)))));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var supervisorQuery = query.subquery(Long.class);
                var supervisor = supervisorQuery.from(com.example.qldetai.feature.topic.domain.TopicSupervisor.class);
                supervisorQuery.select(supervisor.get("id")).where(cb.equal(supervisor.get("topic"), root),
                        cb.like(cb.lower(supervisor.get("lecturer").get("user").get("fullName")), pattern, '\\'));
                conditions.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("department").get("name")), pattern, '\\'),
                        cb.exists(supervisorQuery)));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return toResponses(topicRepository.findAll(specification, pageable), staff ? null : actorId);
    }

    @Transactional(readOnly = true)
    public TopicResponse get(Long id, Long actorId) {
        Topic topic = requireTopic(id);
        User actor = requireUser(actorId);
        if (isAcademicStaff(actor)) {
            return toResponse(topic);
        }
        if (!hasRole(actor, RoleCode.STUDENT) || !canStudentView(topic, actorId)) {
            throw new AccessDeniedException("You cannot view this topic.");
        }
        return toResponse(topic, topic.getProposedBy().getId().equals(actorId));
    }

    private Page<Topic> listAll(Long periodId, TopicStatus status, PageRequest pageable) {
        Page<Topic> topics;
        if (periodId != null && status != null) {
            topics = topicRepository.findByPeriodIdAndStatus(periodId, status, pageable);
        } else if (periodId != null) {
            topics = topicRepository.findByPeriodId(periodId, pageable);
        } else if (status != null) {
            topics = topicRepository.findByStatus(status, pageable);
        } else {
            topics = topicRepository.findAll(pageable);
        }
        return topics;
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_CREATE", entityType = "TOPIC")
    public TopicResponse create(CreateTopicRequest request, Long actorId) {
        User actor = requireUser(actorId);
        RegistrationPeriod period = requirePeriod(request.periodId());
        LocalDateTime now = LocalDateTime.now(clock);
        ProposalSource source = determineProposalSource(actor);
        Department department = resolveDepartment(actor);
        if (!hasRole(actor, RoleCode.ADMIN) && !isProposalWindowOpen(period, now)) {
            throw new BusinessRuleException("Topics can only be proposed during the proposal window.");
        }
        if (!department.getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("Topic proposer must belong to the same faculty as the registration period.");
        }
        String code = normalizeCode(request.code());
        if (topicRepository.existsByPeriodIdAndCodeIgnoreCase(period.getId(), code)) {
            throw new ConflictException("Topic code is already used in this registration period.");
        }

        Topic topic = new Topic();
        topic.setPeriod(period);
        topic.setFaculty(period.getFaculty());
        topic.setDepartment(department);
        topic.setCode(code);
        apply(
                topic,
                request.title(),
                request.description(),
                request.objectives(),
                request.expectedOutcomes(),
                request.prerequisites(),
                request.visibilityType(),
                resolveReservedGroup(period, request.visibilityType(), request.reservedGroupId(), actor));
        topic.setProposedBy(actor);
        topic.setProposalSource(source);
        topic.setStatus(TopicStatus.PENDING);
        return toResponse(topicRepository.save(topic));
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_CREATE_FOR_REGISTRATION", entityType = "TOPIC")
    public TopicResponse createForRegistration(CreateRegistrationTopicRequest request, Long actorId) {
        User actor = requireUser(actorId);
        RegistrationPeriod period = requirePeriod(request.periodId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isProposalWindowOpen(period, now)) {
            throw new BusinessRuleException("Topics can only be proposed during the proposal window.");
        }

        Department department = requireActiveDepartment(request.departmentId());
        if (!department.getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("Department must belong to the same faculty as the registration period.");
        }

        String code = normalizeCode(request.code());
        if (topicRepository.existsByPeriodIdAndCodeIgnoreCase(period.getId(), code)) {
            throw new ConflictException("Topic code is already used in this registration period.");
        }
        if (request.secondaryLecturerId() != null
                && request.secondaryLecturerId().equals(request.primaryLecturerId())) {
            throw new BusinessRuleException("Primary and secondary supervisors must be different lecturers.");
        }

        Topic topic = new Topic();
        topic.setPeriod(period);
        topic.setFaculty(period.getFaculty());
        topic.setDepartment(department);
        topic.setCode(code);
        apply(
                topic,
                request.title(),
                request.description(),
                request.objectives(),
                request.expectedOutcomes(),
                request.prerequisites(),
                VisibilityType.OPEN,
                null);
        topic.setProposedBy(actor);
        topic.setProposalSource(ProposalSource.LECTURER);
        topic.setStatus(TopicStatus.APPROVED);
        topic = topicRepository.saveAndFlush(topic);

        Lecturer primary = requireLecturer(request.primaryLecturerId(), topic);
        Lecturer secondary = request.secondaryLecturerId() == null
                ? null
                : requireLecturer(request.secondaryLecturerId(), topic);
        topicSupervisorRepository.save(createSupervisor(topic, primary, actor, 1, now));
        if (secondary != null) {
            topicSupervisorRepository.save(createSupervisor(topic, secondary, actor, 2, now));
        }
        topicSupervisorRepository.flush();

        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setPublishedAt(now);
        return toResponse(topicRepository.saveAndFlush(topic));
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_UPDATE", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse update(Long id, UpdateTopicRequest request, Long actorId) {
        Topic topic = requireTopic(id);
        User actor = requireUser(actorId);
        verifyVersion(topic, request.version());
        ensureTopicEditor(topic, actor);
        apply(
                topic,
                request.title(),
                request.description(),
                request.objectives(),
                request.expectedOutcomes(),
                request.prerequisites(),
                request.visibilityType(),
                resolveReservedGroup(topic.getPeriod(), request.visibilityType(), request.reservedGroupId(), actor));
        return toResponse(topic);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_SUPERVISORS_ASSIGN", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse assignSupervisors(Long id, AssignSupervisorsRequest request, Long actorId) {
        Topic topic = topicRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic was not found."));
        User actor = requireUser(actorId);
        verifyVersion(topic, request.version());
        ensureModerator(actor);
        if (topic.getStatus() != TopicStatus.APPROVED && topic.getStatus() != TopicStatus.PUBLISHED) {
            throw new BusinessRuleException("Supervisors can only be assigned after the topic is approved.");
        }

        Lecturer primary = requireLecturer(request.primaryLecturerId(), topic);
        Lecturer secondary = request.secondaryLecturerId() == null
                ? null
                : requireLecturer(request.secondaryLecturerId(), topic);
        if (secondary != null && secondary.getId().equals(primary.getId())) {
            throw new BusinessRuleException("Primary and secondary supervisors must be different lecturers.");
        }

        // Supervisor rows are part of the topic revision even if topic fields are unchanged.
        entityManager.lock(topic, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        topicSupervisorRepository.deleteByTopicId(topic.getId());
        topicSupervisorRepository.flush();
        LocalDateTime now = LocalDateTime.now(clock);
        topicSupervisorRepository.save(createSupervisor(topic, primary, actor, 1, now));
        if (secondary != null) {
            topicSupervisorRepository.save(createSupervisor(topic, secondary, actor, 2, now));
        }
        return toResponse(topic);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_REVIEW", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse review(Long id, ReviewTopicRequest request, Long actorId) {
        Topic topic = requireTopic(id);
        User actor = requireUser(actorId);
        verifyVersion(topic, request.version());
        ensureModerator(actor);
        if (topic.getStatus() == TopicStatus.PUBLISHED || topic.getStatus() == TopicStatus.WITHDRAWN) {
            throw new BusinessRuleException("Published or withdrawn topics can no longer be reviewed.");
        }

        TopicReview review = new TopicReview();
        review.setTopic(topic);
        review.setReviewedBy(actor);
        review.setDecision(request.decision());
        review.setComment(request.comment().trim());
        review.setReviewedAt(LocalDateTime.now(clock));
        topicReviewRepository.save(review);

        topic.setStatus(switch (request.decision()) {
            case APPROVED -> TopicStatus.APPROVED;
            case REVISION_REQUIRED -> TopicStatus.REVISION_REQUIRED;
            case REJECTED -> TopicStatus.REJECTED;
        });

        notificationService.publish(
                topic.getProposedBy().getId(),
                "topic-review-" + topic.getId() + "-" + review.getId(),
                "Đề tài đã được phản hồi",
                "Đề tài " + topic.getCode() + " vừa được cập nhật trạng thái " + topic.getStatus() + ".",
                "/topics/" + topic.getId());

        if (review.getDecision() == TopicReviewDecision.APPROVED
                || review.getDecision() == TopicReviewDecision.REJECTED) {
            emailNotificationEventService.topicDecision(topic, review);
        }

        return toResponse(topic);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_PUBLISH", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse publish(Long id, PublishTopicRequest request, Long actorId) {
        Topic topic = requireTopic(id);
        User actor = requireUser(actorId);
        verifyVersion(topic, request.version());
        ensureModerator(actor);
        if (topic.getStatus() != TopicStatus.APPROVED) {
            throw new BusinessRuleException("Only approved topics can be published.");
        }
        List<TopicSupervisor> supervisors = topicSupervisorRepository.findByTopicIdOrderBySlotNoAsc(topic.getId());
        if (supervisors.stream().noneMatch(supervisor -> supervisor.getSlotNo() == 1)) {
            throw new BusinessRuleException("Assign a primary supervisor before publishing the topic.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setPublishedAt(now);
        notificationService.publish(
                topic.getProposedBy().getId(),
                "topic-published-" + topic.getId(),
                "Đề tài đã được công bố",
                "Đề tài " + topic.getCode() + " đã được công bố để sinh viên đăng ký.",
                "/topics/" + topic.getId());
        return toResponse(topic);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_WITHDRAW", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse withdraw(Long id, WithdrawTopicRequest request, Long actorId) {
        Topic topic = requireTopic(id);
        User actor = requireUser(actorId);
        verifyVersion(topic, request.version());
        if (!hasRole(actor, RoleCode.ADMIN) && !topic.getProposedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the proposer or an administrator can withdraw this topic.");
        }
        if (topic.getStatus() == TopicStatus.PUBLISHED) {
            throw new BusinessRuleException("Published topics cannot be withdrawn.");
        }
        topic.setStatus(TopicStatus.WITHDRAWN);
        notificationService.publish(
                topic.getProposedBy().getId(),
                "topic-withdrawn-" + topic.getId(),
                "Đề tài đã được rút",
                request.reason().trim(),
                "/topics/" + topic.getId());
        return toResponse(topic);
    }

    private void apply(
            Topic topic,
            String title,
            String description,
            String objectives,
            String expectedOutcomes,
            String prerequisites,
            VisibilityType visibilityType,
            StudentGroup reservedGroup) {
        topic.setTitle(title.trim());
        topic.setDescription(description.trim());
        topic.setObjectives(objectives.trim());
        topic.setExpectedOutcomes(expectedOutcomes.trim());
        topic.setPrerequisites(normalizeNullable(prerequisites));
        topic.setVisibilityType(visibilityType);
        topic.setReservedGroup(reservedGroup);
    }

    private ProposalSource determineProposalSource(User actor) {
        if (lecturerRepository.findByUserId(actor.getId()).isPresent()) {
            return ProposalSource.LECTURER;
        }
        if (studentRepository.findByUserId(actor.getId()).isPresent()) {
            return ProposalSource.STUDENT;
        }
        throw new AccessDeniedException("A student or lecturer profile is required to propose a topic.");
    }

    private Department resolveDepartment(User actor) {
        return lecturerRepository.findByUserId(actor.getId())
                .map(Lecturer::getDepartment)
                .or(() -> studentRepository.findByUserId(actor.getId()).map(Student::getDepartment))
                .orElseThrow(() ->
                        new AccessDeniedException("A student or lecturer profile is required to propose a topic."));
    }

    private StudentGroup resolveReservedGroup(
            RegistrationPeriod period,
            VisibilityType visibilityType,
            Long reservedGroupId,
            User actor) {
        if (visibilityType == VisibilityType.OPEN) {
            if (reservedGroupId != null) {
                throw new BusinessRuleException("Open topics cannot target a reserved group.");
            }
            return null;
        }
        if (reservedGroupId == null) {
            throw new BusinessRuleException("Reserved topics must specify a target group.");
        }
        StudentGroup group = studentGroupRepository.findById(reservedGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserved group was not found."));
        if (!group.getPeriod().getId().equals(period.getId())) {
            throw new BusinessRuleException("Reserved group must belong to the same registration period.");
        }
        if (hasRole(actor, RoleCode.STUDENT)
                && !isAcademicStaff(actor)
                && !groupMemberRepository.existsByGroupIdAndStudentUserIdAndLeftAtIsNull(group.getId(), actor.getId())) {
            throw new AccessDeniedException("Students can reserve a topic only for their own group.");
        }
        return group;
    }

    private TopicSupervisor createSupervisor(
            Topic topic,
            Lecturer lecturer,
            User actor,
            int slotNo,
            LocalDateTime assignedAt) {
        TopicSupervisor supervisor = new TopicSupervisor();
        supervisor.setTopic(topic);
        supervisor.setLecturer(lecturer);
        supervisor.setSlotNo(slotNo);
        supervisor.setAssignedBy(actor);
        supervisor.setAssignedAt(assignedAt);
        return supervisor;
    }

    private Lecturer requireLecturer(Long lecturerId, Topic topic) {
        Lecturer lecturer = lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecturer was not found."));
        if (!lecturer.getDepartment().getFaculty().getId().equals(topic.getFaculty().getId())) {
            throw new BusinessRuleException("Supervisor must belong to the same faculty as the topic.");
        }
        return lecturer;
    }

    private RegistrationPeriod requirePeriod(Long id) {
        RegistrationPeriod period = registrationPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));
        if (period.getStatus() != RegistrationPeriodStatus.ACTIVE) {
            throw new BusinessRuleException("Topics can only be managed in an active registration period.");
        }
        return period;
    }

    private Department requireActiveDepartment(Long id) {
        return departmentRepository.findById(id)
                .filter(Department::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active department was not found."));
    }

    private Topic requireTopic(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic was not found."));
    }

    private User requireUser(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
    }

    private void verifyVersion(Topic topic, int requestedVersion) {
        if (topic.getVersion() != requestedVersion) {
            throw new ConflictException("The topic was changed by another user. Reload and try again.");
        }
    }

    private void ensureTopicEditor(Topic topic, User actor) {
        if (!hasRole(actor, RoleCode.ADMIN) && !topic.getProposedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the topic proposer or an administrator can edit this topic.");
        }
        if (!EDITABLE_STATUSES.contains(topic.getStatus())) {
            throw new BusinessRuleException("Only draft, pending, or revision-required topics can be edited.");
        }
    }

    private void ensureModerator(User actor) {
        if (!hasRole(actor, RoleCode.ADMIN) && !hasRole(actor, RoleCode.LECTURER)) {
            throw new AccessDeniedException("Only lecturers or administrators can moderate topics.");
        }
    }

    private boolean isProposalWindowOpen(RegistrationPeriod period, LocalDateTime now) {
        return !now.isBefore(period.getProposalStartAt()) && !now.isAfter(period.getProposalEndAt());
    }

    private boolean hasRole(User user, RoleCode role) {
        return user.getUserRoles().stream()
                .anyMatch(userRole -> userRole.getRole().getCode() == role);
    }

    private boolean isAcademicStaff(User user) {
        return hasRole(user, RoleCode.ADMIN) || hasRole(user, RoleCode.LECTURER);
    }

    private boolean canStudentView(Topic topic, Long studentUserId) {
        if (topic.getProposedBy().getId().equals(studentUserId)) {
            return true;
        }
        if (topic.getStatus() != TopicStatus.PUBLISHED) {
            return false;
        }
        return topic.getVisibilityType() == VisibilityType.OPEN
                || (topic.getReservedGroup() != null
                        && groupMemberRepository.existsByGroupIdAndStudentUserIdAndLeftAtIsNull(
                                topic.getReservedGroup().getId(), studentUserId));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TopicResponse toResponse(Topic topic) {
        return toResponse(topic, true);
    }

    private TopicResponse toResponse(Topic topic, boolean includeLatestReview) {
        return toResponse(topic, topicSupervisorRepository.findByTopicIdOrderBySlotNoAsc(topic.getId()),
                includeLatestReview ? topicReviewRepository.findFirstByTopicIdOrderByReviewedAtDesc(topic.getId()).orElse(null) : null);
    }

    private PageResponse<TopicResponse> toResponses(Page<Topic> found, Long studentUserId) {
        if (found.isEmpty()) {
            return PageResponse.from(found.map(this::toResponse));
        }
        List<Long> topicIds = found.getContent().stream().map(Topic::getId).toList();
        Map<Long, List<TopicSupervisor>> supervisorsByTopic = topicSupervisorRepository
                .findByTopicIdInOrderBySlotNoAsc(topicIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(supervisor -> supervisor.getTopic().getId()));
        List<Long> reviewableIds = found.getContent().stream()
                .filter(topic -> studentUserId == null || topic.getProposedBy().getId().equals(studentUserId))
                .map(Topic::getId).toList();
        Map<Long, TopicReview> latestReviews = reviewableIds.isEmpty() ? Map.of() : topicReviewRepository
                .findLatestByTopicIds(reviewableIds).stream()
                .collect(java.util.stream.Collectors.toMap(review -> review.getTopic().getId(), review -> review));
        return PageResponse.from(found.map(topic -> toResponse(topic,
                supervisorsByTopic.getOrDefault(topic.getId(), List.of()), latestReviews.get(topic.getId()))));
    }

    private TopicResponse toResponse(Topic topic, List<TopicSupervisor> foundSupervisors, TopicReview review) {
        List<TopicSupervisorResponse> supervisors = foundSupervisors.stream()
                .map(supervisor -> new TopicSupervisorResponse(
                        supervisor.getLecturer().getId(),
                        supervisor.getLecturer().getLecturerCode(),
                        supervisor.getLecturer().getUser().getFullName(),
                        supervisor.getSlotNo()))
                .toList();
        TopicReviewResponse latestReview = review == null ? null : new TopicReviewResponse(
                                review.getId(),
                                review.getDecision(),
                                review.getComment(),
                                review.getReviewedBy().getId(),
                                review.getReviewedBy().getFullName(),
                                review.getReviewedAt());

        return new TopicResponse(
                topic.getId(),
                topic.getPeriod().getId(),
                topic.getPeriod().getName(),
                topic.getFaculty().getId(),
                topic.getFaculty().getName(),
                topic.getDepartment().getId(),
                topic.getDepartment().getName(),
                topic.getCode(),
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getExpectedOutcomes(),
                topic.getPrerequisites(),
                topic.getProposedBy().getId(),
                topic.getProposedBy().getFullName(),
                topic.getProposalSource(),
                topic.getVisibilityType(),
                topic.getReservedGroup() == null ? null : topic.getReservedGroup().getId(),
                topic.getReservedGroup() == null ? null : topic.getReservedGroup().getName(),
                topic.getStatus(),
                topic.getStatus() == TopicStatus.PUBLISHED,
                topic.getPublishedAt(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topic.getVersion(),
                supervisors,
                latestReview);
    }
}
