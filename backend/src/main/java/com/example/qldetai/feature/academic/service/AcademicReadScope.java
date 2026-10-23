package com.example.qldetai.feature.academic.service;

import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.domain.TopicSupervisor;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Database predicates shared by collection and ID reads; no filtering after pagination. */
@Component
@RequiredArgsConstructor
public class AcademicReadScope {
    private final UserRepository users;
    private final AcademicAppointmentRepository appointments;
    private final Clock clock;

    private boolean admin(Long actorId) {
        return actorId != null && users.findWithRolesById(actorId)
                .map(u -> u.getUserRoles().stream().anyMatch(r -> r.getRole().getCode() == RoleCode.ADMIN))
                .orElse(false);
    }

    private List<AcademicAppointment> active(Long actorId) {
        return appointments.findActiveByUserId(actorId, LocalDateTime.now(clock));
    }

    public Predicate managedTopic(Path<?> topic, List<AcademicAppointment> active, CriteriaBuilder cb) {
        return cb.or(active.stream().map(a -> a.getPositionCode() == PositionCode.DEAN
                ? cb.equal(topic.get("faculty").get("id"), a.getFaculty().getId())
                : a.getPositionCode() == PositionCode.DEPARTMENT_HEAD && a.getDepartment() != null
                    ? cb.equal(topic.get("department").get("id"), a.getDepartment().getId())
                    : cb.disjunction()).toArray(Predicate[]::new));
    }

    private Predicate supervised(Path<?> topic, Long actorId, CommonAbstractCriteria query, CriteriaBuilder cb) {
        var sq = query.subquery(Long.class);
        var supervisor = sq.from(TopicSupervisor.class);
        sq.select(supervisor.get("id")).where(cb.equal(supervisor.get("topic"), topic),
                cb.equal(supervisor.get("lecturer").get("user").get("id"), actorId));
        return cb.exists(sq);
    }

    public Specification<Topic> topics(Long actorId) {
        if (admin(actorId)) return (r, q, cb) -> cb.conjunction();
        var active = active(actorId);
        return (r, q, cb) -> cb.or(
                cb.equal(r.get("status"), com.example.qldetai.feature.topic.domain.TopicStatus.PUBLISHED),
                cb.equal(r.get("proposedBy").get("id"), actorId),
                managedTopic(r, active, cb), supervised(r, actorId, q, cb));
    }

    public Specification<ProjectAssignment> projects(Long actorId) {
        if (admin(actorId)) return (r, q, cb) -> cb.conjunction();
        var active = active(actorId);
        return (r, q, cb) -> {
            var sq = q.subquery(Long.class);
            var evaluation = sq.from(com.example.qldetai.feature.evaluation.domain.EvaluationAssignment.class);
            sq.select(evaluation.get("id")).where(cb.equal(evaluation.get("assignment"), r),
                    cb.equal(evaluation.get("lecturer").get("user").get("id"), actorId),
                    cb.equal(evaluation.get("status"), com.example.qldetai.feature.evaluation.domain.EvaluationStatus.ACTIVE));
            var committees = q.subquery(Long.class);
            var defense = committees.from(com.example.qldetai.feature.evaluation.domain.DefenseSession.class);
            var member = committees.from(com.example.qldetai.feature.evaluation.domain.CommitteeMember.class);
            committees.select(defense.get("id")).where(cb.equal(defense.get("assignment"), r),
                    cb.notEqual(defense.get("status"), com.example.qldetai.feature.evaluation.domain.DefenseStatus.CANCELLED),
                    cb.equal(member.get("committee"), defense.get("committee")),
                    cb.equal(member.get("lecturer").get("user").get("id"), actorId));
            return cb.or(managedTopic(r.get("topic"), active, cb),
                    supervised(r.get("topic"), actorId, q, cb), cb.exists(sq), cb.exists(committees));
        };
    }

    public Specification<TopicRegistration> registrations(Long actorId) {
        if (admin(actorId)) return (r, q, cb) -> cb.conjunction();
        var active = active(actorId);
        return (r, q, cb) -> cb.or(managedTopic(r.get("topic"), active, cb),
                supervised(r.get("topic"), actorId, q, cb));
    }

    public Specification<StudentGroup> groups(Long actorId) {
        if (admin(actorId)) return (r, q, cb) -> cb.conjunction();
        var active = active(actorId);
        return (r, q, cb) -> {
            var registrationQuery = q.subquery(Long.class);
            var registration = registrationQuery.from(TopicRegistration.class);
            registrationQuery.select(registration.get("id")).where(cb.equal(registration.get("group"), r),
                    cb.or(managedTopic(registration.get("topic"), active, cb),
                            supervised(registration.get("topic"), actorId, registrationQuery, cb)));
            var management = active.stream().map(a -> a.getPositionCode() == PositionCode.DEAN
                    ? cb.equal(r.get("period").get("faculty").get("id"), a.getFaculty().getId())
                    : cb.disjunction()).toArray(Predicate[]::new);
            var departments = active.stream().filter(a -> a.getPositionCode() == PositionCode.DEPARTMENT_HEAD
                    && a.getDepartment() != null).map(a -> a.getDepartment().getId()).toList();
            var members = q.subquery(Long.class);
            var member = members.from(com.example.qldetai.feature.groupregistration.domain.GroupMember.class);
            members.select(member.get("id")).where(cb.equal(member.get("group"), r),
                    cb.isNull(member.get("leftAt")), departments.isEmpty() ? cb.disjunction()
                            : member.get("student").get("department").get("id").in(departments));
            return cb.or(cb.or(management), cb.exists(registrationQuery), cb.exists(members));
        };
    }
}
