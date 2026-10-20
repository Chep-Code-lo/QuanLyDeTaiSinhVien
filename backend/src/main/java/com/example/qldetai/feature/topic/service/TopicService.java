package com.example.qldetai.feature.topic.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Department;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.communication.service.NotificationService;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.identity.domain.Lecturer;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
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
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateTopicRequest;
import com.example.qldetai.feature.topic.dto.TopicDtos.CreateRegistrationTopicRequest;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicService {

    private static final EnumSet<TopicStatus> EDITABLE_STATUSES =
            EnumSet.of(TopicStatus.DRAFT, TopicStatus.PENDING, TopicStatus.REVISION_REQUIRED);

    private final TopicRepository topicRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final TopicReviewRepository topicReviewRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final DepartmentRepository departmentRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;

    public TopicService(
            TopicRepository topicRepository,
            TopicSupervisorRepository topicSupervisorRepository,
            TopicReviewRepository topicReviewRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            StudentGroupRepository studentGroupRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock,
            DepartmentRepository departmentRepository,
            AcademicAppointmentRepository academicAppointmentRepository) {
        this.topicRepository = topicRepository;
        this.topicSupervisorRepository = topicSupervisorRepository;
        this.topicReviewRepository = topicReviewRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.departmentRepository = departmentRepository;
        this.academicAppointmentRepository = academicAppointmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TopicResponse> list(Long periodId, TopicStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
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
        return PageResponse.from(topics.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public TopicResponse get(Long id) {
        return toResponse(requireTopic(id));
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
                resolveReservedGroup(period, request.visibilityType(), request.reservedGroupId()));
        topic.setProposedBy(actor);
        topic.setProposalSource(source);
        topic.setStatus(TopicStatus.PENDING);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        return toResponse(topicRepository.save(topic));
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_CREATE_PUBLISH", entityType = "TOPIC")
    public TopicResponse createForRegistration(CreateRegistrationTopicRequest request, Long actorId) {
        User actor = requireUser(actorId);
        ensureModerator(actor);
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Bộ môn không tồn tại."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!hasRole(actor, RoleCode.ADMIN)
                && !academicAppointmentRepository.hasActiveFacultyAppointment(
                        actorId, PositionCode.DEAN, department.getFaculty().getId(), now)
                && !academicAppointmentRepository.hasActiveDepartmentAppointment(
                        actorId, PositionCode.DEPARTMENT_HEAD, department.getId(), now)) {
            throw new AccessDeniedException("Bạn không có quyền tạo đề tài cho bộ môn này.");
        }
        RegistrationPeriod period = requirePeriod(request.periodId());
        if (!department.getFaculty().getId().equals(period.getFaculty().getId())) {
            throw new BusinessRuleException("Bộ môn phải thuộc khoa của đợt đăng ký.");
        }
        if (!department.isActive() || !department.getFaculty().isActive()) {
            throw new BusinessRuleException("Khoa hoặc bộ môn đã ngừng hoạt động.");
        }
        if (now.isBefore(period.getProposalStartAt()) || now.isAfter(period.getRegistrationEndAt())) {
            throw new BusinessRuleException("Chỉ được tạo đề tài từ khi mở đề xuất đến khi kết thúc đăng ký.");
        }
        String code = normalizeCode(request.code());
        if (topicRepository.existsByPeriodIdAndCodeIgnoreCase(period.getId(), code)) {
            throw new ConflictException("Mã đề tài đã được sử dụng trong đợt đăng ký này.");
        }

        Topic topic = new Topic();
        topic.setPeriod(period);
        topic.setFaculty(period.getFaculty());
        topic.setDepartment(department);
        topic.setCode(code);
        apply(topic, request.title(), request.description(), request.objectives(),
                request.expectedOutcomes(), request.prerequisites(), VisibilityType.OPEN, null);
        topic.setProposedBy(actor);
        topic.setProposalSource(ProposalSource.LECTURER);
        // MySQL requires supervisors to exist before a topic can be published.
        topic.setStatus(TopicStatus.APPROVED);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);

        Lecturer primary = requireLecturer(request.primaryLecturerId(), topic);
        Lecturer secondary = request.secondaryLecturerId() == null
                ? null : requireLecturer(request.secondaryLecturerId(), topic);
        if (secondary != null && secondary.getId().equals(primary.getId())) {
            throw new BusinessRuleException("Hai giảng viên hướng dẫn phải khác nhau.");
        }
        if (primary.getUser().getStatus() != UserStatus.ACTIVE
                || (secondary != null && secondary.getUser().getStatus() != UserStatus.ACTIVE)) {
            throw new BusinessRuleException("Giảng viên hướng dẫn phải có tài khoản đang hoạt động.");
        }

        topicRepository.save(topic);
        TopicReview review = new TopicReview();
        review.setTopic(topic);
        review.setReviewedBy(actor);
        review.setDecision(TopicReviewDecision.APPROVED);
        review.setComment("Đề tài được quản lý học thuật tạo và công bố cho sinh viên đăng ký.");
        review.setReviewedAt(now);
        topicReviewRepository.save(review);
        topicSupervisorRepository.save(createSupervisor(topic, primary, actor, 1, now));
        if (secondary != null) {
            topicSupervisorRepository.save(createSupervisor(topic, secondary, actor, 2, now));
        }
        topicSupervisorRepository.flush();
        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setPublishedAt(now);
        topicRepository.saveAndFlush(topic);
        notificationService.publish(actorId, "topic-published-" + topic.getId(),
                "Đề tài đã được công bố",
                "Đề tài " + topic.getCode() + " đã sẵn sàng cho sinh viên đăng ký.", "/topics");
        return toResponse(topic);
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
                resolveReservedGroup(topic.getPeriod(), request.visibilityType(), request.reservedGroupId()));
        topic.setUpdatedAt(LocalDateTime.now(clock));
        return toResponse(topic);
    }

    @Transactional
    @BusinessAudit(action = "TOPIC_SUPERVISORS_ASSIGN", entityType = "TOPIC", entityIdArgument = 0)
    public TopicResponse assignSupervisors(Long id, AssignSupervisorsRequest request, Long actorId) {
        Topic topic = requireTopic(id);
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

        topicSupervisorRepository.deleteByTopicId(topic.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        topicSupervisorRepository.save(createSupervisor(topic, primary, actor, 1, now));
        if (secondary != null) {
            topicSupervisorRepository.save(createSupervisor(topic, secondary, actor, 2, now));
        }
        topic.setUpdatedAt(now);
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
        topic.setUpdatedAt(review.getReviewedAt());

        notificationService.publish(
                topic.getProposedBy().getId(),
                "topic-review-" + topic.getId() + "-" + review.getId(),
                "Đề tài đã được phản hồi",
                "Đề tài " + topic.getCode() + " vừa được cập nhật trạng thái " + topic.getStatus() + ".",
                "/topics/" + topic.getId());

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
        topic.setUpdatedAt(now);
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
        topic.setUpdatedAt(LocalDateTime.now(clock));
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
            Long reservedGroupId) {
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

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TopicResponse toResponse(Topic topic) {
        List<TopicSupervisorResponse> supervisors = topicSupervisorRepository.findByTopicIdOrderBySlotNoAsc(topic.getId())
                .stream()
                .map(supervisor -> new TopicSupervisorResponse(
                        supervisor.getLecturer().getId(),
                        supervisor.getLecturer().getLecturerCode(),
                        supervisor.getLecturer().getUser().getFullName(),
                        supervisor.getSlotNo()))
                .toList();
        TopicReviewResponse latestReview = topicReviewRepository.findByTopicIdOrderByReviewedAtDesc(topic.getId())
                .stream()
                .findFirst()
                .map(review -> new TopicReviewResponse(
                        review.getId(),
                        review.getDecision(),
                        review.getComment(),
                        review.getReviewedBy().getId(),
                        review.getReviewedBy().getFullName(),
                        review.getReviewedAt()))
                .orElse(null);

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
                topic.getPublishedAt(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topic.getVersion(),
                supervisors,
                latestReview);
    }
}
