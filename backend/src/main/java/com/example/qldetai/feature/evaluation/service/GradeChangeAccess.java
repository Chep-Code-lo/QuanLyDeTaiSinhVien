package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.feature.evaluation.domain.EvaluationAssignment;
import com.example.qldetai.feature.evaluation.domain.EvaluationStatus;
import com.example.qldetai.feature.evaluation.domain.GradeChangeRequest;
import com.example.qldetai.feature.identity.domain.AcademicAppointment;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.topic.domain.TopicSupervisor;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GradeChangeAccess {
    private final UserRepository users;
    private final Clock clock;

    public Specification<GradeChangeRequest> manageableBy(Long userId) {
        return scope(userId, false);
    }

    public Specification<GradeChangeRequest> readableBy(Long userId) {
        return scope(userId, true);
    }

    private Specification<GradeChangeRequest> scope(Long userId, boolean includeParticipants) {
        boolean admin =
                users.findWithRolesById(userId)
                        .map(
                                user ->
                                        user.getUserRoles().stream()
                                                .anyMatch(
                                                        role ->
                                                                role.getRole().getCode()
                                                                        == RoleCode.ADMIN))
                        .orElse(false);
        LocalDateTime now = LocalDateTime.now(clock);
        return (root, query, cb) -> {
            if (admin) return cb.conjunction();
            var assignment = root.get("sheet").get("evaluation").get("assignment");
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
            if (!includeParticipants) return cb.exists(appointments);
            var evaluators = query.subquery(Long.class);
            var evaluation = evaluators.from(EvaluationAssignment.class);
            evaluators
                    .select(evaluation.get("id"))
                    .where(
                            cb.equal(evaluation.get("assignment"), assignment),
                            cb.equal(evaluation.get("lecturer").get("user").get("id"), userId),
                            cb.equal(evaluation.get("status"), EvaluationStatus.ACTIVE));
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
                    cb.exists(supervisors),
                    cb.equal(root.get("requestedBy").get("id"), userId));
        };
    }
}
