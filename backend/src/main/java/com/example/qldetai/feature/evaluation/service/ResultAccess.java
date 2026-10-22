package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.feature.evaluation.domain.*;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.topic.domain.TopicSupervisor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** Shared database predicate: filtering happens before pagination and applies to detail reads too. */
@Component
public class ResultAccess {
    private final EvaluationActorAccess actors;
    private final Clock clock;

    public ResultAccess(EvaluationActorAccess actors, Clock clock) {
        this.actors = actors;
        this.clock = clock;
    }

    public Specification<ResultVersion> readableBy(Long userId) {
        boolean admin =
                actors.user(userId).getUserRoles().stream()
                        .anyMatch(role -> role.getRole().getCode() == RoleCode.ADMIN);
        LocalDateTime now = LocalDateTime.now(clock);
        return (root, query, cb) -> {
            if (admin) return cb.conjunction();
            var assignment = root.get("assignment");

            var appointments = query.subquery(Long.class);
            var appointment = appointments.from(AcademicAppointment.class);
            appointments
                    .select(appointment.get("id"))
                    .where(
                            cb.equal(appointment.get("lecturer").get("user").get("id"), userId),
                            cb.lessThanOrEqualTo(appointment.get("startsAt"), now),
                            cb.or(
                                    cb.isNull(appointment.get("endsAt")),
                                    cb.greaterThan(appointment.get("endsAt"), now)),
                            cb.or(
                                    cb.and(
                                            cb.equal(
                                                    appointment.get("positionCode"),
                                                    PositionCode.DEAN),
                                            cb.equal(
                                                    appointment.get("faculty"),
                                                    assignment.get("period").get("faculty"))),
                                    cb.and(
                                            cb.equal(
                                                    appointment.get("positionCode"),
                                                    PositionCode.DEPARTMENT_HEAD),
                                            cb.equal(
                                                    appointment.get("department"),
                                                    assignment.get("topic").get("department")))));

            var evaluators = query.subquery(Long.class);
            var evaluation = evaluators.from(EvaluationAssignment.class);
            evaluators
                    .select(evaluation.get("id"))
                    .where(
                            cb.equal(evaluation.get("assignment"), assignment),
                            cb.equal(evaluation.get("lecturer").get("user").get("id"), userId),
                            cb.equal(evaluation.get("status"), EvaluationStatus.ACTIVE));

            var committees = query.subquery(Long.class);
            var defense = committees.from(DefenseSession.class);
            var member = committees.from(CommitteeMember.class);
            committees
                    .select(defense.get("id"))
                    .where(
                            cb.equal(defense.get("assignment"), assignment),
                            cb.notEqual(defense.get("status"), DefenseStatus.CANCELLED),
                            cb.equal(member.get("committee"), defense.get("committee")),
                            cb.equal(member.get("lecturer").get("user").get("id"), userId));

            var supervisors = query.subquery(Long.class);
            var supervisor = supervisors.from(TopicSupervisor.class);
            supervisors
                    .select(supervisor.get("id"))
                    .where(
                            cb.equal(supervisor.get("topic"), assignment.get("topic")),
                            cb.equal(supervisor.get("lecturer").get("user").get("id"), userId));

            return cb.or(
                    cb.exists(appointments),
                    cb.exists(evaluators),
                    cb.exists(committees),
                    cb.and(
                            cb.equal(root.get("status"), ResultStatus.PUBLISHED),
                            cb.exists(supervisors)));
        };
    }
}
