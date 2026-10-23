package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.feature.evaluation.domain.*;
import com.example.qldetai.feature.identity.domain.*;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.UserRepository;

import jakarta.persistence.criteria.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Apply visibility before pagination, including count queries. */
@Component
@RequiredArgsConstructor
public class EvaluationReadScope {
    private final UserRepository users;
    private final AcademicAppointmentRepository appointments;
    private final Clock clock;

    private boolean admin(Long actorId) {
        return users.findWithRolesById(actorId)
                .map(
                        user ->
                                user.getUserRoles().stream()
                                        .anyMatch(
                                                role -> role.getRole().getCode() == RoleCode.ADMIN))
                .orElse(false);
    }

    public Specification<EvaluationAssignment> assignments(Long actorId) {
        if (actorId == null || admin(actorId)) return (root, query, cb) -> cb.conjunction();
        var active = appointments.findActiveByUserId(actorId, LocalDateTime.now(clock));
        return (root, query, cb) ->
                cb.or(
                        cb.equal(root.get("lecturer").get("user").get("id"), actorId),
                        managed(root.get("assignment").get("topic"), active, cb));
    }

    public Specification<DefenseSession> defenses(Long actorId) {
        if (actorId == null || admin(actorId)) return (root, query, cb) -> cb.conjunction();
        var active = appointments.findActiveByUserId(actorId, LocalDateTime.now(clock));
        return (root, query, cb) ->
                cb.or(
                        member(root.get("committee"), actorId, query, cb),
                        managed(root.get("assignment").get("topic"), active, cb));
    }

    public Specification<EvaluationCommittee> committees(Long actorId) {
        if (actorId == null || admin(actorId)) return (root, query, cb) -> cb.conjunction();
        var active = appointments.findActiveByUserId(actorId, LocalDateTime.now(clock));
        var deanFaculties =
                active.stream()
                        .filter(a -> a.getPositionCode() == PositionCode.DEAN)
                        .map(a -> a.getFaculty().getId())
                        .toList();
        return (root, query, cb) -> {
            var assigned = query.subquery(Long.class);
            var defense = assigned.from(DefenseSession.class);
            assigned.select(defense.get("id"))
                    .where(
                            cb.equal(defense.get("committee"), root),
                            managed(defense.get("assignment").get("topic"), active, cb));
            return cb.or(
                    member(root, actorId, query, cb),
                    deanFaculties.isEmpty()
                            ? cb.disjunction()
                            : root.get("faculty").get("id").in(deanFaculties),
                    cb.exists(assigned));
        };
    }

    private Predicate member(
            Path<?> committee, Long actorId, CriteriaQuery<?> query, CriteriaBuilder cb) {
        var subquery = query.subquery(Long.class);
        var member = subquery.from(CommitteeMember.class);
        subquery.select(member.get("id"))
                .where(
                        cb.equal(member.get("committee"), committee),
                        cb.equal(member.get("lecturer").get("user").get("id"), actorId));
        return cb.exists(subquery);
    }

    private Predicate managed(Path<?> topic, List<AcademicAppointment> active, CriteriaBuilder cb) {
        var predicates =
                active.stream()
                        .map(
                                a ->
                                        a.getPositionCode() == PositionCode.DEAN
                                                ? cb.equal(
                                                        topic.get("faculty").get("id"),
                                                        a.getFaculty().getId())
                                                : a.getDepartment() != null
                                                        ? cb.equal(
                                                                topic.get("department").get("id"),
                                                                a.getDepartment().getId())
                                                        : cb.disjunction())
                        .toList();
        return cb.or(predicates.toArray(Predicate[]::new));
    }
}
